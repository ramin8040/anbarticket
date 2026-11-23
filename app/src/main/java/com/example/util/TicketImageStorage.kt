package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class WatermarkInfo(
    val warehouseName: String,
    val rowName: String,
    val palletName: String,
    val cartonName: String? = null,
    val cartonCount: Int = 0,
    val itemCount: Int = 0,
    val jalaliDateTime: String,
)

data class SavedTicketImage(
    val mediaUri: String,
    val relativeFolderPath: String,
    val fileName: String,
    val capturedAtMillis: Long,
    val jalaliDateTime: String,
)

class TicketImageStorage(
    private val context: Context,
    val folderManager: InternalImageFolderManager = InternalImageFolderManager(context),
) {

    private val watermarkTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.vazirmatn) ?: Typeface.DEFAULT_BOLD
    }

    suspend fun saveFromTempFile(
        tempFile: File,
        watermarkInfo: WatermarkInfo,
        applyWatermark: Boolean,
        capturedAtMillis: Long,
    ): SavedTicketImage = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeAndOrientBitmap(
                openBoundsStream = { FileInputStream(tempFile) },
                openDecodeStream = { FileInputStream(tempFile) },
                openExifStream = { FileInputStream(tempFile) },
            ) ?: throw IllegalStateException("خطا در خواندن تصویر ثبت‌شده از دوربین")

            processAndSaveBitmap(
                source = bitmap,
                watermarkInfo = watermarkInfo,
                applyWatermark = applyWatermark,
                capturedAtMillis = capturedAtMillis,
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    suspend fun saveFromGalleryUri(
        sourceUri: Uri,
        watermarkInfo: WatermarkInfo,
        applyWatermark: Boolean,
        capturedAtMillis: Long,
    ): SavedTicketImage = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bitmap = decodeAndOrientBitmap(
            openBoundsStream = { resolver.openInputStream(sourceUri) },
            openDecodeStream = { resolver.openInputStream(sourceUri) },
            openExifStream = { resolver.openInputStream(sourceUri) },
        ) ?: throw IllegalStateException("خطا در خواندن تصویر انتخاب‌شده از گالری")

        processAndSaveBitmap(
            source = bitmap,
            watermarkInfo = watermarkInfo,
            applyWatermark = applyWatermark,
            capturedAtMillis = capturedAtMillis,
        )
    }

    suspend fun deleteSavedPhoto(mediaUri: String) {
        folderManager.deleteImageFile(mediaUri)
    }

    /**
     * Moves an existing saved ticket image into a new real folder inside internal storage
     * and deletes the old copy so folder organization stays accurate after editing ticket location.
     */
    suspend fun movePhotoToTarget(
        sourceMediaUri: String,
        targetWarehouseName: String,
        targetRowName: String,
        targetPalletName: String,
        targetCartonName: String?,
        fileName: String,
    ): String? {
        return folderManager.moveImageFile(
            sourceUriOrPath = sourceMediaUri,
            targetWarehouseName = targetWarehouseName,
            targetRowName = targetRowName,
            targetPalletName = targetPalletName,
            targetCartonName = targetCartonName,
            fileName = fileName,
        )?.fileUri
    }

    private fun decodeAndOrientBitmap(
        openBoundsStream: () -> InputStream?,
        openDecodeStream: () -> InputStream?,
        openExifStream: () -> InputStream?,
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openBoundsStream()?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val maxSide = 2048
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxSide || boundsOptions.outHeight / sampleSize > maxSide) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openDecodeStream()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: return null

        val orientation = runCatching {
            openExifStream()?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return decoded
        }

        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) {
            decoded.recycle()
        }
        return rotated
    }

    private suspend fun processAndSaveBitmap(
        source: Bitmap,
        watermarkInfo: WatermarkInfo,
        applyWatermark: Boolean,
        capturedAtMillis: Long,
    ): SavedTicketImage {
        val outputBitmap = if (applyWatermark) {
            drawPersianWatermark(source, watermarkInfo)
        } else {
            source
        }

        val prefix = if (watermarkInfo.cartonName.isNullOrBlank()) "ticket_pallet" else "ticket_carton"
        val fileName = "${prefix}_${PersianUtils.formatJalaliFileStamp(capturedAtMillis)}.jpg"

        val stored = try {
            folderManager.saveBitmapImage(
                bitmap = outputBitmap,
                warehouseName = watermarkInfo.warehouseName,
                rowName = watermarkInfo.rowName,
                palletName = watermarkInfo.palletName,
                cartonName = watermarkInfo.cartonName,
                fileName = fileName,
                jpegQuality = 90,
            )
        } finally {
            if (outputBitmap !== source) {
                outputBitmap.recycle()
            }
            source.recycle()
        }

        return SavedTicketImage(
            mediaUri = stored.fileUri,
            relativeFolderPath = stored.relativeFolderPath,
            fileName = stored.fileName,
            capturedAtMillis = capturedAtMillis,
            jalaliDateTime = watermarkInfo.jalaliDateTime,
        )
    }

    private fun drawPersianWatermark(source: Bitmap, info: WatermarkInfo): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val width = result.width.toFloat()
        val height = result.height.toFloat()

        val baseScale = max(width, height) / 1400f
        val textSizePx = (28f * baseScale).coerceIn(22f, 52f)
        val paddingH = (28f * baseScale).coerceIn(20f, 56f)
        val paddingV = (20f * baseScale).coerceIn(14f, 40f)
        val accentBarWidth = (10f * baseScale).coerceIn(6f, 20f)
        val marginEdge = (20f * baseScale).coerceIn(12f, 36f)
        val cornerRadius = (16f * baseScale).coerceIn(10f, 28f)

        val locationLine = buildString {
            append("انبار: ${info.warehouseName}")
            append("  |  ")
            append("ردیف: ${info.rowName}")
            append("  |  ")
            append("پالت: ${info.palletName}")
            if (!info.cartonName.isNullOrBlank()) {
                append("  |  ")
                append("کارتن: ${info.cartonName}")
            }
        }

        val countsAndDateLine = buildString {
            if (info.cartonName.isNullOrBlank()) {
                if (info.cartonCount > 0) {
                    append("تعداد کارتن: ${PersianUtils.toPersianDigits(info.cartonCount.toString())}  |  ")
                }
                if (info.itemCount > 0) {
                    append("اقلام: ${PersianUtils.toPersianDigits(info.itemCount.toString())}  |  ")
                }
            } else {
                if (info.itemCount > 0) {
                    append("اقلام کارتن: ${PersianUtils.toPersianDigits(info.itemCount.toString())}  |  ")
                }
            }
            append("زمان ثبت: ${info.jalaliDateTime}")
        }

        val fullText = "$locationLine\n$countsAndDateLine"

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = watermarkTypeface
            setShadowLayer(3f * baseScale, 0f, 1.5f * baseScale, Color.argb(180, 0, 0, 0))
        }

        val maxBoxWidth = (width - marginEdge * 2f).roundToInt().coerceAtLeast(100)
        val textContentWidth = (maxBoxWidth - paddingH * 2f - accentBarWidth * 2f).roundToInt().coerceAtLeast(80)

        val staticLayout = StaticLayout.Builder
            .obtain(fullText, 0, fullText.length, textPaint, textContentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(6f * baseScale, 1.15f)
            .setIncludePad(true)
            .build()

        val boxHeight = staticLayout.height + paddingV * 2f
        val boxTop = (height - marginEdge - boxHeight).coerceAtLeast(marginEdge)
        val boxRect = RectF(
            marginEdge,
            boxTop,
            width - marginEdge,
            height - marginEdge,
        )

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(208, 15, 23, 42)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 245, 158, 11)
            style = Paint.Style.STROKE
            strokeWidth = (2f * baseScale).coerceIn(1.5f, 4f)
        }
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, borderPaint)

        val accentRect = RectF(
            boxRect.right - accentBarWidth - (10f * baseScale),
            boxRect.top + paddingV * 0.65f,
            boxRect.right - (10f * baseScale),
            boxRect.bottom - paddingV * 0.65f,
        )
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 158, 11)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(accentRect, accentBarWidth / 2f, accentBarWidth / 2f, accentPaint)

        canvas.save()
        canvas.translate(
            boxRect.left + paddingH,
            boxRect.top + paddingV,
        )
        staticLayout.draw(canvas)
        canvas.restore()

        return result
    }
}
