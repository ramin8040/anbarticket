package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CloudBackupPreferences
import com.example.data.local.GlobalStats
import com.example.data.local.LastBackupStatus
import com.example.data.local.WarehouseSummary
import com.example.ui.components.AppBottomNavigationBar
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FolderPathBanner
import com.example.ui.components.NameInputDialog
import com.example.ui.components.OverflowActionMenu
import com.example.ui.components.SearchBarField
import com.example.ui.components.StatBadge
import com.example.ui.components.ThemeToggleButton
import com.example.ui.components.TopLevelTab
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseListScreen(
    warehouses: List<WarehouseSummary>,
    globalStats: GlobalStats,
    isCloudConfigured: Boolean,
    cloudBackupPrefs: CloudBackupPreferences,
    syncedPhotoCount: Int,
    isCloudSyncRunning: Boolean,
    isDarkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleTheme: () -> Unit,
    onOpenCloudBackup: () -> Unit,
    onSelectTab: (TopLevelTab) -> Unit,
    onOpenWarehouse: (Long) -> Unit,
    onAddWarehouse: (String, (String?) -> Unit) -> Unit,
    onRenameWarehouse: (Long, String, (String?) -> Unit) -> Unit,
    onDeleteWarehouse: (Long) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWarehouse by remember { mutableStateOf<WarehouseSummary?>(null) }
    var deletingWarehouse by remember { mutableStateOf<WarehouseSummary?>(null) }

    val filteredWarehouses = remember(warehouses, searchQuery) {
        if (searchQuery.isBlank()) warehouses
        else warehouses.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "تیکت‌شمار انبار",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "انبار ← ردیف ← پالت ← کارتن‌ها",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    val pendingCount = (globalStats.totalPhotos - syncedPhotoCount).coerceAtLeast(0)
                    val cloudIcon = when {
                        !isCloudConfigured || cloudBackupPrefs.lastBackupStatus == LastBackupStatus.ERROR -> Icons.Filled.CloudOff
                        isCloudSyncRunning || pendingCount > 0 -> Icons.Filled.CloudSync
                        else -> Icons.Filled.CloudDone
                    }
                    val cloudTint = when {
                        !isCloudConfigured -> MaterialTheme.colorScheme.secondary
                        cloudBackupPrefs.lastBackupStatus == LastBackupStatus.ERROR -> MaterialTheme.colorScheme.error
                        isCloudSyncRunning || pendingCount > 0 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    IconButton(
                        onClick = onOpenCloudBackup,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("warehouses_cloud_backup_button"),
                    ) {
                        Icon(
                            imageVector = cloudIcon,
                            contentDescription = "پشتیبان‌گیری ابری (ابر آروان)",
                            tint = cloudTint,
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
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = TopLevelTab.WAREHOUSES,
                onSelectTab = onSelectTab,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("انبار جدید") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("add_warehouse_fab"),
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
                item(key = "hero_stats") {
                    GlobalStatsHeroCard(
                        stats = globalStats,
                        isDarkTheme = isDarkTheme,
                    )
                }

                item(key = "root_folder_path") {
                    FolderPathBanner(
                        folderPath = "حافظه داخلی/${PersianUtils.ROOT_FOLDER_NAME}",
                    )
                }

                item(key = "cloud_backup_status_banner") {
                    val pendingCount = (globalStats.totalPhotos - syncedPhotoCount).coerceAtLeast(0)
                    val bannerSubtitle = when {
                        isCloudSyncRunning -> "در حال پردازش بکاپ یا همگام‌سازی…"
                        !isCloudConfigured -> "گرفتن بکاپ جدید (ZIP با عکس‌ها)، بازیابی از حافظه، اشتراک‌گذاری و ابر آروان"
                        cloudBackupPrefs.lastBackupStatus == LastBackupStatus.ERROR -> "خطا در آخرین پشتیبان‌گیری ابری — برای مدیریت بکاپ‌ها لمس کنید"
                        pendingCount > 0 -> "بکاپ فایلی ZIP + ${pendingCount.toPersianNumber()} تصویر در انتظار ارسال به ابر"
                        cloudBackupPrefs.lastBackupAtMillis > 0L -> "آخرین پشتیبان ابری: ${PersianUtils.formatJalaliDateTime(cloudBackupPrefs.lastBackupAtMillis)}"
                        else -> "گرفتن بکاپ فایلی ZIP همراه با عکس‌ها، بازیابی و پشتیبان‌گیری ابری"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenCloudBackup)
                            .testTag("warehouses_cloud_backup_banner"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCloudConfigured && pendingCount == 0 && cloudBackupPrefs.lastBackupAtMillis > 0L) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isCloudConfigured && pendingCount == 0 && cloudBackupPrefs.lastBackupAtMillis > 0L) {
                                            Icons.Filled.CloudDone
                                        } else {
                                            Icons.Filled.CloudSync
                                        },
                                        contentDescription = null,
                                        tint = if (isCloudConfigured && pendingCount == 0 && cloudBackupPrefs.lastBackupAtMillis > 0L) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "مدیریت بکاپ‌ها و بازیابی (فایلی ZIP + ابری)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = bannerSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "باز کردن صفحه مدیریت بکاپ‌ها",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (warehouses.isNotEmpty()) {
                    item(key = "search_warehouses") {
                        SearchBarField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "جست‌وجوی انبار…",
                            testTag = "search_warehouses_input",
                        )
                    }
                }

                if (warehouses.isEmpty()) {
                    item(key = "empty_warehouses") {
                        EmptyStatePanel(
                            title = "هنوز انباری ثبت نشده است",
                            description = "اولین انبار را بسازید تا ردیف‌ها، پالت‌ها، کارتن‌ها و عکس تیکت‌ها به صورت خودکار در پوشه‌های واقعی روی حافظه گوشی تفکیک شوند.",
                            illustrationResId = R.drawable.img_warehouse_empty_1795384517704,
                            actionLabel = "ایجاد اولین انبار",
                            onAction = { showAddDialog = true },
                            actionTestTag = "empty_add_warehouse_button",
                        )
                    }
                } else if (filteredWarehouses.isEmpty()) {
                    item(key = "no_search_results") {
                        EmptyStatePanel(
                            title = "انباری با این عبارت یافت نشد",
                            description = "عبارت جست‌وجو را بررسی یا پاک کنید.",
                            icon = Icons.Filled.Warehouse,
                        )
                    }
                } else {
                    items(filteredWarehouses, key = { it.id }) { warehouse ->
                        WarehouseCard(
                            warehouse = warehouse,
                            onClick = { onOpenWarehouse(warehouse.id) },
                            onEdit = { editingWarehouse = warehouse },
                            onDelete = { deletingWarehouse = warehouse },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        NameInputDialog(
            title = "ایجاد انبار جدید",
            label = "نام یا کد انبار",
            placeholder = "مثلاً: انبار مرکزی یا سالن ۱",
            confirmLabel = "ایجاد انبار",
            onDismiss = { showAddDialog = false },
            onConfirm = onAddWarehouse,
        )
    }

    editingWarehouse?.let { target ->
        NameInputDialog(
            title = "ویرایش نام انبار",
            label = "نام یا کد انبار",
            placeholder = "مثلاً: انبار مرکزی",
            initialValue = target.name,
            confirmLabel = "ذخیره تغییرات",
            onDismiss = { editingWarehouse = null },
            onConfirm = { newName, callback ->
                onRenameWarehouse(target.id, newName, callback)
            },
        )
    }

    deletingWarehouse?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف انبار «${target.name}»",
            message = "با حذف این انبار، تمام ردیف‌ها، پالت‌ها، کارتن‌ها و عکس تیکت‌های ثبت‌شده آن نیز از حافظه پاک می‌شوند. ادامه می‌دهید؟",
            onDismiss = { deletingWarehouse = null },
            onConfirm = { onDeleteWarehouse(target.id) },
        )
    }
}

@Composable
private fun GlobalStatsHeroCard(
    stats: GlobalStats,
    isDarkTheme: Boolean,
) {
    val gradientColors = if (isDarkTheme) {
        listOf(Color(0xFF1E293B), Color(0xFF151E2B))
    } else {
        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(gradientColors))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "گزارش کلی انبارگردانی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "آرشیو آفلاین با پوشه‌بندی واقعی و واترمارک شمسی",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33F59E0B),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${stats.totalPhotos.toPersianNumber()} تیکت",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE68A),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroMetricTile(
                    label = "انبار",
                    value = stats.warehouseCount.toPersianNumber(),
                    modifier = Modifier.weight(1f),
                )
                HeroMetricTile(
                    label = "پالت",
                    value = stats.palletCount.toPersianNumber(),
                    modifier = Modifier.weight(1f),
                )
                HeroMetricTile(
                    label = "کارتن",
                    value = stats.totalCartons.toPersianNumber(),
                    modifier = Modifier.weight(1f),
                )
                HeroMetricTile(
                    label = "اقلام",
                    value = stats.totalItems.toPersianNumber(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0x1FFFFFFF),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WarehouseCard(
    warehouse: WarehouseSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("warehouse_card_${warehouse.id}"),
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Warehouse,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = warehouse.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${warehouse.rowCount.toPersianNumber()} ردیف  •  ${warehouse.palletCount.toPersianNumber()} پالت",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OverflowActionMenu(
                    onEdit = onEdit,
                    onDelete = onDelete,
                    testTagPrefix = "warehouse_${warehouse.id}",
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "ورود به انبار",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "کارتن",
                    value = warehouse.totalCartons,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "اقلام",
                    value = warehouse.totalItems,
                    icon = Icons.Filled.Layers,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "عکس",
                    value = warehouse.photoCount,
                    icon = Icons.Filled.PhotoLibrary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
