package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.OperationResult
import com.example.data.cloud.CloudBackupManager
import com.example.data.local.AppDatabase
import com.example.data.local.CartonEntity
import com.example.data.local.PalletEntity
import com.example.data.local.RowEntity
import com.example.data.local.TicketPhotoEntity
import com.example.data.local.WarehouseEntity
import com.example.util.InternalImageFolderManager
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

/**
 * User-selected behavior when restoring a local ZIP backup inside the application.
 */
enum class LocalRestoreConflictMode(val title: String, val description: String) {
    MERGE(
        title = "ادغام با اطلاعات فعلی (پیشنهادی)",
        description = "اطلاعات و عکس‌های فعلی گوشی حفظ می‌شوند و انبارها، ردیف‌ها، پالت‌ها، کارتن‌ها و عکس‌های داخل بکاپ به آن‌ها اضافه یا با هم‌نام‌ها ادغام می‌شوند.",
    ),
    REPLACE(
        title = "جایگزینی کامل اطلاعات",
        description = "تمام اطلاعات و عکس‌های فعلی داخل برنامه پاک شده و دقیقاً اطلاعات و تصاویر موجود در فایل بکاپ جایگزین می‌شوند.",
    ),
}

/**
 * Progress state for local ZIP backup creation or restoration.
 */
data class LocalZipTransferProgress(
    val isRunning: Boolean = false,
    val isRestore: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val statusText: String = "",
)

/**
 * Pre-restore inspection summary of a user-picked ZIP backup archive.
 */
data class LocalBackupPreviewInfo(
    val sourceUri: Uri,
    val createdAtMillis: Long,
    val createdAtJalali: String,
    val warehouseCount: Int,
    val rowCount: Int,
    val palletCount: Int,
    val cartonCount: Int,
    val ticketCount: Int,
    val imageEntryCount: Int,
)

data class LocalBackupExportSummary(
    val exportedWarehouses: Int,
    val exportedPallets: Int,
    val exportedCartons: Int,
    val exportedTickets: Int,
    val exportedImages: Int,
    val fileName: String,
    val sizeBytes: Long,
)

data class LocalBackupRestoreSummary(
    val mode: LocalRestoreConflictMode,
    val restoredWarehouses: Int,
    val restoredRows: Int,
    val restoredPallets: Int,
    val restoredCartons: Int,
    val restoredTickets: Int,
    val restoredImages: Int,
)

/**
 * Manages full offline ZIP backup creation (database manifest + physical ticket images in tree hierarchy),
 * saving to user-selected device folders (SAF), direct sharing via FileProvider,
 * pre-restore ZIP validation/inspection, and restoring in either REPLACE or MERGE mode.
 */
class LocalBackupZipManager(
    private val context: Context,
    private val database: AppDatabase,
    private val folderManager: InternalImageFolderManager,
) {
    private val dao = database.warehouseDao()

    fun buildSuggestedBackupFileName(timestampMillis: Long = System.currentTimeMillis()): String {
        val stamp = PersianUtils.formatJalaliFileStamp(timestampMillis)
        return "AnbarTicket_Backup_$stamp.zip"
    }

    /**
     * Exports a complete ZIP backup directly to a user-chosen document Uri (from CreateDocument).
     */
    suspend fun exportBackupToUri(
        targetUri: Uri,
        onProgress: (LocalZipTransferProgress) -> Unit = {},
    ): OperationResult<LocalBackupExportSummary> = withContext(Dispatchers.IO) {
        try {
            val tempFile = createTempBackupFile()
            try {
                val summary = FileOutputStream(tempFile).use { fos ->
                    writeFullBackupZipToStream(
                        rawOutput = fos,
                        fileName = buildSuggestedBackupFileName(),
                        onProgress = onProgress,
                    )
                }
                context.contentResolver.openOutputStream(targetUri, "w")?.use { outStream ->
                    FileInputStream(tempFile).use { inStream ->
                        inStream.copyTo(outStream, DEFAULT_BUFFER_SIZE)
                    }
                    outStream.flush()
                } ?: return@withContext OperationResult.Error("امکان باز کردن مسیر انتخابی برای ذخیره فایل بکاپ وجود ندارد.")

                OperationResult.Success(summary.copy(sizeBytes = tempFile.length()))
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            OperationResult.Error(e.message ?: "خطا در ذخیره فایل پشتیبان ZIP در حافظه گوشی")
        }
    }

    /**
     * Exports a complete ZIP backup to the given File (used for sharing and deterministic unit tests).
     */
    suspend fun exportBackupToFile(
        targetFile: File,
        onProgress: (LocalZipTransferProgress) -> Unit = {},
    ): OperationResult<LocalBackupExportSummary> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val summary = FileOutputStream(targetFile).use { fos ->
                writeFullBackupZipToStream(
                    rawOutput = fos,
                    fileName = targetFile.name,
                    onProgress = onProgress,
                )
            }
            OperationResult.Success(summary.copy(sizeBytes = targetFile.length()))
        } catch (e: Exception) {
            OperationResult.Error(e.message ?: "خطا در ساخت فایل پشتیبان ZIP")
        }
    }

    /**
     * Creates a shareable ZIP backup file inside cacheDir/backups and returns its FileProvider Uri.
     */
    suspend fun prepareShareableBackupZip(
        onProgress: (LocalZipTransferProgress) -> Unit = {},
    ): OperationResult<Pair<Uri, LocalBackupExportSummary>> = withContext(Dispatchers.IO) {
        try {
            val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
            // Clean up older temporary shared ZIPs to avoid wasting cache space
            backupsDir.listFiles()?.forEach { it.delete() }

            val fileName = buildSuggestedBackupFileName()
            val zipFile = File(backupsDir, fileName)
            when (val res = exportBackupToFile(zipFile, onProgress)) {
                is OperationResult.Error -> res
                is OperationResult.Success -> {
                    val authority = "${context.packageName}.fileprovider"
                    val contentUri = FileProvider.getUriForFile(context, authority, zipFile)
                    OperationResult.Success(contentUri to res.data)
                }
            }
        } catch (e: Exception) {
            OperationResult.Error(e.message ?: "خطا در آماده‌سازی فایل پشتیبان برای اشتراک‌گذاری")
        }
    }

    /**
     * Inspects a user-picked ZIP archive Uri to validate its manifest and show a summary before restoring.
     */
    suspend fun inspectBackupZip(sourceUri: Uri): OperationResult<LocalBackupPreviewInfo> = withContext(Dispatchers.IO) {
        try {
            val stream = openSourceInputStream(sourceUri)
                ?: return@withContext OperationResult.Error("امکان خواندن فایل انتخاب‌شده وجود ندارد.")
            stream.use { input ->
                inspectBackupZipStream(sourceUri, input)
            }
        } catch (e: Exception) {
            OperationResult.Error(e.message ?: "فایل انتخاب‌شده یک بکاپ معتبر ZIP نیست.")
        }
    }

    /**
     * Restores the backup ZIP from [sourceUri] using the user-confirmed [mode] (REPLACE or MERGE).
     */
    suspend fun restoreFromBackupZip(
        sourceUri: Uri,
        mode: LocalRestoreConflictMode,
        onProgress: (LocalZipTransferProgress) -> Unit = {},
    ): OperationResult<LocalBackupRestoreSummary> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.nanoTime()}").apply { mkdirs() }
        try {
            onProgress(
                LocalZipTransferProgress(
                    isRunning = true,
                    isRestore = true,
                    currentStep = 0,
                    totalSteps = 1,
                    statusText = "در حال استخراج و بررسی امن فایل پشتیبان ZIP…",
                ),
            )

            val inputStream = openSourceInputStream(sourceUri)
                ?: return@withContext OperationResult.Error("امکان باز کردن فایل پشتیبان انتخاب‌شده وجود ندارد.")

            var manifestJson: String? = null
            val extractedImagesByZipKey = mutableMapOf<String, File>()
            val stagingImagesRoot = File(stagingDir, "images").apply { mkdirs() }
            val stagingImagesCanonical = stagingImagesRoot.canonicalPath

            inputStream.use { rawIn ->
                ZipInputStream(BufferedInputStream(rawIn)).use { zis ->
                    var entryCount = 0
                    var totalExtractedBytes = 0L
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        entryCount++
                        if (entryCount > MAX_ZIP_ENTRIES) {
                            return@withContext OperationResult.Error("تعداد فایل‌های داخل آرشیو بیش از حد مجاز است.")
                        }
                        if (entry.isDirectory) {
                            zis.closeEntry()
                            continue
                        }

                        val normalizedName = normalizeZipEntryName(entry.name)
                            ?: return@withContext OperationResult.Error("مسیر غیرمجاز در داخل فایل ZIP شناسایی شد.")

                        if (normalizedName == ZIP_MANIFEST_ENTRY_NAME ||
                            normalizedName == CloudBackupManager.MANIFEST_OBJECT_KEY
                        ) {
                            val bytes = readBoundedEntryBytes(zis, MAX_MANIFEST_BYTES)
                            manifestJson = String(bytes, StandardCharsets.UTF_8)
                        } else if (normalizedName.startsWith("images/") ||
                            normalizedName.startsWith("backup/images/")
                        ) {
                            val relImageKey = normalizedName
                                .removePrefix("backup/")
                                .removePrefix("images/")
                                .trimStart('/')
                            if (relImageKey.isNotBlank()) {
                                val outFile = File(stagingImagesRoot, relImageKey)
                                val outCanonical = outFile.canonicalPath
                                if (!outCanonical.startsWith("$stagingImagesCanonical${File.separator}")) {
                                    return@withContext OperationResult.Error("مسیر تصویر در فایل پشتیبان خارج از محدوده امن است.")
                                }
                                outFile.parentFile?.mkdirs()
                                val written = copyBoundedEntryToFile(zis, outFile, MAX_SINGLE_IMAGE_BYTES)
                                totalExtractedBytes += written
                                if (totalExtractedBytes > MAX_TOTAL_ARCHIVE_BYTES) {
                                    return@withContext OperationResult.Error("حجم کل استخراج‌شده بیش از سقف مجاز امنیتی است.")
                                }
                                extractedImagesByZipKey[relImageKey] = outFile
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }

            val rawManifest = manifestJson
                ?: return@withContext OperationResult.Error("فایل فهرست اطلاعات (manifest.json) در داخل این فایل ZIP یافت نشد.")

            val parsed = runCatching { CloudBackupManager.parseManifest(rawManifest) }.getOrElse {
                return@withContext OperationResult.Error("ساختار اطلاعات داخل فایل پشتیبان معتبر نیست.")
            }

            return@withContext when (mode) {
                LocalRestoreConflictMode.REPLACE -> executeReplaceRestore(
                    parsed = parsed,
                    extractedImagesByZipKey = extractedImagesByZipKey,
                    onProgress = onProgress,
                )
                LocalRestoreConflictMode.MERGE -> executeMergeRestore(
                    parsed = parsed,
                    extractedImagesByZipKey = extractedImagesByZipKey,
                    onProgress = onProgress,
                )
            }
        } catch (e: Exception) {
            OperationResult.Error(e.message ?: "خطا در بازیابی فایل پشتیبان ZIP")
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun executeReplaceRestore(
        parsed: CloudBackupManager.ParsedBackupManifest,
        extractedImagesByZipKey: Map<String, File>,
        onProgress: (LocalZipTransferProgress) -> Unit,
    ): OperationResult<LocalBackupRestoreSummary> {
        val totalSteps = parsed.ticketPhotos.size + 1

        // 1. Clean existing physical warehouse folders under rootDir
        folderManager.rootDir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                folderManager.deleteWarehouseDirectory(child.name)
            } else {
                child.delete()
            }
        }

        val warehouseById = parsed.warehouses.associateBy { it.id }
        val rowById = parsed.rows.associateBy { it.id }
        val palletById = parsed.pallets.associateBy { it.id }
        val cartonById = parsed.cartons.associateBy { it.id }

        // 2. Recreate physical folder hierarchy
        parsed.warehouses.forEach { wh -> folderManager.ensureWarehouseDirectory(wh.name) }
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

        var restoredImagesCount = 0
        val restoredTickets = mutableListOf<TicketPhotoEntity>()

        for ((index, item) in parsed.ticketPhotos.withIndex()) {
            onProgress(
                LocalZipTransferProgress(
                    isRunning = true,
                    isRestore = true,
                    currentStep = index + 1,
                    totalSteps = totalSteps,
                    statusText = "در حال جایگزینی تصویر ${(index + 1).toPersianNumber()} از ${parsed.ticketPhotos.size.toPersianNumber()}…",
                ),
            )

            val pallet = palletById[item.entity.palletId] ?: continue
            val row = rowById[pallet.rowId] ?: continue
            val warehouse = warehouseById[row.warehouseId] ?: continue
            val carton = item.entity.cartonId?.let { cartonById[it] }

            val stagedFile = findExtractedImageFile(item, extractedImagesByZipKey)
            val savedImage = if (stagedFile != null && stagedFile.exists()) {
                restoredImagesCount++
                folderManager.saveImageBytes(
                    bytes = stagedFile.readBytes(),
                    warehouseName = warehouse.name,
                    rowName = row.name,
                    palletName = pallet.name,
                    cartonName = carton?.name,
                    fileName = item.entity.fileName,
                )
            } else {
                null
            }

            val finalUri = savedImage?.fileUri ?: folderManager.resolveStoredFileUri(
                warehouseName = warehouse.name,
                rowName = row.name,
                palletName = pallet.name,
                cartonName = carton?.name,
                fileName = item.entity.fileName,
            )
            val finalRelPath = savedImage?.relativeFolderPath ?: PersianUtils.buildTargetTicketFolderPath(
                warehouseName = warehouse.name,
                rowName = row.name,
                palletName = pallet.name,
                cartonName = carton?.name,
            )

            restoredTickets.add(
                item.entity.copy(
                    mediaUri = finalUri,
                    relativeFolderPath = finalRelPath,
                ),
            )
        }

        onProgress(
            LocalZipTransferProgress(
                isRunning = true,
                isRestore = true,
                currentStep = totalSteps,
                totalSteps = totalSteps,
                statusText = "در حال ثبت نهایی پایگاه داده…",
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
        }

        return OperationResult.Success(
            LocalBackupRestoreSummary(
                mode = LocalRestoreConflictMode.REPLACE,
                restoredWarehouses = parsed.warehouses.size,
                restoredRows = parsed.rows.size,
                restoredPallets = parsed.pallets.size,
                restoredCartons = parsed.cartons.size,
                restoredTickets = restoredTickets.size,
                restoredImages = restoredImagesCount,
            ),
        )
    }

    private suspend fun executeMergeRestore(
        parsed: CloudBackupManager.ParsedBackupManifest,
        extractedImagesByZipKey: Map<String, File>,
        onProgress: (LocalZipTransferProgress) -> Unit,
    ): OperationResult<LocalBackupRestoreSummary> {
        val totalSteps = parsed.ticketPhotos.size + 1

        // Map backup IDs -> live local database IDs by natural name hierarchy
        val warehouseIdMap = mutableMapOf<Long, Long>()
        val rowIdMap = mutableMapOf<Long, Long>()
        val palletIdMap = mutableMapOf<Long, Long>()
        val cartonIdMap = mutableMapOf<Long, Long>()

        val warehouseNameByNewId = mutableMapOf<Long, String>()
        val rowNameByNewId = mutableMapOf<Long, String>()
        val palletNameByNewId = mutableMapOf<Long, String>()
        val cartonNameByNewId = mutableMapOf<Long, String>()

        var mergedWarehouses = 0
        var mergedRows = 0
        var mergedPallets = 0
        var mergedCartons = 0
        var mergedTickets = 0
        var mergedImages = 0

        database.withTransaction {
            val existingWarehouses = dao.getAllWarehousesOnce().toMutableList()
            for (bWh in parsed.warehouses) {
                val match = existingWarehouses.firstOrNull {
                    it.name.trim().equals(bWh.name.trim(), ignoreCase = true)
                }
                val targetId = if (match != null) {
                    match.id
                } else {
                    val createdId = dao.insertWarehouse(
                        WarehouseEntity(name = bWh.name.trim(), createdAt = bWh.createdAt),
                    )
                    existingWarehouses.add(WarehouseEntity(id = createdId, name = bWh.name.trim(), createdAt = bWh.createdAt))
                    mergedWarehouses++
                    createdId
                }
                warehouseIdMap[bWh.id] = targetId
                warehouseNameByNewId[targetId] = match?.name ?: bWh.name.trim()
            }

            val existingRows = dao.getAllRowsOnce().toMutableList()
            for (bRow in parsed.rows) {
                val targetWhId = warehouseIdMap[bRow.warehouseId] ?: continue
                val match = existingRows.firstOrNull {
                    it.warehouseId == targetWhId && it.name.trim().equals(bRow.name.trim(), ignoreCase = true)
                }
                val targetId = if (match != null) {
                    match.id
                } else {
                    val createdId = dao.insertRow(
                        RowEntity(warehouseId = targetWhId, name = bRow.name.trim(), createdAt = bRow.createdAt),
                    )
                    existingRows.add(RowEntity(id = createdId, warehouseId = targetWhId, name = bRow.name.trim(), createdAt = bRow.createdAt))
                    mergedRows++
                    createdId
                }
                rowIdMap[bRow.id] = targetId
                rowNameByNewId[targetId] = match?.name ?: bRow.name.trim()
            }

            val existingPallets = dao.getAllPalletsOnce().toMutableList()
            for (bPallet in parsed.pallets) {
                val targetRowId = rowIdMap[bPallet.rowId] ?: continue
                val match = existingPallets.firstOrNull {
                    it.rowId == targetRowId && it.name.trim().equals(bPallet.name.trim(), ignoreCase = true)
                }
                val targetId = if (match != null) {
                    val updatedCartons = max(match.cartonCount, bPallet.cartonCount)
                    val updatedItems = max(match.itemCount, bPallet.itemCount)
                    if (updatedCartons != match.cartonCount || updatedItems != match.itemCount) {
                        dao.updatePallet(match.id, match.name, updatedCartons, updatedItems)
                    }
                    match.id
                } else {
                    val createdId = dao.insertPallet(
                        PalletEntity(
                            rowId = targetRowId,
                            name = bPallet.name.trim(),
                            cartonCount = bPallet.cartonCount,
                            itemCount = bPallet.itemCount,
                            createdAt = bPallet.createdAt,
                        ),
                    )
                    existingPallets.add(
                        PalletEntity(
                            id = createdId,
                            rowId = targetRowId,
                            name = bPallet.name.trim(),
                            cartonCount = bPallet.cartonCount,
                            itemCount = bPallet.itemCount,
                            createdAt = bPallet.createdAt,
                        ),
                    )
                    mergedPallets++
                    createdId
                }
                palletIdMap[bPallet.id] = targetId
                palletNameByNewId[targetId] = match?.name ?: bPallet.name.trim()
            }

            val existingCartons = dao.getAllCartonsOnce().toMutableList()
            for (bCarton in parsed.cartons) {
                val targetPalletId = palletIdMap[bCarton.palletId] ?: continue
                val match = existingCartons.firstOrNull {
                    it.palletId == targetPalletId && it.name.trim().equals(bCarton.name.trim(), ignoreCase = true)
                }
                val targetId = if (match != null) {
                    val updatedItems = max(match.itemCount, bCarton.itemCount)
                    if (updatedItems != match.itemCount) {
                        dao.updateCarton(match.id, match.name, updatedItems)
                    }
                    match.id
                } else {
                    val createdId = dao.insertCarton(
                        CartonEntity(
                            palletId = targetPalletId,
                            name = bCarton.name.trim(),
                            itemCount = bCarton.itemCount,
                            createdAt = bCarton.createdAt,
                        ),
                    )
                    existingCartons.add(
                        CartonEntity(
                            id = createdId,
                            palletId = targetPalletId,
                            name = bCarton.name.trim(),
                            itemCount = bCarton.itemCount,
                            createdAt = bCarton.createdAt,
                        ),
                    )
                    mergedCartons++
                    createdId
                }
                cartonIdMap[bCarton.id] = targetId
                cartonNameByNewId[targetId] = match?.name ?: bCarton.name.trim()
            }
        }

        // Ensure physical directories exist for all merged/matched entities
        val allLiveWarehouses = dao.getAllWarehousesOnce().associateBy { it.id }
        val allLiveRows = dao.getAllRowsOnce().associateBy { it.id }
        val allLivePallets = dao.getAllPalletsOnce().associateBy { it.id }
        val allLiveCartons = dao.getAllCartonsOnce().associateBy { it.id }

        allLiveWarehouses.values.forEach { folderManager.ensureWarehouseDirectory(it.name) }
        allLiveRows.values.forEach { row ->
            val wh = allLiveWarehouses[row.warehouseId] ?: return@forEach
            folderManager.ensureRowDirectory(wh.name, row.name)
        }
        allLivePallets.values.forEach { pallet ->
            val row = allLiveRows[pallet.rowId] ?: return@forEach
            val wh = allLiveWarehouses[row.warehouseId] ?: return@forEach
            folderManager.ensurePalletDirectory(wh.name, row.name, pallet.name)
        }
        allLiveCartons.values.forEach { carton ->
            val pallet = allLivePallets[carton.palletId] ?: return@forEach
            val row = allLiveRows[pallet.rowId] ?: return@forEach
            val wh = allLiveWarehouses[row.warehouseId] ?: return@forEach
            folderManager.ensureCartonDirectory(wh.name, row.name, pallet.name, carton.name)
        }

        // Merge ticket photos (skip exact duplicates by target palletId + cartonId + fileName)
        val existingTickets = dao.getAllTicketPhotosOnce()
        val existingKeys = existingTickets.map { "${it.palletId}_${it.cartonId ?: -1L}_${it.fileName}" }.toMutableSet()

        for ((index, item) in parsed.ticketPhotos.withIndex()) {
            onProgress(
                LocalZipTransferProgress(
                    isRunning = true,
                    isRestore = true,
                    currentStep = index + 1,
                    totalSteps = totalSteps,
                    statusText = "در حال ادغام تصویر ${(index + 1).toPersianNumber()} از ${parsed.ticketPhotos.size.toPersianNumber()}…",
                ),
            )

            val targetPalletId = palletIdMap[item.entity.palletId] ?: continue
            val targetCartonId = item.entity.cartonId?.let { cartonIdMap[it] }

            val pallet = allLivePallets[targetPalletId] ?: continue
            val row = allLiveRows[pallet.rowId] ?: continue
            val warehouse = allLiveWarehouses[row.warehouseId] ?: continue
            val carton = targetCartonId?.let { allLiveCartons[it] }

            val dedupKey = "${targetPalletId}_${targetCartonId ?: -1L}_${item.entity.fileName}"
            val stagedFile = findExtractedImageFile(item, extractedImagesByZipKey)

            if (dedupKey in existingKeys) {
                // Ensure the physical image file exists on disk even if DB row already exists
                val expectedUri = folderManager.resolveStoredFileUri(
                    warehouseName = warehouse.name,
                    rowName = row.name,
                    palletName = pallet.name,
                    cartonName = carton?.name,
                    fileName = item.entity.fileName,
                )
                val expectedFile = Uri.parse(expectedUri).path?.let { File(it) }
                if ((expectedFile == null || !expectedFile.exists()) && stagedFile != null && stagedFile.exists()) {
                    folderManager.saveImageBytes(
                        bytes = stagedFile.readBytes(),
                        warehouseName = warehouse.name,
                        rowName = row.name,
                        palletName = pallet.name,
                        cartonName = carton?.name,
                        fileName = item.entity.fileName,
                    )
                    mergedImages++
                }
                continue
            }

            val savedImage = if (stagedFile != null && stagedFile.exists()) {
                mergedImages++
                folderManager.saveImageBytes(
                    bytes = stagedFile.readBytes(),
                    warehouseName = warehouse.name,
                    rowName = row.name,
                    palletName = pallet.name,
                    cartonName = carton?.name,
                    fileName = item.entity.fileName,
                )
            } else {
                null
            }

            val finalUri = savedImage?.fileUri ?: folderManager.resolveStoredFileUri(
                warehouseName = warehouse.name,
                rowName = row.name,
                palletName = pallet.name,
                cartonName = carton?.name,
                fileName = item.entity.fileName,
            )
            val finalRelPath = savedImage?.relativeFolderPath ?: PersianUtils.buildTargetTicketFolderPath(
                warehouseName = warehouse.name,
                rowName = row.name,
                palletName = pallet.name,
                cartonName = carton?.name,
            )

            dao.insertTicketPhoto(
                TicketPhotoEntity(
                    id = 0L, // auto-generate fresh ID to avoid colliding with existing photos
                    palletId = targetPalletId,
                    cartonId = targetCartonId,
                    mediaUri = finalUri,
                    relativeFolderPath = finalRelPath,
                    fileName = item.entity.fileName,
                    capturedAt = item.entity.capturedAt,
                    jalaliDateTime = item.entity.jalaliDateTime,
                    note = item.entity.note,
                ),
            )
            existingKeys.add(dedupKey)
            mergedTickets++
        }

        return OperationResult.Success(
            LocalBackupRestoreSummary(
                mode = LocalRestoreConflictMode.MERGE,
                restoredWarehouses = mergedWarehouses.coerceAtLeast(parsed.warehouses.size),
                restoredRows = mergedRows.coerceAtLeast(parsed.rows.size),
                restoredPallets = mergedPallets.coerceAtLeast(parsed.pallets.size),
                restoredCartons = mergedCartons.coerceAtLeast(parsed.cartons.size),
                restoredTickets = mergedTickets,
                restoredImages = mergedImages,
            ),
        )
    }

    private suspend fun writeFullBackupZipToStream(
        rawOutput: OutputStream,
        fileName: String,
        onProgress: (LocalZipTransferProgress) -> Unit,
    ): LocalBackupExportSummary {
        val warehouses = dao.getAllWarehousesOnce()
        val rows = dao.getAllRowsOnce()
        val pallets = dao.getAllPalletsOnce()
        val cartons = dao.getAllCartonsOnce()
        val ticketPhotos = dao.getAllTicketPhotosOnce()

        val totalSteps = ticketPhotos.size + 1
        onProgress(
            LocalZipTransferProgress(
                isRunning = true,
                isRestore = false,
                currentStep = 0,
                totalSteps = totalSteps,
                statusText = "در حال آماده‌سازی فایل پشتیبان کامل (ZIP)…",
            ),
        )

        val now = System.currentTimeMillis()
        val photoZipEntryKeys = mutableMapOf<Long, String>()
        ticketPhotos.forEach { photo ->
            photoZipEntryKeys[photo.id] = buildZipImageEntryName(photo.relativeFolderPath, photo.fileName)
        }

        val manifestJson = CloudBackupManager.serializeManifest(
            createdAtMillis = now,
            warehouses = warehouses,
            rows = rows,
            pallets = pallets,
            cartons = cartons,
            ticketPhotos = ticketPhotos,
            photoCloudKeys = photoZipEntryKeys,
        )

        var exportedImagesCount = 0
        val addedEntryNames = mutableSetOf<String>()

        ZipOutputStream(BufferedOutputStream(rawOutput)).use { zos ->
            // 1. Write manifest.json
            val manifestEntry = ZipEntry(ZIP_MANIFEST_ENTRY_NAME)
            zos.putNextEntry(manifestEntry)
            zos.write(manifestJson.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            addedEntryNames.add(ZIP_MANIFEST_ENTRY_NAME)

            // 2. Stream every ticket photo into images/<relativeFolderPath>/<fileName>
            for ((index, photo) in ticketPhotos.withIndex()) {
                onProgress(
                    LocalZipTransferProgress(
                        isRunning = true,
                        isRestore = false,
                        currentStep = index + 1,
                        totalSteps = totalSteps,
                        statusText = "در حال فشرده‌سازی تصویر ${(index + 1).toPersianNumber()} از ${ticketPhotos.size.toPersianNumber()}…",
                    ),
                )

                val entryName = photoZipEntryKeys[photo.id]
                    ?: buildZipImageEntryName(photo.relativeFolderPath, photo.fileName)
                if (entryName in addedEntryNames) continue

                val localFile = resolveLocalFile(photo.mediaUri)
                if (localFile != null && localFile.exists() && localFile.isFile) {
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(localFile).use { fis ->
                        fis.copyTo(zos, DEFAULT_BUFFER_SIZE)
                    }
                    zos.closeEntry()
                    addedEntryNames.add(entryName)
                    exportedImagesCount++
                } else {
                    val contentBytes = readContentUriBytes(photo.mediaUri)
                    if (contentBytes != null) {
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.write(contentBytes)
                        zos.closeEntry()
                        addedEntryNames.add(entryName)
                        exportedImagesCount++
                    }
                }
            }
            zos.finish()
            zos.flush()
        }

        return LocalBackupExportSummary(
            exportedWarehouses = warehouses.size,
            exportedPallets = pallets.size,
            exportedCartons = cartons.size,
            exportedTickets = ticketPhotos.size,
            exportedImages = exportedImagesCount,
            fileName = fileName,
            sizeBytes = 0L,
        )
    }

    private fun inspectBackupZipStream(
        sourceUri: Uri,
        rawInput: InputStream,
    ): OperationResult<LocalBackupPreviewInfo> {
        var manifestJson: String? = null
        var imageEntryCount = 0
        var entryCount = 0

        ZipInputStream(BufferedInputStream(rawInput)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    return OperationResult.Error("تعداد فایل‌های داخل آرشیو بیش از حد مجاز است.")
                }
                if (!entry.isDirectory) {
                    val normalizedName = normalizeZipEntryName(entry.name)
                        ?: return OperationResult.Error("فایل ZIP حاوی مسیرهای غیرمجاز است.")

                    if (normalizedName == ZIP_MANIFEST_ENTRY_NAME ||
                        normalizedName == CloudBackupManager.MANIFEST_OBJECT_KEY
                    ) {
                        val bytes = readBoundedEntryBytes(zis, MAX_MANIFEST_BYTES)
                        manifestJson = String(bytes, StandardCharsets.UTF_8)
                    } else if (normalizedName.startsWith("images/") ||
                        normalizedName.startsWith("backup/images/")
                    ) {
                        imageEntryCount++
                    }
                }
                zis.closeEntry()
            }
        }

        val rawManifest = manifestJson
            ?: return OperationResult.Error("فایل انتخاب‌شده فاقد اطلاعات پشتیبان برنامه (manifest.json) است.")

        val parsed = runCatching { CloudBackupManager.parseManifest(rawManifest) }.getOrElse {
            return OperationResult.Error("ساختار اطلاعات داخل فایل پشتیبان نامعتبر یا متعلق به نسخه دیگری است.")
        }

        val jalali = if (parsed.createdAtMillis > 0L) {
            PersianUtils.formatJalaliDateTime(parsed.createdAtMillis)
        } else {
            "نامشخص"
        }

        return OperationResult.Success(
            LocalBackupPreviewInfo(
                sourceUri = sourceUri,
                createdAtMillis = parsed.createdAtMillis,
                createdAtJalali = jalali,
                warehouseCount = parsed.warehouses.size,
                rowCount = parsed.rows.size,
                palletCount = parsed.pallets.size,
                cartonCount = parsed.cartons.size,
                ticketCount = parsed.ticketPhotos.size,
                imageEntryCount = imageEntryCount,
            ),
        )
    }

    private fun findExtractedImageFile(
        item: CloudBackupManager.ManifestTicketEntry,
        extractedImagesByZipKey: Map<String, File>,
    ): File? {
        val rawKey = item.cloudObjectKey
            .trimStart('/')
            .removePrefix("backup/")
            .removePrefix("images/")
            .trimStart('/')
        extractedImagesByZipKey[rawKey]?.let { return it }

        val fallbackKey = "${item.entity.relativeFolderPath.trim('/')}/${item.entity.fileName}"
        extractedImagesByZipKey[fallbackKey]?.let { return it }

        return extractedImagesByZipKey.entries.firstOrNull { (k, _) ->
            k.endsWith("/${item.entity.fileName}") || k == item.entity.fileName
        }?.value
    }

    private fun openSourceInputStream(uri: Uri): InputStream? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { FileInputStream(File(it)) }
            null -> FileInputStream(File(uri.toString()))
            else -> context.contentResolver.openInputStream(uri)
        }
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

    private fun createTempBackupFile(): File {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        return File(dir, "temp_export_${System.nanoTime()}.zip")
    }

    companion object {
        const val ZIP_MANIFEST_ENTRY_NAME = "manifest.json"
        private const val ZIP_IMAGES_PREFIX = "images"
        private const val MAX_ZIP_ENTRIES = 10_000
        private const val MAX_MANIFEST_BYTES = 25 * 1024 * 1024 // 25 MB
        private const val MAX_SINGLE_IMAGE_BYTES = 40L * 1024L * 1024L // 40 MB
        private const val MAX_TOTAL_ARCHIVE_BYTES = 1500L * 1024L * 1024L // 1.5 GB

        fun buildZipImageEntryName(relativeFolderPath: String, fileName: String): String {
            val cleanFolder = relativeFolderPath
                .replace('\\', '/')
                .trim('/')
                .split('/')
                .filter { it.isNotBlank() && it != "." && it != ".." }
                .joinToString("/")
            val cleanFile = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
            return "$ZIP_IMAGES_PREFIX/$cleanFolder/$cleanFile"
        }

        /**
         * Normalizes a ZipEntry path and returns null if it contains any path traversal (`..`) or unsafe segments.
         */
        fun normalizeZipEntryName(rawName: String): String? {
            val unified = rawName.replace('\\', '/').trimStart('/')
            if (unified.isBlank() || unified.contains('\u0000')) return null
            val segments = unified.split('/')
            for (seg in segments) {
                if (seg == ".." || seg == ".") return null
            }
            return segments.filter { it.isNotBlank() }.joinToString("/")
        }

        private fun readBoundedEntryBytes(input: InputStream, maxBytes: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalStateException("حجم فایل فهرست داخل ZIP بیش از حد مجاز است.")
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        private fun copyBoundedEntryToFile(input: InputStream, targetFile: File, maxBytes: Long): Long {
            var total = 0L
            val buffer = ByteArray(16 * 1024)
            FileOutputStream(targetFile).use { fos ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IllegalStateException("حجم فایل تصویر داخل ZIP بیش از حد مجاز است.")
                    }
                    fos.write(buffer, 0, read)
                }
                fos.flush()
            }
            return total
        }
    }
}
