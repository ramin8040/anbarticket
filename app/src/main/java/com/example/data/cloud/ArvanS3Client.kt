package com.example.data.cloud

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class CloudResult<out T> {
    data class Success<T>(val data: T) : CloudResult<T>()
    data class Error(
        val message: String,
        val isTransientNetworkError: Boolean = false,
        val httpCode: Int? = null,
    ) : CloudResult<Nothing>()
}

/**
 * S3-compatible object storage configuration loaded strictly from BuildConfig (Secrets panel).
 */
data class CloudStorageConfig(
    val endpoint: String,
    val bucket: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
) {
    val normalizedEndpoint: String
        get() = endpoint.trim().trimEnd('/')

    val hostDisplay: String
        get() = normalizedEndpoint.toHttpUrlOrNull()?.host ?: normalizedEndpoint

    val isConfigured: Boolean
        get() = validationError() == null

    fun validationError(): String? {
        if (accessKey.isBlank() || accessKey.startsWith("YOUR_") ||
            secretKey.isBlank() || secretKey.startsWith("YOUR_") ||
            bucket.isBlank() || bucket.startsWith("YOUR_")
        ) {
            return "کلیدهای فضای ابری (ابر آروان / S3) هنوز در بخش Secrets تنظیم نشده‌اند."
        }
        val parsed = normalizedEndpoint.toHttpUrlOrNull()
            ?: return "آدرس سرور فضای ابری (ARVAN_S3_ENDPOINT) معتبر نیست."
        if (!parsed.scheme.equals("https", ignoreCase = true)) {
            return "برای امنیت داده‌ها، آدرس فضای ابری باید با پروتکل HTTPS آغاز شود."
        }
        val host = parsed.host.lowercase(Locale.US)
        if (host.isBlank() || host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".local")) {
            return "آدرس میزبان فضای ابری مجاز نیست."
        }
        if (!BUCKET_REGEX.matches(bucket.trim())) {
            return "نام سطل (Bucket) فضای ابری معتبر نیست."
        }
        return null
    }

    companion object {
        private val BUCKET_REGEX = Regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")

        fun fromBuildConfig(): CloudStorageConfig {
            return CloudStorageConfig(
                endpoint = BuildConfig.ARVAN_S3_ENDPOINT.trim(),
                bucket = BuildConfig.ARVAN_S3_BUCKET.trim(),
                region = BuildConfig.ARVAN_S3_REGION.trim().ifEmpty { "ir-thr-at1" },
                accessKey = BuildConfig.ARVAN_S3_ACCESS_KEY.trim(),
                secretKey = BuildConfig.ARVAN_S3_SECRET_KEY.trim(),
            )
        }
    }
}

interface CloudObjectStore {
    val config: CloudStorageConfig
    suspend fun testConnection(): CloudResult<Unit>
    suspend fun uploadBytes(objectKey: String, bytes: ByteArray, contentType: String): CloudResult<Unit>
    suspend fun uploadFile(objectKey: String, file: File, contentType: String): CloudResult<Unit>
    suspend fun downloadBytes(objectKey: String): CloudResult<ByteArray>
}

/**
 * Pure AWS Signature Version 4 (AWS4-HMAC-SHA256) request signer compatible with
 * ArvanCloud Object Storage (ابر آروان), MinIO, Liara, ParsPack, Cloudflare R2, and AWS S3.
 */
object AwsSigV4Signer {
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val HEX_UPPER = "0123456789ABCDEF"
    private const val HEX_LOWER = "0123456789abcdef"
    val EMPTY_PAYLOAD_HASH: String = sha256Hex(ByteArray(0))

    data class SignedHeaders(
        val host: String,
        val amzDate: String,
        val payloadSha256: String,
        val authorization: String,
    )

    fun signRequest(
        method: String,
        canonicalUri: String,
        host: String,
        region: String,
        accessKey: String,
        secretKey: String,
        payloadSha256: String,
        nowUtc: Date = Date(),
    ): SignedHeaders {
        val amzDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateStampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val amzDate = amzDateFormat.format(nowUtc)
        val dateStamp = dateStampFormat.format(nowUtc)

        val canonicalHeaders = "host:$host\n" +
            "x-amz-content-sha256:$payloadSha256\n" +
            "x-amz-date:$amzDate\n"
        val signedHeadersList = "host;x-amz-content-sha256;x-amz-date"

        val canonicalRequest = buildString {
            append(method.uppercase(Locale.US)).append('\n')
            append(canonicalUri).append('\n')
            append('\n') // empty canonical query string
            append(canonicalHeaders).append('\n')
            append(signedHeadersList).append('\n')
            append(payloadSha256)
        }

        val credentialScope = "$dateStamp/$region/$SERVICE/aws4_request"
        val stringToSign = buildString {
            append(ALGORITHM).append('\n')
            append(amzDate).append('\n')
            append(credentialScope).append('\n')
            append(sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8)))
        }

        val signingKey = deriveSigningKey(secretKey, dateStamp, region, SERVICE)
        val signature = hmacSha256Hex(signingKey, stringToSign)
        val authorization = "$ALGORITHM Credential=$accessKey/$credentialScope, " +
            "SignedHeaders=$signedHeadersList, Signature=$signature"

        return SignedHeaders(
            host = host,
            amzDate = amzDate,
            payloadSha256 = payloadSha256,
            authorization = authorization,
        )
    }

    /**
     * Encodes each segment of an S3 path per RFC 3986 (preserving '/' separators),
     * ensuring Persian UTF-8 folder names sign identically to ArvanCloud / S3 expectations.
     */
    fun uriEncodePath(rawPath: String): String {
        val cleaned = rawPath.trimStart('/')
        if (cleaned.isEmpty()) return "/"
        return "/" + cleaned.split('/').joinToString("/") { segment -> uriEncodeSegment(segment) }
    }

    fun uriEncodeSegment(segment: String): String {
        val bytes = segment.toByteArray(StandardCharsets.UTF_8)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            val isUnreserved = (c in 'A'.code..'Z'.code) ||
                (c in 'a'.code..'z'.code) ||
                (c in '0'.code..'9'.code) ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            if (isUnreserved) {
                sb.append(c.toChar())
            } else {
                sb.append('%')
                sb.append(HEX_UPPER[(c ushr 4) and 0x0F])
                sb.append(HEX_UPPER[c and 0x0F])
            }
        }
        return sb.toString()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return bytesToLowerHex(digest)
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return bytesToLowerHex(digest.digest())
    }

    private fun deriveSigningKey(
        secretKey: String,
        dateStamp: String,
        region: String,
        service: String,
    ): ByteArray {
        val kSecret = ("AWS4$secretKey").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return bytesToLowerHex(hmacSha256(key, data))
    }

    private fun bytesToLowerHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = HEX_LOWER[v ushr 4]
            chars[i * 2 + 1] = HEX_LOWER[v and 0x0F]
        }
        return String(chars)
    }
}

/**
 * Real HTTPS S3-compatible client for ArvanCloud Object Storage (ابر آروان) and similar S3 providers.
 * Uses path-style addressing (`https://<endpoint>/<bucket>/<key>`) for maximum compatibility.
 */
class ArvanS3Client(
    override val config: CloudStorageConfig = CloudStorageConfig.fromBuildConfig(),
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : CloudObjectStore {

    override suspend fun testConnection(): CloudResult<Unit> = withContext(Dispatchers.IO) {
        val configError = config.validationError()
        if (configError != null) return@withContext CloudResult.Error(configError)

        executeSignedRequest(
            method = "HEAD",
            objectKey = null,
            payloadSha256 = AwsSigV4Signer.EMPTY_PAYLOAD_HASH,
            buildBody = { null },
            onSuccess = { CloudResult.Success(Unit) },
        )
    }

    override suspend fun uploadBytes(
        objectKey: String,
        bytes: ByteArray,
        contentType: String,
    ): CloudResult<Unit> = withContext(Dispatchers.IO) {
        val configError = config.validationError()
        if (configError != null) return@withContext CloudResult.Error(configError)

        val sha256 = AwsSigV4Signer.sha256Hex(bytes)
        val mediaType = contentType.toMediaType()
        executeSignedRequest(
            method = "PUT",
            objectKey = objectKey,
            payloadSha256 = sha256,
            buildBody = { bytes.toRequestBody(mediaType) },
            onSuccess = { CloudResult.Success(Unit) },
        )
    }

    override suspend fun uploadFile(
        objectKey: String,
        file: File,
        contentType: String,
    ): CloudResult<Unit> = withContext(Dispatchers.IO) {
        val configError = config.validationError()
        if (configError != null) return@withContext CloudResult.Error(configError)
        if (!file.exists() || !file.isFile) {
            return@withContext CloudResult.Error("فایل تصویر روی حافظه دستگاه یافت نشد.")
        }

        val sha256 = AwsSigV4Signer.sha256Hex(file)
        val mediaType = contentType.toMediaType()
        executeSignedRequest(
            method = "PUT",
            objectKey = objectKey,
            payloadSha256 = sha256,
            buildBody = { file.asRequestBody(mediaType) },
            onSuccess = { CloudResult.Success(Unit) },
        )
    }

    override suspend fun downloadBytes(objectKey: String): CloudResult<ByteArray> = withContext(Dispatchers.IO) {
        val configError = config.validationError()
        if (configError != null) return@withContext CloudResult.Error(configError)

        executeSignedRequest(
            method = "GET",
            objectKey = objectKey,
            payloadSha256 = AwsSigV4Signer.EMPTY_PAYLOAD_HASH,
            buildBody = { null },
            onSuccess = { bytes -> CloudResult.Success(bytes) },
        )
    }

    private fun <T> executeSignedRequest(
        method: String,
        objectKey: String?,
        payloadSha256: String,
        buildBody: () -> okhttp3.RequestBody?,
        onSuccess: (ByteArray) -> CloudResult<T>,
    ): CloudResult<T> {
        val baseUrl = config.normalizedEndpoint.toHttpUrlOrNull()
            ?: return CloudResult.Error("آدرس سرور فضای ابری نامعتبر است.")

        // Ensure resolved host is not a local/private network address
        val hostBlocked = runCatching {
            val addr = InetAddress.getByName(baseUrl.host)
            addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress
        }.getOrDefault(false)
        if (hostBlocked) {
            return CloudResult.Error("اتصال به آدرس‌های شبکه محلی مجاز نیست.")
        }

        val rawPath = if (objectKey.isNullOrBlank()) {
            config.bucket.trim()
        } else {
            "${config.bucket.trim()}/${objectKey.trimStart('/')}"
        }
        val canonicalUri = AwsSigV4Signer.uriEncodePath(rawPath)
        val fullUrl = "${baseUrl.scheme}://${baseUrl.host}" +
            (if (baseUrl.port != 443) ":${baseUrl.port}" else "") +
            canonicalUri
        val hostHeader = if (baseUrl.port != 443) "${baseUrl.host}:${baseUrl.port}" else baseUrl.host

        val signed = AwsSigV4Signer.signRequest(
            method = method,
            canonicalUri = canonicalUri,
            host = hostHeader,
            region = config.region,
            accessKey = config.accessKey,
            secretKey = config.secretKey,
            payloadSha256 = payloadSha256,
        )

        val request = Request.Builder()
            .url(fullUrl)
            .method(method, buildBody())
            .header("Host", signed.host)
            .header("x-amz-date", signed.amzDate)
            .header("x-amz-content-sha256", signed.payloadSha256)
            .header("Authorization", signed.authorization)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                if (response.isSuccessful) {
                    onSuccess(bodyBytes)
                } else {
                    val userMsg = when (response.code) {
                        401, 403 -> "دسترسی رد شد (کد ${response.code}). کلید دسترسی، کلید محرمانه یا نام Bucket ابر آروان را بررسی کنید."
                        404 -> "سطل (Bucket) یا فایل پشتیبان در فضای ابری یافت نشد (کد ۴۰۴)."
                        408, 429, in 500..599 -> "خطای موقت سرور فضای ابری (کد ${response.code}). لطفاً مجدداً تلاش کنید."
                        else -> "خطای فضای ابری با کد ${response.code}."
                    }
                    CloudResult.Error(
                        message = userMsg,
                        isTransientNetworkError = response.code == 408 || response.code == 429 || response.code >= 500,
                        httpCode = response.code,
                    )
                }
            }
        } catch (e: IOException) {
            CloudResult.Error(
                message = "خطا در برقراری ارتباط اینترنتی با فضای ابری. اتصال شبکه را بررسی کنید.",
                isTransientNetworkError = true,
            )
        } catch (e: Exception) {
            CloudResult.Error(
                message = "خطای غیرمنتظره در ارتباط با فضای ابری: ${e.message ?: "نامشخص"}",
                isTransientNetworkError = false,
            )
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
