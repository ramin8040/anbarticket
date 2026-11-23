package com.example.data

import android.net.Uri
import com.example.data.local.BackupIntervalOption
import com.example.data.local.CartonEntity
import com.example.data.local.CartonPathInfo
import com.example.data.local.CartonSummary
import com.example.data.local.CloudBackupPreferences
import com.example.data.local.GlobalStats
import com.example.data.local.PalletEntity
import com.example.data.local.PalletOptionItem
import com.example.data.local.PalletPathInfo
import com.example.data.local.PalletSummary
import com.example.data.local.RowEntity
import com.example.data.local.RowPathInfo
import com.example.data.local.RowReportItem
import com.example.data.local.RowSummary
import com.example.data.local.TicketDetailItem
import com.example.data.local.TicketPhotoEntity
import com.example.data.local.UserPreferences
import com.example.data.local.WarehouseDao
import com.example.data.local.WarehouseEntity
import com.example.data.local.WarehouseSummary
import com.example.util.InternalImageFolderManager
import com.example.util.InternalStorageSummary
import com.example.util.PersianUtils
import com.example.util.StorageFolderNode
import com.example.util.TicketImageStorage
import com.example.util.WatermarkInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

sealed class OperationResult<out T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Error(val message: String) : OperationResult<Nothing>()
}

class WarehouseRepository(
    private val dao: WarehouseDao,
    private val imageStorage: TicketImageStorage,
    private val preferences: UserPreferences,
    private val folderManager: InternalImageFolderManager = imageStorage.folderManager,
) {
    val isDarkTheme: Flow<Boolean> = preferences.isDarkTheme
    val watermarkEnabled: Flow<Boolean> = preferences.watermarkEnabled
    val cloudBackupPreferences: Flow<CloudBackupPreferences> = preferences.cloudBackupPreferences
    val syncedPhotoCount: Flow<Int> = dao.observeSyncedPhotoCount()
    val globalStats: Flow<GlobalStats> = dao.observeGlobalStats()
    val warehouses: Flow<List<WarehouseSummary>> = dao.observeWarehouseSummaries()
    val allTicketDetails: Flow<List<TicketDetailItem>> = dao.observeAllTicketDetails()
    val rowReportItems: Flow<List<RowReportItem>> = dao.observeRowReportItems()
    val allPallets: Flow<List<PalletEntity>> = dao.observeAllPallets()
    val allCartons: Flow<List<CartonEntity>> = dao.observeAllCartons()
    val allPalletOptions: Flow<List<PalletOptionItem>> = dao.observeAllPalletOptions()

    fun observeTicketDetailById(photoId: Long): Flow<TicketDetailItem?> = dao.observeTicketDetailById(photoId)

    suspend fun getDirectoryTree(): StorageFolderNode = folderManager.getDirectoryTree()

    suspend fun getStorageSummary(): InternalStorageSummary = folderManager.getStorageSummary()

    suspend fun setDarkTheme(enabled: Boolean) = preferences.setDarkTheme(enabled)

    suspend fun setWatermarkEnabled(enabled: Boolean) = preferences.setWatermarkEnabled(enabled)

    suspend fun setAutoBackupEnabled(enabled: Boolean) = preferences.setAutoBackupEnabled(enabled)

    suspend fun setBackupWifiOnly(wifiOnly: Boolean) = preferences.setBackupWifiOnly(wifiOnly)

    suspend fun setBackupInterval(interval: BackupIntervalOption) = preferences.setBackupInterval(interval)

    fun observeWarehouse(warehouseId: Long): Flow<WarehouseEntity?> = dao.observeWarehouse(warehouseId)

    fun observeRows(warehouseId: Long): Flow<List<RowSummary>> = dao.observeRowSummaries(warehouseId)

    fun observeRowPathInfo(rowId: Long): Flow<RowPathInfo?> = dao.observeRowPathInfo(rowId)

    fun observePallets(rowId: Long): Flow<List<PalletSummary>> = dao.observePalletSummaries(rowId)

    fun observePalletSummary(palletId: Long): Flow<PalletSummary?> = dao.observePalletSummary(palletId)

    fun observePalletPathInfo(palletId: Long): Flow<PalletPathInfo?> = dao.observePalletPathInfo(palletId)

    fun observeCartons(palletId: Long): Flow<List<CartonSummary>> = dao.observeCartonSummaries(palletId)

    fun observeCartonPathInfo(cartonId: Long): Flow<CartonPathInfo?> = dao.observeCartonPathInfo(cartonId)

    fun observePalletTickets(palletId: Long): Flow<List<TicketPhotoEntity>> = dao.observePalletTickets(palletId)

    fun observeCartonTickets(cartonId: Long): Flow<List<TicketPhotoEntity>> = dao.observeCartonTickets(cartonId)

    // --- Warehouses CRUD (Synced with Real Internal Storage Tree) ---
    suspend fun addWarehouse(rawName: String): OperationResult<Long> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام انبار را وارد کنید.")
        if (dao.countWarehousesWithName(name) > 0) {
            return OperationResult.Error("انباری با این نام قبلاً ثبت شده است.")
        }
        val id = dao.insertWarehouse(WarehouseEntity(name = name))
        folderManager.ensureWarehouseDirectory(name)
        return OperationResult.Success(id)
    }

    suspend fun renameWarehouse(id: Long, rawName: String): OperationResult<Unit> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام انبار را وارد کنید.")
        if (dao.countWarehousesWithName(name, excludeId = id) > 0) {
            return OperationResult.Error("انباری با این نام قبلاً ثبت شده است.")
        }
        val oldWarehouse = dao.getWarehouseByIdOnce(id)
        if (oldWarehouse != null && oldWarehouse.name != name) {
            folderManager.renameWarehouseDirectory(oldWarehouse.name, name)
        }
        dao.updateWarehouseName(id, name)
        syncTicketPaths(dao.getTicketDetailsForWarehouse(id))
        return OperationResult.Success(Unit)
    }

    suspend fun deleteWarehouse(id: Long) {
        val warehouse = dao.getWarehouseByIdOnce(id)
        val uris = dao.getPhotoUrisForWarehouse(id)
        uris.forEach { imageStorage.deleteSavedPhoto(it) }
        if (warehouse != null) {
            folderManager.deleteWarehouseDirectory(warehouse.name)
        }
        dao.deleteWarehouseById(id)
    }

    // --- Rows CRUD (Synced with Real Internal Storage Tree) ---
    suspend fun addRow(warehouseId: Long, rawName: String): OperationResult<Long> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام یا شماره ردیف را وارد کنید.")
        if (dao.countRowsWithName(warehouseId, name) > 0) {
            return OperationResult.Error("ردیفی با این نام در این انبار قبلاً ثبت شده است.")
        }
        val id = dao.insertRow(RowEntity(warehouseId = warehouseId, name = name))
        dao.getWarehouseByIdOnce(warehouseId)?.let { wh ->
            folderManager.ensureRowDirectory(wh.name, name)
        }
        return OperationResult.Success(id)
    }

    suspend fun renameRow(warehouseId: Long, rowId: Long, rawName: String): OperationResult<Unit> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام یا شماره ردیف را وارد کنید.")
        if (dao.countRowsWithName(warehouseId, name, excludeId = rowId) > 0) {
            return OperationResult.Error("ردیفی با این نام در این انبار قبلاً ثبت شده است.")
        }
        val oldInfo = dao.getRowPathInfoOnce(rowId)
        if (oldInfo != null && oldInfo.rowName != name) {
            folderManager.renameRowDirectory(oldInfo.warehouseName, oldInfo.rowName, name)
        }
        dao.updateRowName(rowId, name)
        syncTicketPaths(dao.getTicketDetailsForRow(rowId))
        return OperationResult.Success(Unit)
    }

    suspend fun deleteRow(rowId: Long) {
        val rowInfo = dao.getRowPathInfoOnce(rowId)
        val uris = dao.getPhotoUrisForRow(rowId)
        uris.forEach { imageStorage.deleteSavedPhoto(it) }
        if (rowInfo != null) {
            folderManager.deleteRowDirectory(rowInfo.warehouseName, rowInfo.rowName)
        }
        dao.deleteRowById(rowId)
    }

    // --- Pallets CRUD (Synced with Real Internal Storage Tree) ---
    suspend fun addPallet(
        rowId: Long,
        rawName: String,
        cartonCount: Int,
        itemCount: Int,
    ): OperationResult<Long> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام یا شماره پالت را وارد کنید.")
        if (dao.countPalletsWithName(rowId, name) > 0) {
            return OperationResult.Error("پالتی با این نام در این ردیف قبلاً ثبت شده است.")
        }
        val id = dao.insertPallet(
            PalletEntity(
                rowId = rowId,
                name = name,
                cartonCount = cartonCount.coerceAtLeast(0),
                itemCount = itemCount.coerceAtLeast(0),
            ),
        )
        dao.getRowPathInfoOnce(rowId)?.let { rowInfo ->
            folderManager.ensurePalletDirectory(rowInfo.warehouseName, rowInfo.rowName, name)
        }
        return OperationResult.Success(id)
    }

    suspend fun updatePallet(
        rowId: Long,
        palletId: Long,
        rawName: String,
        cartonCount: Int,
        itemCount: Int,
    ): OperationResult<Unit> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام یا شماره پالت را وارد کنید.")
        if (dao.countPalletsWithName(rowId, name, excludeId = palletId) > 0) {
            return OperationResult.Error("پالتی با این نام در این ردیف قبلاً ثبت شده است.")
        }
        val oldInfo = dao.getPalletPathInfoOnce(palletId)
        val nameChanged = oldInfo != null && oldInfo.palletName != name
        if (nameChanged && oldInfo != null) {
            folderManager.renamePalletDirectory(oldInfo.warehouseName, oldInfo.rowName, oldInfo.palletName, name)
        }
        dao.updatePallet(
            id = palletId,
            name = name,
            cartonCount = cartonCount.coerceAtLeast(0),
            itemCount = itemCount.coerceAtLeast(0),
        )
        if (nameChanged) {
            syncTicketPaths(dao.getTicketDetailsForPallet(palletId))
        }
        return OperationResult.Success(Unit)
    }

    suspend fun deletePallet(palletId: Long) {
        val palletInfo = dao.getPalletPathInfoOnce(palletId)
        val uris = dao.getPhotoUrisForPallet(palletId)
        uris.forEach { imageStorage.deleteSavedPhoto(it) }
        if (palletInfo != null) {
            folderManager.deletePalletDirectory(
                palletInfo.warehouseName,
                palletInfo.rowName,
                palletInfo.palletName,
            )
        }
        dao.deletePalletById(palletId)
    }

    // --- Cartons CRUD (Synced with Real Internal Storage Tree) ---
    suspend fun addCarton(
        palletId: Long,
        rawName: String,
        itemCount: Int,
    ): OperationResult<Long> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام یا شماره کارتن را وارد کنید.")
        if (dao.countCartonsWithName(palletId, name) > 0) {
            return OperationResult.Error("کارتنی با این نام روی این پالت قبلاً ثبت شده است.")
        }
        val id = dao.insertCarton(
            CartonEntity(
                palletId = palletId,
                name = name,
                itemCount = itemCount.coerceAtLeast(0),
            ),
        )
        dao.getPalletPathInfoOnce(palletId)?.let { palletInfo ->
            folderManager.ensureCartonDirectory(
                palletInfo.warehouseName,
                palletInfo.rowName,
                palletInfo.palletName,
                name,
            )
        }
        return OperationResult.Success(id)
    }

    suspend fun updateCarton(
        palletId: Long,
        cartonId: Long,
        rawName: String,
        itemCount: Int,
    ): OperationResult<Unit> {
        val name = rawName.trim()
        if (name.isEmpty()) return OperationResult.Error("لطفاً نام یا شماره کارتن را وارد کنید.")
        if (dao.countCartonsWithName(palletId, name, excludeId = cartonId) > 0) {
            return OperationResult.Error("کارتنی با این نام روی این پالت قبلاً ثبت شده است.")
        }
        val oldInfo = dao.getCartonPathInfoOnce(cartonId)
        val nameChanged = oldInfo != null && oldInfo.cartonName != name
        if (nameChanged && oldInfo != null) {
            folderManager.renameCartonDirectory(
                oldInfo.warehouseName,
                oldInfo.rowName,
                oldInfo.palletName,
                oldInfo.cartonName,
                name,
            )
        }
        dao.updateCarton(
            id = cartonId,
            name = name,
            itemCount = itemCount.coerceAtLeast(0),
        )
        if (nameChanged) {
            syncTicketPaths(dao.getTicketDetailsForCarton(cartonId))
        }
        return OperationResult.Success(Unit)
    }

    suspend fun deleteCarton(cartonId: Long) {
        val cartonInfo = dao.getCartonPathInfoOnce(cartonId)
        val uris = dao.getPhotoUrisForCarton(cartonId)
        uris.forEach { imageStorage.deleteSavedPhoto(it) }
        if (cartonInfo != null) {
            folderManager.deleteCartonDirectory(
                cartonInfo.warehouseName,
                cartonInfo.rowName,
                cartonInfo.palletName,
                cartonInfo.cartonName,
            )
        }
        dao.deleteCartonById(cartonId)
    }

    // --- Ticket Photo Capture & Gallery Import ---
    suspend fun saveCapturedTicketFile(
        tempFile: File,
        palletId: Long,
        cartonId: Long?,
    ): OperationResult<TicketPhotoEntity> {
        return try {
            val now = System.currentTimeMillis()
            val applyWatermark = preferences.watermarkEnabled.first()
            val watermarkInfo = buildWatermarkInfo(palletId, cartonId, now)
                ?: return OperationResult.Error("مسیر پالت یا کارتن یافت نشد.")

            val saved = imageStorage.saveFromTempFile(
                tempFile = tempFile,
                watermarkInfo = watermarkInfo,
                applyWatermark = applyWatermark,
                capturedAtMillis = now,
            )
            val entity = TicketPhotoEntity(
                palletId = palletId,
                cartonId = cartonId,
                mediaUri = saved.mediaUri,
                relativeFolderPath = saved.relativeFolderPath,
                fileName = saved.fileName,
                capturedAt = saved.capturedAtMillis,
                jalaliDateTime = saved.jalaliDateTime,
            )
            val id = dao.insertTicketPhoto(entity)
            OperationResult.Success(entity.copy(id = id))
        } catch (e: Exception) {
            OperationResult.Error(e.message ?: "خطا در ذخیره عکس تیکت")
        }
    }

    suspend fun importGalleryTickets(
        uris: List<Uri>,
        palletId: Long,
        cartonId: Long?,
    ): OperationResult<List<TicketPhotoEntity>> {
        if (uris.isEmpty()) return OperationResult.Success(emptyList())
        return try {
            val applyWatermark = preferences.watermarkEnabled.first()
            val savedEntities = mutableListOf<TicketPhotoEntity>()
            for ((index, uri) in uris.withIndex()) {
                val now = System.currentTimeMillis() + index
                val watermarkInfo = buildWatermarkInfo(palletId, cartonId, now)
                    ?: return OperationResult.Error("مسیر پالت یا کارتن یافت نشد.")

                val saved = imageStorage.saveFromGalleryUri(
                    sourceUri = uri,
                    watermarkInfo = watermarkInfo,
                    applyWatermark = applyWatermark,
                    capturedAtMillis = now,
                )
                val entity = TicketPhotoEntity(
                    palletId = palletId,
                    cartonId = cartonId,
                    mediaUri = saved.mediaUri,
                    relativeFolderPath = saved.relativeFolderPath,
                    fileName = saved.fileName,
                    capturedAt = saved.capturedAtMillis,
                    jalaliDateTime = saved.jalaliDateTime,
                )
                val id = dao.insertTicketPhoto(entity)
                savedEntities.add(entity.copy(id = id))
            }
            OperationResult.Success(savedEntities)
        } catch (e: Exception) {
            OperationResult.Error(e.message ?: "خطا در وارد کردن تصاویر از گالری")
        }
    }

    suspend fun deletePhoto(photoId: Long) {
        val photo = dao.getTicketPhotoById(photoId) ?: return
        imageStorage.deleteSavedPhoto(photo.mediaUri)
        dao.deleteTicketPhotoById(photoId)
    }

    suspend fun updateTicket(
        photoId: Long,
        newPalletId: Long,
        newCartonId: Long?,
        jalaliDateStr: String,
        jalaliTimeStr: String,
        note: String,
        updatedPalletCartonCount: Int? = null,
        updatedPalletItemCount: Int? = null,
        updatedCartonItemCount: Int? = null,
    ): OperationResult<Unit> {
        val existing = dao.getTicketPhotoById(photoId)
            ?: return OperationResult.Error("تیکت مورد نظر یافت نشد.")

        val parsed = PersianUtils.parseJalaliDateTime(jalaliDateStr, jalaliTimeStr)
            ?: return OperationResult.Error("فرمت تاریخ شمسی (مثلاً ۱۴۰۵/۰۹/۰۱) یا ساعت (مثلاً ۱۴:۳۰) معتبر نیست.")
        val (newTimestamp, normalizedJalali) = parsed

        val palletInfo = dao.getPalletPathInfoOnce(newPalletId)
            ?: return OperationResult.Error("پالت انتخاب‌شده یافت نشد.")

        val cartonInfo = if (newCartonId != null) {
            dao.getCartonPathInfoOnce(newCartonId)
                ?: return OperationResult.Error("کارتن انتخاب‌شده یافت نشد.")
        } else {
            null
        }

        val newFolderPath = PersianUtils.buildTargetTicketFolderPath(
            warehouseName = palletInfo.warehouseName,
            rowName = palletInfo.rowName,
            palletName = palletInfo.palletName,
            cartonName = cartonInfo?.cartonName,
        )

        val finalMediaUri = if (newFolderPath != existing.relativeFolderPath) {
            imageStorage.movePhotoToTarget(
                sourceMediaUri = existing.mediaUri,
                targetWarehouseName = palletInfo.warehouseName,
                targetRowName = palletInfo.rowName,
                targetPalletName = palletInfo.palletName,
                targetCartonName = cartonInfo?.cartonName,
                fileName = existing.fileName,
            ) ?: existing.mediaUri
        } else {
            existing.mediaUri
        }

        dao.updateTicketPhoto(
            photoId = photoId,
            palletId = newPalletId,
            cartonId = newCartonId,
            mediaUri = finalMediaUri,
            relativeFolderPath = newFolderPath,
            capturedAt = newTimestamp,
            jalaliDateTime = normalizedJalali,
            note = note.trim(),
        )

        if (newCartonId == null && (updatedPalletCartonCount != null || updatedPalletItemCount != null)) {
            dao.updatePallet(
                id = newPalletId,
                name = palletInfo.palletName,
                cartonCount = (updatedPalletCartonCount ?: palletInfo.cartonCount).coerceAtLeast(0),
                itemCount = (updatedPalletItemCount ?: palletInfo.itemCount).coerceAtLeast(0),
            )
        } else if (cartonInfo != null && updatedCartonItemCount != null) {
            dao.updateCarton(
                id = cartonInfo.cartonId,
                name = cartonInfo.cartonName,
                itemCount = updatedCartonItemCount.coerceAtLeast(0),
            )
        }

        return OperationResult.Success(Unit)
    }

    private suspend fun syncTicketPaths(tickets: List<TicketDetailItem>) {
        for (ticket in tickets) {
            val newRelativePath = PersianUtils.buildTargetTicketFolderPath(
                warehouseName = ticket.warehouseName,
                rowName = ticket.rowName,
                palletName = ticket.palletName,
                cartonName = ticket.cartonName,
            )
            val newUri = if (Uri.parse(ticket.mediaUri).scheme == "file") {
                folderManager.resolveStoredFileUri(
                    warehouseName = ticket.warehouseName,
                    rowName = ticket.rowName,
                    palletName = ticket.palletName,
                    cartonName = ticket.cartonName,
                    fileName = ticket.fileName,
                )
            } else {
                ticket.mediaUri
            }
            dao.updateTicketPhotoPath(
                photoId = ticket.id,
                mediaUri = newUri,
                relativeFolderPath = newRelativePath,
            )
        }
    }

    private suspend fun buildWatermarkInfo(
        palletId: Long,
        cartonId: Long?,
        timestampMillis: Long,
    ): WatermarkInfo? {
        val jalali = PersianUtils.formatJalaliDateTime(timestampMillis)
        return if (cartonId != null) {
            val info = dao.getCartonPathInfoOnce(cartonId) ?: return null
            WatermarkInfo(
                warehouseName = info.warehouseName,
                rowName = info.rowName,
                palletName = info.palletName,
                cartonName = info.cartonName,
                cartonCount = 0,
                itemCount = info.itemCount,
                jalaliDateTime = jalali,
            )
        } else {
            val info = dao.getPalletPathInfoOnce(palletId) ?: return null
            WatermarkInfo(
                warehouseName = info.warehouseName,
                rowName = info.rowName,
                palletName = info.palletName,
                cartonName = null,
                cartonCount = info.cartonCount,
                itemCount = info.itemCount,
                jalaliDateTime = jalali,
            )
        }
    }
}
