package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OperationResult
import com.example.data.WarehouseRepository
import com.example.data.cloud.CloudBackupManager
import com.example.data.cloud.CloudBackupScheduler
import com.example.data.cloud.CloudResult
import com.example.data.cloud.CloudStorageConfig
import com.example.data.cloud.CloudTransferProgress
import com.example.data.local.AppDatabase
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
import com.example.data.local.RowPathInfo
import com.example.data.local.RowReportItem
import com.example.data.local.RowSummary
import com.example.data.local.TicketDetailItem
import com.example.data.local.TicketPhotoEntity
import com.example.data.local.UserPreferences
import com.example.data.local.WarehouseEntity
import com.example.data.local.WarehouseSummary
import com.example.util.PersianUtils.toPersianNumber
import com.example.util.TicketImageStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CameraSessionState(
    val pendingSaves: Int = 0,
    val capturedPhotos: List<TicketPhotoEntity> = emptyList(),
    val lastError: String? = null,
)

class AnbarViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(application) }
    private val imageStorage: TicketImageStorage by lazy { TicketImageStorage(application) }
    private val userPreferences: UserPreferences by lazy { UserPreferences(application) }

    private val repository: WarehouseRepository by lazy {
        WarehouseRepository(database.warehouseDao(), imageStorage, userPreferences)
    }

    private val cloudBackupManager: CloudBackupManager by lazy {
        CloudBackupManager(
            context = application,
            database = database,
            folderManager = imageStorage.folderManager,
            preferences = userPreferences,
        )
    }

    val cloudStorageConfig: CloudStorageConfig
        get() = cloudBackupManager.config

    val isDarkTheme: StateFlow<Boolean> = repository.isDarkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    val watermarkEnabled: StateFlow<Boolean> = repository.watermarkEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    val cloudBackupPreferences: StateFlow<CloudBackupPreferences> = repository.cloudBackupPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CloudBackupPreferences(),
    )

    val syncedPhotoCount: StateFlow<Int> = repository.syncedPhotoCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0,
    )

    private val _cloudTransferProgress = MutableStateFlow(CloudTransferProgress())
    val cloudTransferProgress: StateFlow<CloudTransferProgress> = _cloudTransferProgress.asStateFlow()

    private val _isTestingCloudConnection = MutableStateFlow(false)
    val isTestingCloudConnection: StateFlow<Boolean> = _isTestingCloudConnection.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = repository.cloudBackupPreferences.first()
            CloudBackupScheduler.applySchedule(
                context = getApplication(),
                enabled = prefs.autoBackupEnabled && cloudBackupManager.config.isConfigured,
                interval = prefs.interval,
                wifiOnly = prefs.wifiOnly,
            )
        }
    }

    val globalStats: StateFlow<GlobalStats> = repository.globalStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GlobalStats(),
    )

    val warehouses: StateFlow<List<WarehouseSummary>> = repository.warehouses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val allTicketDetails: StateFlow<List<TicketDetailItem>> = repository.allTicketDetails.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val rowReportItems: StateFlow<List<RowReportItem>> = repository.rowReportItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val allPallets: StateFlow<List<PalletEntity>> = repository.allPallets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val allCartons: StateFlow<List<CartonEntity>> = repository.allCartons.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val allPalletOptions: StateFlow<List<PalletOptionItem>> = repository.allPalletOptions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun observeTicketDetailById(photoId: Long): Flow<TicketDetailItem?> = repository.observeTicketDetailById(photoId)

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _cameraSession = MutableStateFlow(CameraSessionState())
    val cameraSession: StateFlow<CameraSessionState> = _cameraSession.asStateFlow()

    private val _isImportingGallery = MutableStateFlow(false)
    val isImportingGallery: StateFlow<Boolean> = _isImportingGallery.asStateFlow()

    fun toggleTheme() {
        viewModelScope.launch {
            repository.setDarkTheme(!isDarkTheme.value)
        }
    }

    fun toggleWatermark() {
        viewModelScope.launch {
            repository.setWatermarkEnabled(!watermarkEnabled.value)
        }
    }

    fun resetCameraSession() {
        _cameraSession.value = CameraSessionState()
    }

    fun clearCameraError() {
        _cameraSession.update { it.copy(lastError = null) }
    }

    // --- Observe Flows ---
    fun observeWarehouse(warehouseId: Long): Flow<WarehouseEntity?> = repository.observeWarehouse(warehouseId)

    fun observeRows(warehouseId: Long): Flow<List<RowSummary>> = repository.observeRows(warehouseId)

    fun observeRowPathInfo(rowId: Long): Flow<RowPathInfo?> = repository.observeRowPathInfo(rowId)

    fun observePallets(rowId: Long): Flow<List<PalletSummary>> = repository.observePallets(rowId)

    fun observePalletSummary(palletId: Long): Flow<PalletSummary?> = repository.observePalletSummary(palletId)

    fun observePalletPathInfo(palletId: Long): Flow<PalletPathInfo?> = repository.observePalletPathInfo(palletId)

    fun observeCartons(palletId: Long): Flow<List<CartonSummary>> = repository.observeCartons(palletId)

    fun observeCartonPathInfo(cartonId: Long): Flow<CartonPathInfo?> = repository.observeCartonPathInfo(cartonId)

    fun observePalletTickets(palletId: Long): Flow<List<TicketPhotoEntity>> = repository.observePalletTickets(palletId)

    fun observeCartonTickets(cartonId: Long): Flow<List<TicketPhotoEntity>> = repository.observeCartonTickets(cartonId)

    // --- Warehouses CRUD ---
    fun addWarehouse(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.addWarehouse(name)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("انبار «${name.trim()}» ایجاد شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun renameWarehouse(id: Long, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.renameWarehouse(id, name)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("نام انبار به‌روزرسانی شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun deleteWarehouse(id: Long) {
        viewModelScope.launch {
            repository.deleteWarehouse(id)
            _snackbarMessages.tryEmit("انبار و تمام تیکت‌های مرتبط حذف شدند.")
            scheduleDebouncedAutoBackupIfEnabled()
        }
    }

    // --- Rows CRUD ---
    fun addRow(warehouseId: Long, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.addRow(warehouseId, name)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("ردیف «${name.trim()}» اضافه شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun renameRow(warehouseId: Long, rowId: Long, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repository.renameRow(warehouseId, rowId, name)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("نام ردیف ویرایش شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun deleteRow(rowId: Long) {
        viewModelScope.launch {
            repository.deleteRow(rowId)
            _snackbarMessages.tryEmit("ردیف و پالت‌های زیرمجموعه حذف شدند.")
            scheduleDebouncedAutoBackupIfEnabled()
        }
    }

    // --- Pallets CRUD ---
    fun addPallet(
        rowId: Long,
        name: String,
        cartonCount: Int,
        itemCount: Int,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val res = repository.addPallet(rowId, name, cartonCount, itemCount)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("پالت «${name.trim()}» ثبت شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun updatePallet(
        rowId: Long,
        palletId: Long,
        name: String,
        cartonCount: Int,
        itemCount: Int,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val res = repository.updatePallet(rowId, palletId, name, cartonCount, itemCount)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("اطلاعات پالت ذخیره شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun deletePallet(palletId: Long) {
        viewModelScope.launch {
            repository.deletePallet(palletId)
            _snackbarMessages.tryEmit("پالت و عکس‌های آن حذف شدند.")
            scheduleDebouncedAutoBackupIfEnabled()
        }
    }

    // --- Cartons CRUD ---
    fun addCarton(
        palletId: Long,
        name: String,
        itemCount: Int,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val res = repository.addCarton(palletId, name, itemCount)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("پوشه کارتن «${name.trim()}» اضافه شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun updateCarton(
        palletId: Long,
        cartonId: Long,
        name: String,
        itemCount: Int,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val res = repository.updateCarton(palletId, cartonId, name, itemCount)) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("اطلاعات کارتن ذخیره شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    fun deleteCarton(cartonId: Long) {
        viewModelScope.launch {
            repository.deleteCarton(cartonId)
            _snackbarMessages.tryEmit("کارتن و تیکت‌های آن حذف شدند.")
            scheduleDebouncedAutoBackupIfEnabled()
        }
    }

    // --- Photos ---
    fun saveCapturedTicketFile(tempFile: File, palletId: Long, cartonId: Long?) {
        _cameraSession.update { it.copy(pendingSaves = it.pendingSaves + 1, lastError = null) }
        viewModelScope.launch {
            when (val res = repository.saveCapturedTicketFile(tempFile, palletId, cartonId)) {
                is OperationResult.Success -> {
                    _cameraSession.update { state ->
                        state.copy(
                            pendingSaves = (state.pendingSaves - 1).coerceAtLeast(0),
                            capturedPhotos = listOf(res.data) + state.capturedPhotos,
                        )
                    }
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> {
                    _cameraSession.update { state ->
                        state.copy(
                            pendingSaves = (state.pendingSaves - 1).coerceAtLeast(0),
                            lastError = res.message,
                        )
                    }
                }
            }
        }
    }

    fun importGalleryTickets(
        uris: List<Uri>,
        palletId: Long,
        cartonId: Long?,
        updateCameraSession: Boolean = false,
    ) {
        if (uris.isEmpty()) return
        _isImportingGallery.value = true
        if (updateCameraSession) {
            _cameraSession.update { it.copy(pendingSaves = it.pendingSaves + uris.size, lastError = null) }
        }
        viewModelScope.launch {
            when (val res = repository.importGalleryTickets(uris, palletId, cartonId)) {
                is OperationResult.Success -> {
                    if (updateCameraSession) {
                        _cameraSession.update { state ->
                            state.copy(
                                pendingSaves = (state.pendingSaves - uris.size).coerceAtLeast(0),
                                capturedPhotos = res.data.reversed() + state.capturedPhotos,
                            )
                        }
                    }
                    _snackbarMessages.tryEmit("${res.data.size.toPersianNumber()} عکس در پوشه تفکیک‌شده ذخیره شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> {
                    if (updateCameraSession) {
                        _cameraSession.update { state ->
                            state.copy(
                                pendingSaves = (state.pendingSaves - uris.size).coerceAtLeast(0),
                                lastError = res.message,
                            )
                        }
                    }
                    _snackbarMessages.tryEmit(res.message)
                }
            }
            _isImportingGallery.value = false
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
            _snackbarMessages.tryEmit("عکس تیکت از پوشه حافظه حذف شد.")
            scheduleDebouncedAutoBackupIfEnabled()
        }
    }

    fun updateTicket(
        photoId: Long,
        newPalletId: Long,
        newCartonId: Long?,
        jalaliDateStr: String,
        jalaliTimeStr: String,
        note: String,
        updatedPalletCartonCount: Int?,
        updatedPalletItemCount: Int?,
        updatedCartonItemCount: Int?,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (
                val res = repository.updateTicket(
                    photoId = photoId,
                    newPalletId = newPalletId,
                    newCartonId = newCartonId,
                    jalaliDateStr = jalaliDateStr,
                    jalaliTimeStr = jalaliTimeStr,
                    note = note,
                    updatedPalletCartonCount = updatedPalletCartonCount,
                    updatedPalletItemCount = updatedPalletItemCount,
                    updatedCartonItemCount = updatedCartonItemCount,
                )
            ) {
                is OperationResult.Success -> {
                    onResult(null)
                    _snackbarMessages.tryEmit("اطلاعات تیکت با موفقیت ویرایش شد.")
                    scheduleDebouncedAutoBackupIfEnabled()
                }
                is OperationResult.Error -> onResult(res.message)
            }
        }
    }

    // --- Cloud Backup & Restore (ArvanCloud / S3) ---
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoBackupEnabled(enabled)
            val prefs = repository.cloudBackupPreferences.first()
            CloudBackupScheduler.applySchedule(
                context = getApplication(),
                enabled = enabled && cloudBackupManager.config.isConfigured,
                interval = prefs.interval,
                wifiOnly = prefs.wifiOnly,
            )
            _snackbarMessages.tryEmit(
                if (enabled) "پشتیبان‌گیری خودکار ابری فعال شد." else "پشتیبان‌گیری خودکار ابری غیرفعال شد.",
            )
        }
    }

    fun setBackupWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            repository.setBackupWifiOnly(wifiOnly)
            val prefs = repository.cloudBackupPreferences.first()
            CloudBackupScheduler.applySchedule(
                context = getApplication(),
                enabled = prefs.autoBackupEnabled && cloudBackupManager.config.isConfigured,
                interval = prefs.interval,
                wifiOnly = wifiOnly,
            )
        }
    }

    fun setBackupInterval(interval: BackupIntervalOption) {
        viewModelScope.launch {
            repository.setBackupInterval(interval)
            val prefs = repository.cloudBackupPreferences.first()
            CloudBackupScheduler.applySchedule(
                context = getApplication(),
                enabled = prefs.autoBackupEnabled && cloudBackupManager.config.isConfigured,
                interval = interval,
                wifiOnly = prefs.wifiOnly,
            )
            _snackbarMessages.tryEmit("بازه پشتیبان‌گیری خودکار به «${interval.label}» تغییر یافت.")
        }
    }

    fun testCloudConnection() {
        if (_isTestingCloudConnection.value || _cloudTransferProgress.value.isRunning) return
        _isTestingCloudConnection.value = true
        viewModelScope.launch {
            when (val res = cloudBackupManager.testConnection()) {
                is CloudResult.Success -> {
                    _snackbarMessages.tryEmit("اتصال به فضای ابری با موفقیت برقرار شد.")
                }
                is CloudResult.Error -> {
                    _snackbarMessages.tryEmit(res.message)
                }
            }
            _isTestingCloudConnection.value = false
        }
    }

    fun backupNowToCloud() {
        if (_cloudTransferProgress.value.isRunning) return
        viewModelScope.launch {
            _cloudTransferProgress.value = CloudTransferProgress(
                isRunning = true,
                isRestore = false,
                statusText = "شروع پشتیبان‌گیری ابری…",
            )
            when (
                val res = cloudBackupManager.backupNow { progress ->
                    _cloudTransferProgress.value = progress
                }
            ) {
                is CloudResult.Success -> {
                    val info = res.data
                    _snackbarMessages.tryEmit(
                        "پشتیبان‌گیری کامل شد (${info.uploadedImagesCount.toPersianNumber()} تصویر جدید ارسال شد).",
                    )
                }
                is CloudResult.Error -> {
                    _snackbarMessages.tryEmit(res.message)
                }
            }
            _cloudTransferProgress.value = CloudTransferProgress()
        }
    }

    fun restoreFromCloud() {
        if (_cloudTransferProgress.value.isRunning) return
        viewModelScope.launch {
            _cloudTransferProgress.value = CloudTransferProgress(
                isRunning = true,
                isRestore = true,
                statusText = "شروع بازیابی اطلاعات از فضای ابری…",
            )
            when (
                val res = cloudBackupManager.restoreFromCloud { progress ->
                    _cloudTransferProgress.value = progress
                }
            ) {
                is CloudResult.Success -> {
                    val info = res.data
                    _snackbarMessages.tryEmit(
                        "بازیابی انجام شد: ${info.restoredWarehouses.toPersianNumber()} انبار و ${info.downloadedImages.toPersianNumber()} تصویر بازیابی شدند.",
                    )
                }
                is CloudResult.Error -> {
                    _snackbarMessages.tryEmit(res.message)
                }
            }
            _cloudTransferProgress.value = CloudTransferProgress()
        }
    }

    private fun scheduleDebouncedAutoBackupIfEnabled() {
        viewModelScope.launch {
            val prefs = repository.cloudBackupPreferences.first()
            if (prefs.autoBackupEnabled && cloudBackupManager.config.isConfigured) {
                CloudBackupScheduler.triggerDebouncedAutoBackup(
                    context = getApplication(),
                    wifiOnly = prefs.wifiOnly,
                )
            }
        }
    }
}
