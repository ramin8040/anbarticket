package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PalletSummary
import com.example.data.local.RowPathInfo
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FolderPathBanner
import com.example.ui.components.HierarchyBreadcrumb
import com.example.ui.components.OverflowActionMenu
import com.example.ui.components.PalletFormDialog
import com.example.ui.components.SearchBarField
import com.example.ui.components.StatBadge
import com.example.ui.components.ThemeToggleButton
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalletListScreen(
    rowInfo: RowPathInfo?,
    pallets: List<PalletSummary>,
    isDarkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenPallet: (Long) -> Unit,
    onQuickCameraPallet: (Long) -> Unit,
    onAddPallet: (name: String, cartonCount: Int, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
    onUpdatePallet: (palletId: Long, name: String, cartonCount: Int, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
    onDeletePallet: (Long) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPallet by remember { mutableStateOf<PalletSummary?>(null) }
    var deletingPallet by remember { mutableStateOf<PalletSummary?>(null) }

    val warehouseName = rowInfo?.warehouseName ?: "انبار"
    val rowName = rowInfo?.rowName ?: "ردیف"

    val filteredPallets = remember(pallets, searchQuery) {
        if (searchQuery.isBlank()) pallets
        else pallets.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = rowName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "پالت‌های این ردیف (${pallets.size.toPersianNumber()} پالت)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pallets_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
                actions = {
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
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("پالت جدید") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("add_pallet_fab"),
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "breadcrumb") {
                    HierarchyBreadcrumb(segments = listOf(warehouseName, rowName))
                }

                item(key = "folder_path") {
                    FolderPathBanner(
                        folderPath = PersianUtils.buildRowFolderPath(warehouseName, rowName),
                    )
                }

                if (pallets.isNotEmpty()) {
                    item(key = "search_pallets") {
                        SearchBarField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "جست‌وجوی پالت…",
                            testTag = "search_pallets_input",
                        )
                    }
                }

                if (pallets.isEmpty()) {
                    item(key = "empty_pallets") {
                        EmptyStatePanel(
                            title = "پالتی در این ردیف ثبت نشده است",
                            description = "پالت جدید بسازید، تعداد کارتن و اقلام آن را وارد کنید و سپس عکس تیکت پالت و کارتن‌های روی آن را جداگانه ثبت کنید.",
                            icon = Icons.Filled.Inventory,
                            actionLabel = "ثبت اولین پالت",
                            onAction = { showAddDialog = true },
                            actionTestTag = "empty_add_pallet_button",
                        )
                    }
                } else if (filteredPallets.isEmpty()) {
                    item(key = "no_pallet_results") {
                        EmptyStatePanel(
                            title = "پالتی با این عبارت پیدا نشد",
                            description = "عبارت جست‌وجو را بررسی کنید.",
                            icon = Icons.Filled.Inventory,
                        )
                    }
                } else {
                    items(filteredPallets, key = { it.id }) { pallet ->
                        PalletSummaryCard(
                            pallet = pallet,
                            onClick = { onOpenPallet(pallet.id) },
                            onQuickCamera = { onQuickCameraPallet(pallet.id) },
                            onEdit = { editingPallet = pallet },
                            onDelete = { deletingPallet = pallet },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PalletFormDialog(
            title = "ثبت پالت جدید",
            confirmLabel = "ثبت پالت",
            onDismiss = { showAddDialog = false },
            onConfirm = onAddPallet,
        )
    }

    editingPallet?.let { target ->
        PalletFormDialog(
            title = "ویرایش پالت «${target.name}»",
            initialName = target.name,
            initialCartonCount = target.cartonCount,
            initialItemCount = target.itemCount,
            confirmLabel = "ذخیره تغییرات",
            onDismiss = { editingPallet = null },
            onConfirm = { newName, newCartons, newItems, callback ->
                onUpdatePallet(target.id, newName, newCartons, newItems, callback)
            },
        )
    }

    deletingPallet?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف پالت «${target.name}»",
            message = "با حذف این پالت، تمام کارتن‌های زیرمجموعه و عکس تیکت‌های آن حذف می‌شوند. ادامه می‌دهید؟",
            onDismiss = { deletingPallet = null },
            onConfirm = { onDeletePallet(target.id) },
        )
    }
}

@Composable
private fun PalletSummaryCard(
    pallet: PalletSummary,
    onClick: () -> Unit,
    onQuickCamera: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val displayedCartons = maxOf(pallet.cartonCount, pallet.registeredCartonCount)
    val displayedItems = maxOf(pallet.itemCount, pallet.cartonsTotalItems)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("pallet_card_${pallet.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Inventory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pallet.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${pallet.registeredCartonCount.toPersianNumber()} پوشه کارتن  •  ${pallet.palletPhotoCount.toPersianNumber()} تیکت پالت",
                        style = MaterialTheme.typography.bodySmall,
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
                        .testTag("pallet_quick_camera_${pallet.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "عکاسی سریع از تیکت پالت ${pallet.name}",
                    )
                }

                OverflowActionMenu(
                    onEdit = onEdit,
                    onDelete = onDelete,
                    testTagPrefix = "pallet_${pallet.id}",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "کارتن",
                    value = displayedCartons,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "اقلام",
                    value = displayedItems,
                    icon = Icons.Filled.Layers,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "کل عکس",
                    value = pallet.totalPhotoCount,
                    icon = Icons.Filled.PhotoLibrary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
