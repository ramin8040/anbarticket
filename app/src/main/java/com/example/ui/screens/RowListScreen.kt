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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ViewColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.RowSummary
import com.example.data.local.WarehouseEntity
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FolderPathBanner
import com.example.ui.components.HierarchyBreadcrumb
import com.example.ui.components.NameInputDialog
import com.example.ui.components.OverflowActionMenu
import com.example.ui.components.SearchBarField
import com.example.ui.components.StatBadge
import com.example.ui.components.ThemeToggleButton
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowListScreen(
    warehouse: WarehouseEntity?,
    rows: List<RowSummary>,
    isDarkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRow: (Long) -> Unit,
    onAddRow: (String, (String?) -> Unit) -> Unit,
    onRenameRow: (Long, String, (String?) -> Unit) -> Unit,
    onDeleteRow: (Long) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<RowSummary?>(null) }
    var deletingRow by remember { mutableStateOf<RowSummary?>(null) }

    val warehouseName = warehouse?.name ?: "انبار"
    val filteredRows = remember(rows, searchQuery) {
        if (searchQuery.isBlank()) rows
        else rows.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = warehouseName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "ردیف‌های انبار (${rows.size.toPersianNumber()} ردیف)",
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
                            .testTag("rows_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenMap,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rows_open_map_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = "نقشه ایزومتریک و دو بعدی سوله",
                            tint = MaterialTheme.colorScheme.secondary,
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
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("ردیف جدید") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("add_row_fab"),
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
                    HierarchyBreadcrumb(segments = listOf("انبارها", warehouseName))
                }

                item(key = "folder_path") {
                    FolderPathBanner(
                        folderPath = PersianUtils.buildWarehouseFolderPath(warehouseName),
                    )
                }

                item(key = "open_hall_map_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenMap)
                            .testTag("rows_open_hall_map_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Map,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "نقشه ایزومتریک و دو بعدی سوله",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = "مشاهده چیدمان واقعی ردیف‌ها و پالت‌های «$warehouseName»",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "مشاهده نقشه",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                if (rows.isNotEmpty()) {
                    item(key = "search_rows") {
                        SearchBarField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "جست‌وجوی ردیف…",
                            testTag = "search_rows_input",
                        )
                    }
                }

                if (rows.isEmpty()) {
                    item(key = "empty_rows") {
                        EmptyStatePanel(
                            title = "ردیفی در این انبار ثبت نشده است",
                            description = "ردیف‌های چیدمان «$warehouseName» را اضافه کنید تا پالت‌های هر ردیف در پوشه جداگانه تفکیک شوند.",
                            icon = Icons.Filled.ViewColumn,
                            actionLabel = "افزودن اولین ردیف",
                            onAction = { showAddDialog = true },
                            actionTestTag = "empty_add_row_button",
                        )
                    }
                } else if (filteredRows.isEmpty()) {
                    item(key = "no_row_results") {
                        EmptyStatePanel(
                            title = "ردیفی با این نام پیدا نشد",
                            description = "عبارت جست‌وجو را بررسی کنید.",
                            icon = Icons.Filled.ViewColumn,
                        )
                    }
                } else {
                    items(filteredRows, key = { it.id }) { row ->
                        RowSummaryCard(
                            row = row,
                            onClick = { onOpenRow(row.id) },
                            onEdit = { editingRow = row },
                            onDelete = { deletingRow = row },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        NameInputDialog(
            title = "افزودن ردیف جدید",
            label = "نام یا شماره ردیف",
            placeholder = "مثلاً: ردیف ۱ یا ردیف A",
            confirmLabel = "ثبت ردیف",
            onDismiss = { showAddDialog = false },
            onConfirm = onAddRow,
        )
    }

    editingRow?.let { target ->
        NameInputDialog(
            title = "ویرایش نام ردیف",
            label = "نام یا شماره ردیف",
            placeholder = "مثلاً: ردیف ۱",
            initialValue = target.name,
            confirmLabel = "ذخیره تغییرات",
            onDismiss = { editingRow = null },
            onConfirm = { newName, callback ->
                onRenameRow(target.id, newName, callback)
            },
        )
    }

    deletingRow?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف ردیف «${target.name}»",
            message = "با حذف این ردیف، تمام پالت‌ها، کارتن‌ها و عکس تیکت‌های این ردیف نیز حذف می‌شوند. ادامه می‌دهید؟",
            onDismiss = { deletingRow = null },
            onConfirm = { onDeleteRow(target.id) },
        )
    }
}

@Composable
private fun RowSummaryCard(
    row: RowSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("row_card_${row.id}"),
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
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ViewColumn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${row.palletCount.toPersianNumber()} پالت ثبت‌شده",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OverflowActionMenu(
                    onEdit = onEdit,
                    onDelete = onDelete,
                    testTagPrefix = "row_${row.id}",
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "ورود به ردیف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "کارتن",
                    value = row.totalCartons,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "اقلام",
                    value = row.totalItems,
                    icon = Icons.Filled.Layers,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "عکس",
                    value = row.photoCount,
                    icon = Icons.Filled.PhotoLibrary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
