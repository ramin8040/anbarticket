package com.example.data.cloud

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.CartonEntity
import com.example.data.local.CloudSyncedObjectEntity
import com.example.data.local.LastBackupStatus
import com.example.data.local.PalletEntity
import com.example.data.local.RowEntity
import com.example.data.local.TicketPhotoEntity
import com.example.data.local.UserPreferences
import com.example.data.local.WarehouseEntity
import com.example.util.InternalImageFolderManager
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

data class CloudTransferProgress(
    val isRunning: Boolean = false,
    val isRestore: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val statusText: String = "",
)

data class BackupExecutionSummary(
    val uploadedImagesCount: Int,
    val skippedAlreadySyncedCount: Int,
    val totalTicketsCount: Int,
    val warehouseCount: Int,
    val manifestKey: String,
    val completedAtMillis: Long,
)

data class RestoreExecutionSummary(
    val restoredWarehouses: Int,
    val restoredRows: Int,
    val restoredPallets: Int,
    val restoredCartons: Int,
    val restoredTickets: Int,
    val downloadedImages: Int,
    val missingImages: Int,
)

/**
 * Coordinates incremental cloud backup and full disaster-recovery restore of both
 * Room database entities and physical ticket image files to ArvanCloud / S3 storage.
 */
class CloudBackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val folderManager: InternalImageFolderManager,
    private val preferences: UserPreferences,
    private val objectStore: CloudObjectStore = ArvanS3Client(),
) {
    private val dao = database.warehouseDao()

    val config: CloudStorageConfig get() = objectStore.config

    suspend fun testConnection(): CloudResult<Unit> = objectStore.testConnection()

    suspend fun backupNow(
        onProgress: (CloudTransferProgress) -> Unit = {},
    ): CloudResult<BackupExecutionSummary> = withContext(Dispatchers.IO) {
        val configError = config.validationError()
        if (configError != null) {
            preferences.recordBackupOutcome(
                status = LastBackupStatus.ERROR,
                timestampMillis = System.currentTimeMillis(),
                message = configError,
                uploadedCount = 0,
            )
            return@withContext CloudResult.Error(configError)
        }

        val warehouses = dao.getAllWarehousesOnce()
        val rows = dao.getAllRowsOnce()
        val pallets = dao.getAllPalletsOnce()
        val cartons = dao.getAllCartonsOnce()
        val ticketPhotos = dao.getAllTicketPhotosOnce()
        val syncedMap = dao.getAllSyncedObjectsOnce().associateBy { it.photoId }

        val totalSteps = ticketPhotos.size + 1 // images + manifest.json
        onProgress(
            CloudTransferProgress(
                isRunning = true,
                isRestore = false,
                currentStep = 0,
                totalSteps = totalSteps,
                statusText = "در حال بررسی فایل‌ها برای پشتیبان‌گیری ابری…",
            ),
        )

        var uploadedCount = 0
        var skippedCount = 0
        val photoCloudKeys = mutableMapOf<Long, String>()

        for ((index, photo) in ticketPhotos.withIndex()) {
            val objectKey = buildImageObjectKey(photo.relativeFolderPath, photo.fileName)
            photoCloudKeys[photo.id] = objectKey

            val localFile = resolveLocalFile(photo.mediaUri)
            val fileSize = localFile?.length() ?: -1L
            val existingSync = syncedMap[photo.id]

            val alreadySynced = existingSync != null &&
                existingSync.objectKey == objectKey &&
                (fileSize <= 0L || existingSync.fileSizeBytes == fileSize)

            if (alreadySynced) {
                skippedCount++
            } else {
                onProgress(
                    CloudTransferProgress(
                        isRunning = true,
                        isRestore = false,
                        currentStep = index + 1,
                        totalSteps = totalSteps,
                        statusText = "در حال ارسال تصویر ${(index + 1).toPersianNumber()} از ${ticketPhotos.size.toPersianNumber()}…",
                    ),
                )

                val uploadResult = when {
                    localFile != null && localFile.exists() -> {
                        objectStore.uploadFile(objectKey, localFile, CONTENT_TYPE_JPEG)
                    }
                    else -> {
                        val contentBytes = readContentUriBytes(photo.mediaUri)
                        if (contentBytes != null) {
                            objectStore.uploadBytes(objectKey, contentBytes, CONTENT_TYPE_JPEG)
                        } else {
                            CloudResult.Success(Unit) // local file missing; preserve metadata in manifest
                        }
                    }
                }

                when (uploadResult) {
                    is CloudResult.Error -> {
                        preferences.recordBackupOutcome(
                            status = LastBackupStatus.ERROR,
                            timestampMillis = System.currentTimeMillis(),
                            message = uploadResult.message,
                            uploadedCount = uploadedCount,
                        )
                        return@withContext uploadResult
                    }
                    is CloudResult.Success -> {
                        val syncedBytes = if (fileSize > 0L) fileSize else 0L
                        dao.upsertSyncedObject(
                            CloudSyncedObjectEntity(
                                photoId = photo.id,
                                objectKey = objectKey,
                                fileSizeBytes = syncedBytes,
                                syncedAt = System.currentTimeMillis(),
                            ),
                        )
                        uploadedCount++
                    }
                }
            }
        }

        // Upload complete database manifest JSON
        onProgress(
            CloudTransferProgress(
                isRunning = true,
                isRestore = false,
                currentStep = totalSteps,
                totalSteps = totalSteps,
                statusText = "در حال ذخیره نسخه پشتیبان اطلاعات پایگاه داده…",
            ),
        )

        val now = System.currentTimeMillis()
        val manifestJson = serializeManifest(
            createdAtMillis = now,
            warehouses = warehouses,
            rows = rows,
            pallets = pallets,
            cartons = cartons,
            ticketPhotos = ticketPhotos,
            photoCloudKeys = photoCloudKeys,
        )
        val manifestBytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
        when (val manifestRes = objectStore.uploadBytes(MANIFEST_OBJECT_KEY, manifestBytes, CONTENT_TYPE_JSON)) {
            is CloudResult.Error -> {
                preferences.recordBackupOutcome(
                    status = LastBackupStatus.ERROR,
                    timestampMillis = now,
                    message = manifestRes.message,
                    uploadedCount = uploadedCount,
                )
                return@withContext manifestRes
            }
            is CloudResult.Success -> Unit
        }

        val summaryMessage = if (uploadedCount > 0) {
            "پشتیبان‌گیری موفق: ${uploadedCount.toPersianNumber()} تصویر جدید و کل داده‌ها ذخیره شد."
        } else {
            "همگام‌سازی موفق: اطلاعات پایگاه داده و ${ticketPhotos.size.toPersianNumber()} تیکت به‌روز هستند."
        }

        preferences.recordBackupOutcome(
            status = LastBackupStatus.SUCCESS,
            timestampMillis = now,
            message = summaryMessage,
            uploadedCount = uploadedCount,
        )

        CloudResult.Success(
            BackupExecutionSummary(
                uploadedImagesCount = uploadedCount,
                skippedAlreadySyncedCount = skippedCount,
                totalTicketsCount = ticketPhotos.size,
                warehouseCount = warehouses.size,
                manifestKey = MANIFEST_OBJECT_KEY,
                completedAtMillis = now,
            ),
        )
    }

    suspend fun restoreFromCloud(
        onProgress: (CloudTransferProgress) -> Unit = {},
    ): CloudResult<RestoreExecutionSummary> = withContext(Dispatchers.IO) {
        val configError = config.validationError()
        if (configError != null) return@withContext CloudResult.Error(configError)

        onProgress(
            CloudTransferProgress(
                isRunning = true,
                isRestore = true,
                currentStep = 0,
                totalSteps = 1,
                statusText = "در حال دریافت فایل فهرست پشتیبان از فضای ابری…",
            ),
        )

        val manifestDownload = objectStore.downloadBytes(MANIFEST_OBJECT_KEY)
        val manifestBytes = when (manifestDownload) {
            is CloudResult.Error -> return@withContext manifestDownload
            is CloudResult.Success -> manifestDownload.data
        }

        val parsed = runCatching {
            parseManifest(String(manifestBytes, StandardCharsets.UTF_8))
        }.getOrElse {
            return@withContext CloudResult.Error("ساختار فایل پشتیبان ابری نامعتبر یا آسیب‌دیده است.")
        }

        val warehouseById = parsed.warehouses.associateBy { it.id }
        val rowById = parsed.rows.associateBy { it.id }
        val palletById = parsed.pallets.associateBy { it.id }
        val cartonById = parsed.cartons.associateBy { it.id }

        // Recreate real physical folder tree for all restored entities
        parsed.warehouses.forEach { wh ->
            folderManager.ensureWarehouseDirectory(wh.name)
        }
        parsed.rows.forEach { row ->
            val wh = warehouseById[row.warehouseId] ?: return@forEach
            folderManager.ensureRowDirectory(wh.name, row.name)
        }
        parsed.pallets.forEach { pallet ->
            val row = rowById[pallet.rowId] ?: return@forEach
            val wh = warehouseById[row.warehouseId] ?: return@forEach
            folderManager.ensurePalletDirectory(wh.name, row.name, pallet.name)
        }
        parsed.cartons.forEach { carton ->
            val pallet = palletById[carton.palletId] ?: return@forEach
            val row = rowById[pallet.rowId] ?: return@forEach
            val wh = warehouseById[row.warehouseId] ?: return@forEach
            folderManager.ensureCartonDirectory(wh.name, row.name, pallet.name, carton.name)
        }

        val totalSteps = parsed.ticketPhotos.size + 1
        var downloadedImages = 0
        var missingImages = 0
        val restoredTickets = mutableListOf<TicketPhotoEntity>()
        val restoredSyncRecords = mutableListOf<CloudSyncedObjectEntity>()
        val now = System.currentTimeMillis()

        for ((index, item) in parsed.ticketPhotos.withIndex()) {
            onProgress(
                CloudTransferProgress(
                    isRunning = true,
                    isRestore = true,
                    currentStep = index + 1,
                    totalSteps = totalSteps,
                    statusText = "در حال بازیابی تصویر ${(index + 1).toPersianNumber()} از ${parsed.ticketPhotos.size.toPersianNumber()}…",
                ),
            )

            val pallet = palletById[item.entity.palletId]
            val row = pallet?.let { rowById[it.rowId] }
            val warehouse = row?.let { warehouseById[it.warehouseId] }
            val carton = item.entity.cartonId?.let { cartonById[it] }

            if (warehouse == null || row == null || pallet == null) {
                missingImages++
                continue
            }

            val imgRes = objectStore.downloadBytes(item.cloudObjectKey)
            val savedFile = when (imgRes) {
                is CloudResult.Success -> {
                    downloadedImages++
                    folderManager.saveImageBytes(
                        bytes = imgRes.data,
                        warehouseName = warehouse.name,
                        rowName = row.name,
                        palletName = pallet.name,
                        cartonName = carton?.name,
                        fileName = item.entity.fileName,
                    )
                }
                is CloudResult.Error -> {
                    if (imgRes.httpCode == 404) {
                        missingImages++
                        null
                    } else {
                        return@withContext imgRes
                    }
                }
            }

            val localUri = savedFile?.fileUri ?: folderManager.resolveStoredFileUri(
                warehouseName = warehouse.name,
                rowName = row.name,
                palletName = pallet.name,
                cartonName = carton?.name,
                fileName = item.entity.fileName,
            )
            val relativePath = savedFile?.relativeFolderPath ?: PersianUtils.buildTargetTicketFolderPath(
                warehouseName = warehouse.name,
                rowName = row.name,
                palletName = pallet.name,
                cartonName = carton?.name,
            )

            restoredTickets.add(
                item.entity.copy(
                    mediaUri = localUri,
                    relativeFolderPath = relativePath,
                ),
            )
            if (savedFile != null) {
                restoredSyncRecords.add(
                    CloudSyncedObjectEntity(
                        photoId = item.entity.id,
                        objectKey = item.cloudObjectKey,
                        fileSizeBytes = savedFile.sizeBytes,
                        syncedAt = now,
                    ),
                )
            }
        }

        // Atomically replace Room database contents inside a single transaction
        onProgress(
            CloudTransferProgress(
                isRunning = true,
                isRestore = true,
                currentStep = totalSteps,
                totalSteps = totalSteps,
                statusText = "در حال ثبت نهایی اطلاعات بازیابی‌شده…",
            ),
        )

        database.withTransaction {
            dao.clearAllSyncedObjects()
            dao.clearAllTicketPhotos()
            dao.clearAllCartons()
            dao.clearAllPallets()
            dao.clearAllRows()
            dao.clearAllWarehouses()

            if (parsed.warehouses.isNotEmpty()) dao.insertWarehousesBatch(parsed.warehouses)
            if (parsed.rows.isNotEmpty()) dao.insertRowsBatch(parsed.rows)
            if (parsed.pallets.isNotEmpty()) dao.insertPalletsBatch(parsed.pallets)
            if (parsed.cartons.isNotEmpty()) dao.insertCartonsBatch(parsed.cartons)
            if (restoredTickets.isNotEmpty()) dao.insertTicketPhotosBatch(restoredTickets)
            if (restoredSyncRecords.isNotEmpty()) dao.upsertSyncedObjects(restoredSyncRecords)
        }

        preferences.recordBackupOutcome(
            status = LastBackupStatus.SUCCESS,
            timestampMillis = now,
            message = "بازیابی کامل انجام شد: ${downloadedImages.toPersianNumber()} تصویر و ${parsed.warehouses.size.toPersianNumber()} انبار بازگردانی شدند.",
            uploadedCount = downloadedImages,
        )

        CloudResult.Success(
            RestoreExecutionSummary(
                restoredWarehouses = parsed.warehouses.size,
                restoredRows = parsed.rows.size,
                restoredPallets = parsed.pallets.size,
                restoredCartons = parsed.cartons.size,
                restoredTickets = restoredTickets.size,
                downloadedImages = downloadedImages,
                missingImages = missingImages,
            ),
        )
    }

    private fun resolveLocalFile(mediaUri: String): File? {
        val uri = Uri.parse(mediaUri)
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            null -> File(mediaUri)
            else -> null
        }
    }

    private fun readContentUriBytes(mediaUri: String): ByteArray? {
        return runCatching {
            val uri = Uri.parse(mediaUri)
            if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                null
            }
        }.getOrNull()
    }

    internal data class ManifestTicketEntry(
        val entity: TicketPhotoEntity,
        val cloudObjectKey: String,
    )

    internal data class ParsedBackupManifest(
        val schemaVersion: Int,
        val createdAtMillis: Long,
        val warehouses: List<WarehouseEntity>,
        val rows: List<RowEntity>,
        val pallets: List<PalletEntity>,
        val cartons: List<CartonEntity>,
        val ticketPhotos: List<ManifestTicketEntry>,
    )

    companion object {
        const val MANIFEST_SCHEMA_VERSION = 1
        const val MANIFEST_OBJECT_KEY = "backup/manifest.json"
        private const val IMAGES_PREFIX = "backup/images"
        private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        private const val CONTENT_TYPE_JPEG = "image/jpeg"

        fun buildImageObjectKey(relativeFolderPath: String, fileName: String): String {
            val normalizedFolder = relativeFolderPath
                .trim()
                .trim('/')
                .split('/')
                .filter { it.isNotBlank() && it != "." && it != ".." }
                .joinToString("/")
            val safeName = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
            return "$IMAGES_PREFIX/$normalizedFolder/$safeName"
        }

        internal fun serializeManifest(
            createdAtMillis: Long,
            warehouses: List<WarehouseEntity>,
            rows: List<RowEntity>,
            pallets: List<PalletEntity>,
            cartons: List<CartonEntity>,
            ticketPhotos: List<TicketPhotoEntity>,
            photoCloudKeys: Map<Long, String>,
        ): String {
            val root = JSONObject()
            root.put("schemaVersion", MANIFEST_SCHEMA_VERSION)
            root.put("createdAtMillis", createdAtMillis)
            root.put("createdAtJalali", PersianUtils.formatJalaliDateTime(createdAtMillis))

            val warehousesArr = JSONArray()
            warehouses.forEach { w ->
                warehousesArr.put(
                    JSONObject().apply {
                        put("id", w.id)
                        put("name", w.name)
                        put("createdAt", w.createdAt)
                    },
                )
            }
            root.put("warehouses", warehousesArr)

            val rowsArr = JSONArray()
            rows.forEach { r ->
                rowsArr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("warehouseId", r.warehouseId)
                        put("name", r.name)
                        put("createdAt", r.createdAt)
                    },
                )
            }
            root.put("rows", rowsArr)

            val palletsArr = JSONArray()
            pallets.forEach { p ->
                palletsArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("rowId", p.rowId)
                        put("name", p.name)
                        put("cartonCount", p.cartonCount)
                        put("itemCount", p.itemCount)
                        put("createdAt", p.createdAt)
                    },
                )
            }
            root.put("pallets", palletsArr)

            val cartonsArr = JSONArray()
            cartons.forEach { c ->
                cartonsArr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("palletId", c.palletId)
                        put("name", c.name)
                        put("itemCount", c.itemCount)
                        put("createdAt", c.createdAt)
                    },
                )
            }
            root.put("cartons", cartonsArr)

            val ticketsArr = JSONArray()
            ticketPhotos.forEach { t ->
                val key = photoCloudKeys[t.id] ?: buildImageObjectKey(t.relativeFolderPath, t.fileName)
                ticketsArr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("palletId", t.palletId)
                        if (t.cartonId != null) put("cartonId", t.cartonId) else put("cartonId", JSONObject.NULL)
                        put("relativeFolderPath", t.relativeFolderPath)
                        put("fileName", t.fileName)
                        put("capturedAt", t.capturedAt)
                        put("jalaliDateTime", t.jalaliDateTime)
                        put("note", t.note)
                        put("cloudObjectKey", key)
                    },
                )
            }
            root.put("ticketPhotos", ticketsArr)

            return root.toString(2)
        }

        internal fun parseManifest(rawJson: String): ParsedBackupManifest {
            val root = JSONObject(rawJson)
            val version = root.optInt("schemaVersion", -1)
            require(version == MANIFEST_SCHEMA_VERSION) { "نسخه فایل پشتیبان پشتیبانی نمی‌شود." }

            val createdAtMillis = root.optLong("createdAtMillis", 0L)

            val warehouses = mutableListOf<WarehouseEntity>()
            val wArr = root.optJSONArray("warehouses") ?: JSONArray()
            for (i in 0 until wArr.length()) {
                val obj = wArr.getJSONObject(i)
                warehouses.add(
                    WarehouseEntity(
                        id = obj.getLong("id"),
                        name = obj.getString("name"),
                        createdAt = obj.optLong("createdAt", createdAtMillis),
                    ),
                )
            }

            val rows = mutableListOf<RowEntity>()
            val rArr = root.optJSONArray("rows") ?: JSONArray()
            for (i in 0 until rArr.length()) {
                val obj = rArr.getJSONObject(i)
                rows.add(
                    RowEntity(
                        id = obj.getLong("id"),
                        warehouseId = obj.getLong("warehouseId"),
                        name = obj.getString("name"),
                        createdAt = obj.optLong("createdAt", createdAtMillis),
                    ),
                )
            }

            val pallets = mutableListOf<PalletEntity>()
            val pArr = root.optJSONArray("pallets") ?: JSONArray()
            for (i in 0 until pArr.length()) {
                val obj = pArr.getJSONObject(i)
                pallets.add(
                    PalletEntity(
                        id = obj.getLong("id"),
                        rowId = obj.getLong("rowId"),
                        name = obj.getString("name"),
                        cartonCount = obj.optInt("cartonCount", 0).coerceAtLeast(0),
                        itemCount = obj.optInt("itemCount", 0).coerceAtLeast(0),
                        createdAt = obj.optLong("createdAt", createdAtMillis),
                    ),
                )
            }

            val cartons = mutableListOf<CartonEntity>()
            val cArr = root.optJSONArray("cartons") ?: JSONArray()
            for (i in 0 until cArr.length()) {
                val obj = cArr.getJSONObject(i)
                cartons.add(
                    CartonEntity(
                        id = obj.getLong("id"),
                        palletId = obj.getLong("palletId"),
                        name = obj.getString("name"),
                        itemCount = obj.optInt("itemCount", 0).coerceAtLeast(0),
                        createdAt = obj.optLong("createdAt", createdAtMillis),
                    ),
                )
            }

            val tickets = mutableListOf<ManifestTicketEntry>()
            val tArr = root.optJSONArray("ticketPhotos") ?: JSONArray()
            for (i in 0 until tArr.length()) {
                val obj = tArr.getJSONObject(i)
                val cartonId = if (obj.isNull("cartonId")) null else obj.getLong("cartonId")
                val relPath = obj.getString("relativeFolderPath")
                val fileName = obj.getString("fileName")
                val cloudKey = obj.optString("cloudObjectKey").ifBlank {
                    buildImageObjectKey(relPath, fileName)
                }
                tickets.add(
                    ManifestTicketEntry(
                        entity = TicketPhotoEntity(
                            id = obj.getLong("id"),
                            palletId = obj.getLong("palletId"),
                            cartonId = cartonId,
                            mediaUri = "",
                            relativeFolderPath = relPath,
                            fileName = fileName,
                            capturedAt = obj.optLong("capturedAt", createdAtMillis),
                            jalaliDateTime = obj.optString("jalaliDateTime", ""),
                            note = obj.optString("note", ""),
                        ),
                        cloudObjectKey = cloudKey,
                    ),
                )
            }

            return ParsedBackupManifest(
                schemaVersion = version,
                createdAtMillis = createdAtMillis,
                warehouses = warehouses,
                rows = rows,
                pallets = pallets,
                cartons = cartons,
                ticketPhotos = tickets,
            )
        }
    }
}
