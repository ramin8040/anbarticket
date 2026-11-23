package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CartonEntity
import com.example.data.local.CartonPathInfo
import com.example.data.local.PalletOptionItem
import com.example.data.local.TicketDetailItem
import com.example.data.local.TicketPhotoEntity
import com.example.ui.components.CartonFormDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTicketDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FolderPathBanner
import com.example.ui.components.HierarchyBreadcrumb
import com.example.ui.components.ThemeToggleButton
import com.example.ui.components.TicketPhotoTile
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartonDetailScreen(
    cartonInfo: CartonPathInfo?,
    cartonTickets: List<TicketPhotoEntity>,
    palletOptions: List<PalletOptionItem>,
    allCartons: List<CartonEntity>,
    isDarkTheme: Boolean,
    watermarkEnabled: Boolean,
    isImportingGallery: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleWatermark: () -> Unit,
    onOpenCameraForCarton: () -> Unit,
    onImportGalleryForCarton: (List<Uri>) -> Unit,
    onOpenPhotoViewer: (photoIndex: Int) -> Unit,
    onUpdateTicket: (
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
    ) -> Unit,
    onDeletePhoto: (Long) -> Unit,
    onUpdateCarton: (name: String, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTicketPhoto by remember { mutableStateOf<TicketPhotoEntity?>(null) }
    var deletingTicketPhoto by remember { mutableStateOf<TicketPhotoEntity?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onImportGalleryForCarton(uris)
            }
        },
    )

    val warehouseName = cartonInfo?.warehouseName ?: "انبار"
    val rowName = cartonInfo?.rowName ?: "ردیف"
    val palletName = cartonInfo?.palletName ?: "پالت"
    val cartonName = cartonInfo?.cartonName ?: "کارتن"
    val itemCount = cartonInfo?.itemCount ?: 0

    val targetFolderPath = PersianUtils.buildTargetTicketFolderPath(
        warehouseName = warehouseName,
        rowName = rowName,
        palletName = palletName,
        cartonName = cartonName,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = cartonName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$palletName  •  ${cartonTickets.size.toPersianNumber()} عکس تیکت",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("carton_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_carton_detail_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "ویرایش کارتن",
                        )
                    }
                    ThemeToggleButton(
                        isDarkTheme = isDarkTheme,
                        onToggle = onToggleTheme,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "breadcrumb") {
                    HierarchyBreadcrumb(
                        segments = listOf(warehouseName, rowName, palletName, cartonName),
                    )
                }

                item(key = "carton_item_count_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Layers,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "تعداد اقلام شمارش‌شده کارتن",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${itemCount.toPersianNumber()} قلم",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("inline_edit_carton_count"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ویرایش", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                item(key = "folder_path") {
                    FolderPathBanner(folderPath = targetFolderPath)
                }

                item(key = "carton_photo_actions") {
                    TicketCaptureActionBar(
                        watermarkEnabled = watermarkEnabled,
                        isImportingGallery = isImportingGallery,
                        onToggleWatermark = onToggleWatermark,
                        onOpenCamera = onOpenCameraForCarton,
                        onPickGallery = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        testTagPrefix = "carton",
                    )
                }

                if (cartonTickets.isEmpty()) {
                    item(key = "empty_carton_tickets") {
                        EmptyStatePanel(
                            title = "عکس تیکتی برای این کارتن ثبت نشده است",
                            description = "با عکاسی پشت‌سرهم یا انتخاب از گالری، عکس تیکت این کارتن را در پوشه اختصاصی خودش ذخیره کنید.",
                            icon = Icons.Filled.CameraAlt,
                        )
                    }
                } else {
                    val photoRows = cartonTickets.chunked(2)
                    items(photoRows.size, key = { idx -> "carton_photo_row_$idx" }) { rowIndex ->
                        val pair = photoRows[rowIndex]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEachIndexed { colIndex, photo ->
                                val absoluteIndex = rowIndex * 2 + colIndex
                                TicketPhotoTile(
                                    photo = photo,
                                    onClick = { onOpenPhotoViewer(absoluteIndex) },
                                    onEdit = { editingTicketPhoto = photo },
                                    onDelete = { deletingTicketPhoto = photo },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        CartonFormDialog(
            title = "ویرایش کارتن «$cartonName»",
            initialName = cartonName,
            initialItemCount = itemCount,
            confirmLabel = "ذخیره تغییرات",
            onDismiss = { showEditDialog = false },
            onConfirm = onUpdateCarton,
        )
    }

    editingTicketPhoto?.let { photo ->
        val info = cartonInfo
        if (info != null) {
            val palletOpt = palletOptions.find { it.palletId == info.palletId }
            val detail = TicketDetailItem(
                id = photo.id,
                palletId = photo.palletId,
                cartonId = photo.cartonId ?: info.cartonId,
                warehouseId = info.warehouseId,
                warehouseName = info.warehouseName,
                rowId = info.rowId,
                rowName = info.rowName,
                palletName = info.palletName,
                cartonName = info.cartonName,
                palletCartonCount = palletOpt?.cartonCount ?: 0,
                palletItemCount = palletOpt?.itemCount ?: 0,
                cartonItemCount = info.itemCount,
                mediaUri = photo.mediaUri,
                relativeFolderPath = photo.relativeFolderPath,
                fileName = photo.fileName,
                capturedAt = photo.capturedAt,
                jalaliDateTime = photo.jalaliDateTime,
                note = photo.note,
            )
            EditTicketDialog(
                ticket = detail,
                palletOptions = palletOptions,
                allCartons = allCartons,
                onDismiss = { editingTicketPhoto = null },
                onSave = { newPalletId, newCartonId, dateStr, timeStr, note, pCartons, pItems, cItems, cb ->
                    onUpdateTicket(
                        photo.id,
                        newPalletId,
                        newCartonId,
                        dateStr,
                        timeStr,
                        note,
                        pCartons,
                        pItems,
                        cItems,
                        cb,
                    )
                },
            )
        }
    }

    deletingTicketPhoto?.let { photo ->
        ConfirmDeleteDialog(
            title = "حذف عکس تیکت کارتن",
            message = "آیا از حذف کامل این تیکت و پاک‌شدن فایل عکس از حافظه گوشی مطمئن هستید؟",
            onDismiss = { deletingTicketPhoto = null },
            onConfirm = { onDeletePhoto(photo.id) },
        )
    }
}
