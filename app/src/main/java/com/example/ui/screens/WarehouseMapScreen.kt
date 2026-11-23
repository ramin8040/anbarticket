package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CartonEntity
import com.example.data.local.PalletEntity
import com.example.data.local.RowReportItem
import com.example.data.local.TicketDetailItem
import com.example.data.local.WarehouseSummary
import com.example.ui.components.AppBottomNavigationBar
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.StatBadge
import com.example.ui.components.ThemeToggleButton
import com.example.ui.components.TopLevelTab
import com.example.ui.map.MapPalletNode
import com.example.ui.map.MapRowNode
import com.example.ui.map.MapSelection
import com.example.ui.map.MapViewMode
import com.example.ui.map.PalletMapStatus
import com.example.ui.map.WarehouseHallMapData
import com.example.ui.map.WarehouseMapBuilder
import com.example.ui.map.WarehouseMapCanvas
import com.example.util.PersianUtils.toPersianNumber
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseMapScreen(
    warehouses: List<WarehouseSummary>,
    rowReportItems: List<RowReportItem>,
    allPallets: List<PalletEntity>,
    allCartons: List<CartonEntity>,
    allTickets: List<TicketDetailItem>,
    initialWarehouseId: Long? = null,
    showBottomBar: Boolean = true,
    isDarkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    onToggleTheme: () -> Unit,
    onSelectTab: (TopLevelTab) -> Unit,
    onOpenWarehouseRows: (Long) -> Unit,
    onOpenRow: (Long) -> Unit,
    onOpenPallet: (Long) -> Unit,
    onQuickCameraPallet: (Long) -> Unit,
    onGoToWarehouses: () -> Unit,
) {
    val allMaps = remember(warehouses, rowReportItems, allPallets, allCartons, allTickets) {
        WarehouseMapBuilder.buildAllWarehousesMapData(
            warehouses = warehouses,
            rowReportItems = rowReportItems,
            allPallets = allPallets,
            allCartons = allCartons,
            allTickets = allTickets,
        )
    }

    var selectedWarehouseId by remember(initialWarehouseId) {
        mutableStateOf(initialWarehouseId)
    }
    var viewMode by remember { mutableStateOf(MapViewMode.ISOMETRIC) }
    var showLabels by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selection by remember { mutableStateOf<MapSelection?>(null) }

    // Ensure a valid warehouse is selected whenever warehouse list updates
    LaunchedEffect(allMaps, initialWarehouseId) {
        if (allMaps.isEmpty()) {
            selectedWarehouseId = null
            selection = null
        } else if (selectedWarehouseId == null || allMaps.none { it.warehouseId == selectedWarehouseId }) {
            selectedWarehouseId = initialWarehouseId?.takeIf { id -> allMaps.any { it.warehouseId == id } }
                ?: allMaps.first().warehouseId
        }
    }

    val activeHall: WarehouseHallMapData? = remember(allMaps, selectedWarehouseId) {
        allMaps.firstOrNull { it.warehouseId == selectedWarehouseId } ?: allMaps.firstOrNull()
    }

    // Keep current selection synced with latest live counts if data updates
    LaunchedEffect(activeHall) {
        selection = when (val cur = selection) {
            is MapSelection.Pallet -> activeHall?.rows
                ?.flatMap { it.pallets }
                ?.firstOrNull { it.id == cur.pallet.id }
                ?.let { MapSelection.Pallet(it) }
            is MapSelection.Row -> activeHall?.rows
                ?.firstOrNull { it.id == cur.row.id }
                ?.let { MapSelection.Row(it) }
            null -> null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "نقشه سوله انبار",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = activeHall?.let {
                                "${it.warehouseName}  •  ${it.rows.size.toPersianNumber()} ردیف، ${it.totalPallets.toPersianNumber()} پالت"
                            } ?: "نمای ایزومتریک و پلان دو بعدی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("map_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "بازگشت",
                            )
                        }
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
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavigationBar(
                    selectedTab = TopLevelTab.MAP,
                    onSelectTab = onSelectTab,
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
            if (activeHall == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyStatePanel(
                        title = "سوله یا انباری برای ترسیم نقشه وجود ندارد",
                        description = "ابتدا در بخش انبارها یک سوله/انبار به همراه ردیف‌ها و پالت‌ها ثبت کنید تا نقشه ایزومتریک و دو بعدی آن به‌صورت خودکار ترسیم شود.",
                        icon = Icons.Filled.Map,
                        actionLabel = "رفتن به لیست انبارها",
                        onAction = onGoToWarehouses,
                        actionTestTag = "map_empty_go_warehouses_button",
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 1. Warehouse / Hall selector chips (when 1+ warehouses exist)
                    if (allMaps.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            allMaps.forEach { hall ->
                                val isSelected = hall.warehouseId == activeHall.warehouseId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            selectedWarehouseId = hall.warehouseId
                                            selection = null
                                            zoom = 1f
                                            pan = Offset.Zero
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "${hall.warehouseName} (${hall.totalPallets.toPersianNumber()})",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Warehouse,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                    modifier = Modifier.testTag("map_warehouse_chip_${hall.warehouseId}"),
                                )
                            }
                        }
                    }

                    // 2. View Mode Switcher: Isometric 3D vs Top-Down 2D
                    MapViewModeSegmentedBar(
                        selectedMode = viewMode,
                        onSelectMode = { next ->
                            if (viewMode != next) {
                                viewMode = next
                                zoom = 1f
                                pan = Offset.Zero
                            }
                        },
                    )

                    // 3. Interactive Map Canvas Viewport
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = viewMode,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "map_view_mode_transition",
                            ) { currentMode ->
                                WarehouseMapCanvas(
                                    hallData = activeHall,
                                    viewMode = currentMode,
                                    isDarkTheme = isDarkTheme,
                                    showLabels = showLabels,
                                    zoom = zoom,
                                    pan = pan,
                                    selection = selection,
                                    onTransformChange = { newZoom, newPan ->
                                        zoom = newZoom
                                        pan = newPan
                                    },
                                    onSelect = { selection = it },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // Top-Start Projection & Zoom HUD Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = viewMode.badgeLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = "${(zoom * 100f).roundToInt().toPersianNumber()}٪",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            // Top-End Floating Map Controls (Zoom +, Zoom -, Reset, Labels)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MapOverlayControlButton(
                                    icon = Icons.Filled.Add,
                                    contentDescription = "بزرگ‌نمایی نقشه",
                                    testTag = "map_zoom_in_button",
                                    onClick = { zoom = (zoom * 1.25f).coerceAtMost(3.2f) },
                                )
                                MapOverlayControlButton(
                                    icon = Icons.Filled.Remove,
                                    contentDescription = "کوچک‌نمایی نقشه",
                                    testTag = "map_zoom_out_button",
                                    onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.55f) },
                                )
                                MapOverlayControlButton(
                                    icon = Icons.Filled.CenterFocusStrong,
                                    contentDescription = "بازنشانی نما و مرکز",
                                    testTag = "map_reset_view_button",
                                    onClick = {
                                        zoom = 1f
                                        pan = Offset.Zero
                                    },
                                )
                                MapOverlayControlButton(
                                    icon = if (showLabels) Icons.Filled.Label else Icons.Filled.LabelOff,
                                    contentDescription = if (showLabels) "مخفی کردن برچسب‌ها" else "نمایش برچسب‌ها",
                                    active = showLabels,
                                    testTag = "map_toggle_labels_button",
                                    onClick = { showLabels = !showLabels },
                                )
                            }

                            // Bottom-Start Compact Legend Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    LegendDotItem(color = Color(0xFF10B981), label = "تیکت‌دار")
                                    LegendDotItem(color = Color(0xFFF59E0B), label = "دارای شمارش")
                                    LegendDotItem(color = Color(0xFFC28E56), label = "پالت خالی")
                                }
                            }

                            // Empty Hall Floating Prompt (when warehouse exists but has 0 rows)
                            if (activeHall.rows.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    tonalElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(20.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "سوله «${activeHall.warehouseName}» هنوز خالی است",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "ردیف‌ها و پالت‌ها را ثبت کنید تا چیدمان واقعی روی نقشه ظاهر شود.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Button(
                                            onClick = { onOpenWarehouseRows(activeHall.warehouseId) },
                                            modifier = Modifier.testTag("map_add_rows_cta"),
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("مدیریت ردیف‌های این سوله")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Bottom Contextual Panel: Selected Pallet / Row Inspector OR Hall Summary Strip
                    AnimatedVisibility(
                        visible = selection != null,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut(),
                    ) {
                        when (val cur = selection) {
                            is MapSelection.Pallet -> SelectedPalletInspectorCard(
                                pallet = cur.pallet,
                                onDismiss = { selection = null },
                                onOpenPallet = { onOpenPallet(cur.pallet.id) },
                                onQuickCamera = { onQuickCameraPallet(cur.pallet.id) },
                            )
                            is MapSelection.Row -> SelectedRowInspectorCard(
                                row = cur.row,
                                onDismiss = { selection = null },
                                onOpenRow = { onOpenRow(cur.row.id) },
                            )
                            null -> Unit
                        }
                    }

                    if (selection == null) {
                        HallCompactSummaryBar(
                            hall = activeHall,
                            onOpenWarehouseRows = { onOpenWarehouseRows(activeHall.warehouseId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapViewModeSegmentedBar(
    selectedMode: MapViewMode,
    onSelectMode: (MapViewMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MapModeSegmentButton(
                label = MapViewMode.ISOMETRIC.label,
                icon = Icons.Filled.ViewInAr,
                selected = selectedMode == MapViewMode.ISOMETRIC,
                onClick = { onSelectMode(MapViewMode.ISOMETRIC) },
                testTag = "map_mode_isometric",
                modifier = Modifier.weight(1f),
            )
            MapModeSegmentButton(
                label = MapViewMode.TOP_DOWN_2D.label,
                icon = Icons.Filled.GridView,
                selected = selectedMode == MapViewMode.TOP_DOWN_2D,
                onClick = { onSelectMode(MapViewMode.TOP_DOWN_2D) },
                testTag = "map_mode_top_down",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MapModeSegmentButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MapOverlayControlButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .minimumInteractiveComponentSize()
                .testTag(testTag),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LegendDotItem(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HallCompactSummaryBar(
    hall: WarehouseHallMapData,
    onOpenWarehouseRows: () -> Unit,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "برای مشاهده جزئیات، روی هر پالت یا ردیف ضربه بزنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenWarehouseRows)
                        .testTag("map_open_warehouse_rows_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "ردیف‌ها",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = "باز کردن ردیف‌های سوله",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatBadge(
                    label = "ردیف",
                    value = hall.rows.size,
                    icon = Icons.Filled.ViewColumn,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "پالت",
                    value = hall.totalPallets,
                    icon = Icons.Filled.Inventory,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "کارتن",
                    value = hall.totalCartons,
                    icon = Icons.Filled.Inventory2,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "تیکت",
                    value = hall.totalTickets,
                    icon = Icons.Filled.PhotoLibrary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SelectedPalletInspectorCard(
    pallet: MapPalletNode,
    onDismiss: () -> Unit,
    onOpenPallet: () -> Unit,
    onQuickCamera: () -> Unit,
) {
    val statusBg = when (pallet.status) {
        PalletMapStatus.TICKETED -> MaterialTheme.colorScheme.tertiaryContainer
        PalletMapStatus.COUNTED_NO_TICKET -> MaterialTheme.colorScheme.secondaryContainer
        PalletMapStatus.EMPTY -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val statusFg = when (pallet.status) {
        PalletMapStatus.TICKETED -> MaterialTheme.colorScheme.onTertiaryContainer
        PalletMapStatus.COUNTED_NO_TICKET -> MaterialTheme.colorScheme.onSecondaryContainer
        PalletMapStatus.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("map_selected_pallet_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = pallet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusBg,
                        ) {
                            Text(
                                text = pallet.status.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${pallet.warehouseName} ← ${pallet.rowName} (جایگاه ${(pallet.slotIndex + 1).toPersianNumber()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("map_dismiss_selection"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "بستن پنل جزئیات",
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "کارتن",
                    value = pallet.effectiveCartonCount,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "اقلام",
                    value = pallet.effectiveItemCount,
                    icon = Icons.Filled.Layers,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "تیکت",
                    value = pallet.ticketCount,
                    icon = Icons.Filled.PhotoLibrary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenPallet,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("map_open_pallet_detail_button"),
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("جزئیات پالت")
                }
                Button(
                    onClick = onQuickCamera,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("map_quick_camera_pallet_button"),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("عکاسی تیکت")
                }
            }
        }
    }
}

@Composable
private fun SelectedRowInspectorCard(
    row: MapRowNode,
    onDismiss: () -> Unit,
    onOpenRow: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("map_selected_row_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${row.pallets.size.toPersianNumber()} پالت چیدمان‌شده در این ردیف",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "بستن")
                }
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
                    label = "تیکت",
                    value = row.totalTickets,
                    icon = Icons.Filled.PhotoLibrary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = onOpenRow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_open_row_pallets_button"),
            ) {
                Icon(Icons.Filled.ViewColumn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ورود به لیست پالت‌های این ردیف")
            }
        }
    }
}
