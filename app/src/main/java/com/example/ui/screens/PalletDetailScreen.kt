package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CartonEntity
import com.example.data.local.CartonSummary
import com.example.data.local.PalletOptionItem
import com.example.data.local.PalletPathInfo
import com.example.data.local.PalletSummary
import com.example.data.local.TicketDetailItem
import com.example.data.local.TicketPhotoEntity
import com.example.ui.components.CartonFormDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTicketDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FolderPathBanner
import com.example.ui.components.HierarchyBreadcrumb
import com.example.ui.components.OverflowActionMenu
import com.example.ui.components.PalletFormDialog
import com.example.ui.components.StatBadge
import com.example.ui.components.ThemeToggleButton
import com.example.ui.components.TicketPhotoTile
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalletDetailScreen(
    palletInfo: PalletPathInfo?,
    palletSummary: PalletSummary?,
    palletTickets: List<TicketPhotoEntity>,
    cartons: List<CartonSummary>,
    palletOptions: List<PalletOptionItem>,
    allCartons: List<CartonEntity>,
    isDarkTheme: Boolean,
    watermarkEnabled: Boolean,
    isImportingGallery: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleWatermark: () -> Unit,
    onOpenCameraForPallet: () -> Unit,
    onImportGalleryForPallet: (List<Uri>) -> Unit,
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
    onUpdatePallet: (name: String, cartonCount: Int, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
    onOpenCarton: (Long) -> Unit,
    onQuickCameraForCarton: (Long) -> Unit,
    onAddCarton: (name: String, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
    onUpdateCarton: (cartonId: Long, name: String, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
    onDeleteCarton: (Long) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = تیکت‌های پالت, 1 = کارتن‌های روی پالت
    var showEditPalletDialog by remember { mutableStateOf(false) }
    var showAddCartonDialog by remember { mutableStateOf(false) }
    var editingCarton by remember { mutableStateOf<CartonSummary?>(null) }
    var deletingCarton by remember { mutableStateOf<CartonSummary?>(null) }
    var editingTicketPhoto by remember { mutableStateOf<TicketPhotoEntity?>(null) }
    var deletingTicketPhoto by remember { mutableStateOf<TicketPhotoEntity?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onImportGalleryForPallet(uris)
            }
        },
    )

    val warehouseName = palletInfo?.warehouseName ?: "انبار"
    val rowName = palletInfo?.rowName ?: "ردیف"
    val palletName = palletInfo?.palletName ?: palletSummary?.name ?: "پالت"
    val cartonCount = palletSummary?.cartonCount ?: palletInfo?.cartonCount ?: 0
    val itemCount = palletSummary?.itemCount ?: palletInfo?.itemCount ?: 0
    val cartonsTotalItems = palletSummary?.cartonsTotalItems ?: 0

    val targetFolderPath = if (selectedTab == 0) {
        PersianUtils.buildTargetTicketFolderPath(warehouseName, rowName, palletName, cartonName = null)
    } else {
        "${PersianUtils.buildPalletRootFolderPath(warehouseName, rowName, palletName)}/${PersianUtils.CARTONS_PARENT_SUBFOLDER}"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = palletName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$warehouseName  •  $rowName",
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
                            .testTag("pallet_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditPalletDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_pallet_counts_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "ویرایش پالت و تعدادها",
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
        floatingActionButton = {
            if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showAddCartonDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("کارتن جدید") },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("add_carton_fab"),
                )
            }
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "breadcrumb") {
                    HierarchyBreadcrumb(segments = listOf(warehouseName, rowName, palletName))
                }

                item(key = "pallet_counts_card") {
                    PalletCountsSummaryCard(
                        cartonCount = cartonCount,
                        registeredCartonFolders = cartons.size,
                        itemCount = itemCount,
                        cartonsTotalItems = cartonsTotalItems,
                        onEditClick = { showEditPalletDialog = true },
                    )
                }

                item(key = "tab_switcher") {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("tab_pallet_tickets"),
                        ) {
                            Text(
                                text = "تیکت‌های پالت (${palletTickets.size.toPersianNumber()})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("tab_pallet_cartons"),
                        ) {
                            Text(
                                text = "کارتن‌های روی پالت (${cartons.size.toPersianNumber()})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                item(key = "folder_path") {
                    FolderPathBanner(folderPath = targetFolderPath)
                }

                if (selectedTab == 0) {
                    item(key = "pallet_photo_actions") {
                        TicketCaptureActionBar(
                            watermarkEnabled = watermarkEnabled,
                            isImportingGallery = isImportingGallery,
                            onToggleWatermark = onToggleWatermark,
                            onOpenCamera = onOpenCameraForPallet,
                            onPickGallery = {
                                galleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            testTagPrefix = "pallet",
                        )
                    }

                    if (palletTickets.isEmpty()) {
                        item(key = "empty_pallet_tickets") {
                            EmptyStatePanel(
                                title = "عکس تیکتی برای این پالت ثبت نشده است",
                                description = "دکمه «عکاسی پشت‌سرهم» یا «انتخاب از گالری» را بزنید تا عکس تیکت با واترمارک شمسی در پوشه «تیکت_پالت» ذخیره شود.",
                                icon = Icons.Filled.CameraAlt,
                            )
                        }
                    } else {
                        val photoRows = palletTickets.chunked(2)
                        items(photoRows.size, key = { idx -> "pallet_photo_row_$idx" }) { rowIndex ->
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
                } else {
                    if (cartons.isEmpty()) {
                        item(key = "empty_cartons") {
                            EmptyStatePanel(
                                title = "کارتنی روی این پالت ثبت نشده است",
                                description = "برای تفکیک عکس تیکت هر کارتن در پوشه اختصاصی، کارتن‌های موجود روی این پالت را اضافه کنید.",
                                icon = Icons.Filled.Inventory2,
                                actionLabel = "افزودن اولین کارتن",
                                onAction = { showAddCartonDialog = true },
                                actionTestTag = "empty_add_carton_button",
                            )
                        }
                    } else {
                        items(cartons, key = { it.id }) { carton ->
                            CartonFolderCard(
                                carton = carton,
                                onClick = { onOpenCarton(carton.id) },
                                onQuickCamera = { onQuickCameraForCarton(carton.id) },
                                onEdit = { editingCarton = carton },
                                onDelete = { deletingCarton = carton },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditPalletDialog) {
        PalletFormDialog(
            title = "ویرایش پالت «$palletName»",
            initialName = palletName,
            initialCartonCount = cartonCount,
            initialItemCount = itemCount,
            confirmLabel = "ذخیره تغییرات",
            onDismiss = { showEditPalletDialog = false },
            onConfirm = onUpdatePallet,
        )
    }

    if (showAddCartonDialog) {
        CartonFormDialog(
            title = "افزودن کارتن روی پالت",
            confirmLabel = "ثبت کارتن",
            onDismiss = { showAddCartonDialog = false },
            onConfirm = onAddCarton,
        )
    }

    editingCarton?.let { target ->
        CartonFormDialog(
            title = "ویرایش کارتن «${target.name}»",
            initialName = target.name,
            initialItemCount = target.itemCount,
            confirmLabel = "ذخیره تغییرات",
            onDismiss = { editingCarton = null },
            onConfirm = { newName, newItems, callback ->
                onUpdateCarton(target.id, newName, newItems, callback)
            },
        )
    }

    deletingCarton?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف کارتن «${target.name}»",
            message = "با حذف این کارتن، تمام عکس تیکت‌های ثبت‌شده در پوشه آن نیز حذف می‌شوند. ادامه می‌دهید؟",
            onDismiss = { deletingCarton = null },
            onConfirm = { onDeleteCarton(target.id) },
        )
    }

    editingTicketPhoto?.let { photo ->
        val info = palletInfo
        if (info != null) {
            val detail = TicketDetailItem(
                id = photo.id,
                palletId = photo.palletId,
                cartonId = photo.cartonId,
                warehouseId = info.warehouseId,
                warehouseName = info.warehouseName,
                rowId = info.rowId,
                rowName = info.rowName,
                palletName = info.palletName,
                cartonName = null,
                palletCartonCount = cartonCount,
                palletItemCount = itemCount,
                cartonItemCount = null,
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
            title = "حذف عکس تیکت پالت",
            message = "آیا از حذف کامل این تیکت و پاک‌شدن فایل عکس از حافظه گوشی مطمئن هستید؟",
            onDismiss = { deletingTicketPhoto = null },
            onConfirm = { onDeletePhoto(photo.id) },
        )
    }
}

@Composable
private fun PalletCountsSummaryCard(
    cartonCount: Int,
    registeredCartonFolders: Int,
    itemCount: Int,
    cartonsTotalItems: Int,
    onEditClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "آمار شمارش این پالت",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("inline_edit_pallet_counts"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ویرایش تعدادها", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "تعداد کارتن ثبت‌شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${cartonCount.toPersianNumber()} کارتن",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "پوشه کارتن ساخته‌شده: ${registeredCartonFolders.toPersianNumber()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "تعداد اقلام شمارش‌شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "${itemCount.toPersianNumber()} قلم",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "جمع اقلام کارتن‌ها: ${cartonsTotalItems.toPersianNumber()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TicketCaptureActionBar(
    watermarkEnabled: Boolean,
    isImportingGallery: Boolean,
    onToggleWatermark: () -> Unit,
    onOpenCamera: () -> Unit,
    onPickGallery: () -> Unit,
    testTagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpenCamera,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 14.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_rapid_camera_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "عکاسی پشت‌سرهم",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }

            OutlinedButton(
                onClick = onPickGallery,
                enabled = !isImportingGallery,
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_gallery_picker_button"),
            ) {
                if (isImportingGallery) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "از گالری",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilterChip(
                selected = watermarkEnabled,
                onClick = onToggleWatermark,
                label = {
                    Text(
                        text = if (watermarkEnabled) {
                            "واترمارک انبار، ردیف، پالت و تاریخ شمسی فعال است"
                        } else {
                            "واترمارک روی عکس غیرفعال است"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.BrandingWatermark,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.testTag("${testTagPrefix}_watermark_toggle"),
            )
        }
    }
}

@Composable
private fun CartonFolderCard(
    carton: CartonSummary,
    onClick: () -> Unit,
    onQuickCamera: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("carton_card_${carton.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = carton.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "پوشه اختصاصی کارتن",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalIconButton(
                    onClick = onQuickCamera,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("carton_quick_camera_${carton.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "عکاسی سریع از تیکت کارتن ${carton.name}",
                    )
                }

                OverflowActionMenu(
                    onEdit = onEdit,
                    onDelete = onDelete,
                    testTagPrefix = "carton_${carton.id}",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "اقلام شمارش‌شده",
                    value = carton.itemCount,
                    icon = Icons.Filled.Layers,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "عکس تیکت",
                    value = carton.photoCount,
                    icon = Icons.Filled.PhotoLibrary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
