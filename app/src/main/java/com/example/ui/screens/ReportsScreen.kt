package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
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
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber
import kotlinx.coroutines.launch

private enum class ReportPeriod(val label: String, val daysBack: Int?) {
    DAILY("روزانه (امروز)", 0),
    WEEKLY("هفتگی (۷ روز)", 6),
    MONTHLY("ماهانه (۳۰ روز)", 29),
    ALL("کل دوره", null),
}

private data class DailyBreakdownRow(
    val jalaliDate: String,
    val sortKey: Long,
    val palletCount: Int,
    val cartonCount: Int,
    val itemCount: Int,
    val palletTickets: Int,
    val cartonTickets: Int,
) {
    val totalTickets: Int get() = palletTickets + cartonTickets
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    warehouses: List<WarehouseSummary>,
    rowReportItems: List<RowReportItem>,
    allPallets: List<PalletEntity>,
    allCartons: List<CartonEntity>,
    allTickets: List<TicketDetailItem>,
    isDarkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleTheme: () -> Unit,
    onSelectTab: (TopLevelTab) -> Unit,
    onOpenRow: (Long) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var reportMode by rememberSaveable { mutableIntStateOf(0) } // 0 = زمانی, 1 = مکانی (انبار/ردیف)
    var periodName by rememberSaveable { mutableStateOf(ReportPeriod.WEEKLY.name) }
    var selectedWarehouseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRowId by rememberSaveable { mutableStateOf<Long?>(null) }

    val period = remember(periodName) { ReportPeriod.valueOf(periodName) }

    // --- Time-Range Report Calculations ---
    val timeCutoff = remember(period) { period.daysBack?.let { PersianUtils.startOfDaysAgoMillis(it) } }
    val todayJalali = remember { PersianUtils.formatJalaliDateOnly(System.currentTimeMillis()) }
    val periodRangeSubtitle = remember(period, timeCutoff) {
        when (period) {
            ReportPeriod.DAILY -> "تاریخ: $todayJalali"
            ReportPeriod.WEEKLY, ReportPeriod.MONTHLY -> {
                val startStr = PersianUtils.formatJalaliDateOnly(timeCutoff ?: System.currentTimeMillis())
                "بازه زمانی: $startStr تا $todayJalali"
            }
            ReportPeriod.ALL -> "تمامی اطلاعات ثبت‌شده تا $todayJalali"
        }
    }

    val cartonsByPallet = remember(allCartons) { allCartons.groupBy { it.palletId } }

    val palletsInPeriod = remember(allPallets, timeCutoff) {
        if (timeCutoff == null) allPallets else allPallets.filter { it.createdAt >= timeCutoff }
    }
    val cartonsInPeriod = remember(allCartons, timeCutoff) {
        if (timeCutoff == null) allCartons else allCartons.filter { it.createdAt >= timeCutoff }
    }
    val ticketsInPeriod = remember(allTickets, timeCutoff) {
        if (timeCutoff == null) allTickets else allTickets.filter { it.capturedAt >= timeCutoff }
    }

    val timeTotalPallets = palletsInPeriod.size
    val timeTotalCartons = remember(palletsInPeriod, cartonsInPeriod, cartonsByPallet) {
        val fromPallets = palletsInPeriod.sumOf { pallet ->
            val registered = cartonsByPallet[pallet.id]?.size ?: 0
            maxOf(pallet.cartonCount, registered)
        }
        val orphanCartons = cartonsInPeriod.count { carton -> palletsInPeriod.none { it.id == carton.palletId } }
        fromPallets + orphanCartons
    }
    val timeTotalItems = remember(palletsInPeriod, cartonsInPeriod, cartonsByPallet) {
        val fromPallets = palletsInPeriod.sumOf { pallet ->
            val cartonsSum = cartonsByPallet[pallet.id]?.sumOf { it.itemCount } ?: 0
            maxOf(pallet.itemCount, cartonsSum)
        }
        val extraCartonItems = cartonsInPeriod
            .filter { carton -> palletsInPeriod.none { it.id == carton.palletId } }
            .sumOf { it.itemCount }
        fromPallets + extraCartonItems
    }
    val timePalletTickets = remember(ticketsInPeriod) { ticketsInPeriod.count { !it.isCartonTicket } }
    val timeCartonTickets = remember(ticketsInPeriod) { ticketsInPeriod.count { it.isCartonTicket } }

    val dailyBreakdown = remember(palletsInPeriod, cartonsInPeriod, ticketsInPeriod, cartonsByPallet) {
        val dateKeys = linkedMapOf<String, Long>()
        palletsInPeriod.forEach {
            val d = PersianUtils.formatJalaliDateOnly(it.createdAt)
            dateKeys[d] = maxOf(dateKeys[d] ?: 0L, it.createdAt)
        }
        cartonsInPeriod.forEach {
            val d = PersianUtils.formatJalaliDateOnly(it.createdAt)
            dateKeys[d] = maxOf(dateKeys[d] ?: 0L, it.createdAt)
        }
        ticketsInPeriod.forEach {
            val d = PersianUtils.splitJalaliDateTime(it.jalaliDateTime).first.ifBlank {
                PersianUtils.formatJalaliDateOnly(it.capturedAt)
            }
            dateKeys[d] = maxOf(dateKeys[d] ?: 0L, it.capturedAt)
        }

        dateKeys.entries.map { (dateStr, sortTs) ->
            val dayPallets = palletsInPeriod.filter { PersianUtils.formatJalaliDateOnly(it.createdAt) == dateStr }
            val dayCartons = cartonsInPeriod.filter { PersianUtils.formatJalaliDateOnly(it.createdAt) == dateStr }
            val dayTickets = ticketsInPeriod.filter {
                val td = PersianUtils.splitJalaliDateTime(it.jalaliDateTime).first.ifBlank {
                    PersianUtils.formatJalaliDateOnly(it.capturedAt)
                }
                td == dateStr
            }

            val dayCartonTotal = dayPallets.sumOf { pallet ->
                val reg = cartonsByPallet[pallet.id]?.size ?: 0
                maxOf(pallet.cartonCount, reg)
            } + dayCartons.count { c -> dayPallets.none { it.id == c.palletId } }

            val dayItemTotal = dayPallets.sumOf { pallet ->
                val cSum = cartonsByPallet[pallet.id]?.sumOf { it.itemCount } ?: 0
                maxOf(pallet.itemCount, cSum)
            } + dayCartons.filter { c -> dayPallets.none { it.id == c.palletId } }.sumOf { it.itemCount }

            DailyBreakdownRow(
                jalaliDate = dateStr,
                sortKey = sortTs,
                palletCount = dayPallets.size,
                cartonCount = dayCartonTotal,
                itemCount = dayItemTotal,
                palletTickets = dayTickets.count { !it.isCartonTicket },
                cartonTickets = dayTickets.count { it.isCartonTicket },
            )
        }.sortedByDescending { it.sortKey }
    }

    // --- Location-Based Report Calculations ---
    val rowsForSelectedWarehouse = remember(rowReportItems, selectedWarehouseId) {
        if (selectedWarehouseId == null) rowReportItems
        else rowReportItems.filter { it.warehouseId == selectedWarehouseId }
    }
    val filteredRowReports = remember(rowsForSelectedWarehouse, selectedRowId) {
        if (selectedRowId == null) rowsForSelectedWarehouse
        else rowsForSelectedWarehouse.filter { it.rowId == selectedRowId }
    }

    val locTotalPallets = remember(filteredRowReports) { filteredRowReports.sumOf { it.palletCount } }
    val locTotalCartons = remember(filteredRowReports) { filteredRowReports.sumOf { it.totalCartons } }
    val locTotalItems = remember(filteredRowReports) { filteredRowReports.sumOf { it.totalItems } }
    val locPalletTickets = remember(filteredRowReports) { filteredRowReports.sumOf { it.palletTicketCount } }
    val locCartonTickets = remember(filteredRowReports) { filteredRowReports.sumOf { it.cartonTicketCount } }

    fun buildReadableReportText(): String {
        return if (reportMode == 0) {
            buildString {
                appendLine("گزارش شمارش انبار (${period.label})")
                appendLine(periodRangeSubtitle)
                appendLine("────────────────────────")
                appendLine("• تعداد کل پالت‌ها: ${timeTotalPallets.toPersianNumber()}")
                appendLine("• تعداد کل کارتن‌ها: ${timeTotalCartons.toPersianNumber()}")
                appendLine("• مجموع اقلام شمارش‌شده: ${timeTotalItems.toPersianNumber()}")
                appendLine("• کل عکس تیکت‌ها: ${(timePalletTickets + timeCartonTickets).toPersianNumber()} (پالت: ${timePalletTickets.toPersianNumber()} | کارتن: ${timeCartonTickets.toPersianNumber()})")
                if (dailyBreakdown.isNotEmpty()) {
                    appendLine()
                    appendLine("تفکیک روزانه:")
                    dailyBreakdown.forEach { day ->
                        appendLine("📅 ${day.jalaliDate}: ${day.palletCount.toPersianNumber()} پالت | ${day.cartonCount.toPersianNumber()} کارتن | ${day.itemCount.toPersianNumber()} قلم | ${day.totalTickets.toPersianNumber()} تیکت")
                    }
                }
            }
        } else {
            val scopeLabel = buildString {
                val whName = warehouses.find { it.id == selectedWarehouseId }?.name ?: "همه انبارها"
                val rowName = rowsForSelectedWarehouse.find { it.rowId == selectedRowId }?.rowName
                append(whName)
                if (rowName != null) append(" ← $rowName")
            }
            buildString {
                appendLine("گزارش مکانی شمارش انبار")
                appendLine("محدوده: $scopeLabel")
                appendLine("تاریخ تهیه گزارش: $todayJalali")
                appendLine("────────────────────────")
                appendLine("• تعداد ردیف‌ها: ${filteredRowReports.size.toPersianNumber()}")
                appendLine("• تعداد کل پالت‌ها: ${locTotalPallets.toPersianNumber()}")
                appendLine("• تعداد کل کارتن‌ها: ${locTotalCartons.toPersianNumber()}")
                appendLine("• مجموع اقلام شمارش‌شده: ${locTotalItems.toPersianNumber()}")
                appendLine("• کل تیکت‌ها: ${(locPalletTickets + locCartonTickets).toPersianNumber()} (پالت: ${locPalletTickets.toPersianNumber()} | کارتن: ${locCartonTickets.toPersianNumber()})")
                if (filteredRowReports.isNotEmpty()) {
                    appendLine()
                    appendLine("جزئیات هر ردیف:")
                    filteredRowReports.forEach { row ->
                        appendLine("▪ ${row.warehouseName} / ${row.rowName}: ${row.palletCount.toPersianNumber()} پالت | ${row.totalCartons.toPersianNumber()} کارتن | ${row.totalItems.toPersianNumber()} قلم | ${row.totalTicketCount.toPersianNumber()} تیکت")
                    }
                }
            }
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
                            text = "گزارش‌گیری شمارش",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "خلاصه آماری بر اساس بازه زمانی یا مکان انبار",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(buildReadableReportText()))
                            scope.launch {
                                snackbarHostState.showSnackbar("متن گزارش در حافظه کپی شد.")
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_report_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "کپی متن گزارش",
                        )
                    }
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "گزارش شمارش انبار")
                                putExtra(Intent.EXTRA_TEXT, buildReadableReportText())
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "اشتراک‌گذاری گزارش"))
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("share_report_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "اشتراک‌گذاری گزارش",
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
                selectedTab = TopLevelTab.REPORTS,
                onSelectTab = onSelectTab,
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Report Mode Selector
                item(key = "report_mode_switcher") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = reportMode == 0,
                            onClick = { reportMode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("report_mode_time"),
                        ) {
                            Text("گزارش بازه زمانی", maxLines = 1)
                        }
                        SegmentedButton(
                            selected = reportMode == 1,
                            onClick = { reportMode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("report_mode_location"),
                        ) {
                            Text("گزارش بر اساس مکان", maxLines = 1)
                        }
                    }
                }

                if (reportMode == 0) {
                    // Time Period Filter Chips
                    item(key = "time_period_chips") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReportPeriod.entries.forEach { p ->
                                FilterChip(
                                    selected = period == p,
                                    onClick = { periodName = p.name },
                                    label = { Text(p.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    modifier = Modifier.testTag("report_period_${p.name.lowercase()}"),
                                )
                            }
                        }
                    }

                    // Summary Banner
                    item(key = "time_summary_card") {
                        ReportSummaryHeroCard(
                            title = "خلاصه گزارش ${period.label}",
                            subtitle = periodRangeSubtitle,
                            totalPallets = timeTotalPallets,
                            totalCartons = timeTotalCartons,
                            totalItems = timeTotalItems,
                            palletTickets = timePalletTickets,
                            cartonTickets = timeCartonTickets,
                        )
                    }

                    // Daily Breakdown Title
                    item(key = "daily_breakdown_header") {
                        Text(
                            text = "تفکیک روزانه ثبت پالت، کارتن و تیکت‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    if (dailyBreakdown.isEmpty()) {
                        item(key = "empty_time_report") {
                            EmptyStatePanel(
                                title = "داده‌ای در این بازه زمانی ثبت نشده است",
                                description = "بازه زمانی بزرگ‌تری (مانند ماهانه یا کل دوره) را انتخاب کنید یا پالت و تیکت جدید ثبت نمایید.",
                                icon = Icons.Filled.Assessment,
                            )
                        }
                    } else {
                        val maxActivity = dailyBreakdown.maxOf { maxOf(it.palletCount + it.cartonCount + it.totalTickets, 1) }
                        items(dailyBreakdown, key = { it.jalaliDate }) { day ->
                            DailyReportCard(
                                day = day,
                                maxActivity = maxActivity,
                            )
                        }
                    }
                } else {
                    // Location Filters: Warehouse & Row Chips
                    item(key = "location_filters") {
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
                                Text(
                                    text = "انتخاب انبار",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = selectedWarehouseId == null,
                                        onClick = {
                                            selectedWarehouseId = null
                                            selectedRowId = null
                                        },
                                        label = { Text("همه انبارها") },
                                        modifier = Modifier.testTag("report_warehouse_all"),
                                    )
                                    warehouses.forEach { wh ->
                                        FilterChip(
                                            selected = selectedWarehouseId == wh.id,
                                            onClick = {
                                                selectedWarehouseId = wh.id
                                                selectedRowId = null
                                            },
                                            label = { Text(wh.name) },
                                            modifier = Modifier.testTag("report_warehouse_${wh.id}"),
                                        )
                                    }
                                }

                                if (rowsForSelectedWarehouse.isNotEmpty()) {
                                    Text(
                                        text = "انتخاب ردیف انبار",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilterChip(
                                            selected = selectedRowId == null,
                                            onClick = { selectedRowId = null },
                                            label = { Text("همه ردیف‌ها") },
                                            modifier = Modifier.testTag("report_row_all"),
                                        )
                                        rowsForSelectedWarehouse.forEach { row ->
                                            val chipLabel = if (selectedWarehouseId == null) {
                                                "${row.warehouseName} / ${row.rowName}"
                                            } else {
                                                row.rowName
                                            }
                                            FilterChip(
                                                selected = selectedRowId == row.rowId,
                                                onClick = { selectedRowId = row.rowId },
                                                label = { Text(chipLabel) },
                                                modifier = Modifier.testTag("report_row_${row.rowId}"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Location Summary Card
                    item(key = "location_summary_card") {
                        val scopeTitle = warehouses.find { it.id == selectedWarehouseId }?.name ?: "همه انبارها"
                        val scopeSub = rowsForSelectedWarehouse.find { it.rowId == selectedRowId }?.let {
                            "ردیف انتخاب‌شده: ${it.rowName}"
                        } ?: "تعداد ردیف‌های گزارش‌شده: ${filteredRowReports.size.toPersianNumber()} ردیف"

                        ReportSummaryHeroCard(
                            title = "گزارش مکانی: $scopeTitle",
                            subtitle = scopeSub,
                            totalPallets = locTotalPallets,
                            totalCartons = locTotalCartons,
                            totalItems = locTotalItems,
                            palletTickets = locPalletTickets,
                            cartonTickets = locCartonTickets,
                        )
                    }

                    item(key = "location_rows_header") {
                        Text(
                            text = "خلاصه تفکیکی بر اساس ردیف انبار",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    if (filteredRowReports.isEmpty()) {
                        item(key = "empty_location_report") {
                            EmptyStatePanel(
                                title = "ردیفی برای نمایش گزارش موجود نیست",
                                description = "ابتدا در بخش «انبارها»، انبار و ردیف‌های مورد نظر را ایجاد کنید تا گزارش مکانی محاسبه شود.",
                                icon = Icons.Filled.ViewColumn,
                            )
                        }
                    } else {
                        val maxRowPallets = filteredRowReports.maxOf { maxOf(it.palletCount, 1) }
                        items(filteredRowReports, key = { it.rowId }) { rowItem ->
                            LocationRowReportCard(
                                rowItem = rowItem,
                                maxRowPallets = maxRowPallets,
                                onClick = { onOpenRow(rowItem.rowId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSummaryHeroCard(
    title: String,
    subtitle: String,
    totalPallets: Int,
    totalCartons: Int,
    totalItems: Int,
    palletTickets: Int,
    cartonTickets: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                ),
            )
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportKpiBox(
                    label = "کل پالت‌ها",
                    value = totalPallets.toPersianNumber(),
                    accentColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                )
                ReportKpiBox(
                    label = "کل کارتن‌ها",
                    value = totalCartons.toPersianNumber(),
                    accentColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f),
                )
                ReportKpiBox(
                    label = "مجموع اقلام",
                    value = totalItems.toPersianNumber(),
                    accentColor = Color(0xFF34D399),
                    modifier = Modifier.weight(1f),
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x1FFFFFFF),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "کل تیکت‌های ثبت‌شده: ${(palletTickets + cartonTickets).toPersianNumber()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = "پالت: ${palletTickets.toPersianNumber()}  |  کارتن: ${cartonTickets.toPersianNumber()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFDE68A),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportKpiBox(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0x1FFFFFFF),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE2E8F0),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DailyReportCard(
    day: DailyBreakdownRow,
    maxActivity: Int,
) {
    val ratio = ((day.palletCount + day.cartonCount + day.totalTickets).toFloat() / maxActivity.toFloat())
        .coerceIn(0.08f, 1f)

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = day.jalaliDate,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "${day.totalTickets.toPersianNumber()} عکس تیکت",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            // Visual Activity Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "پالت",
                    value = day.palletCount,
                    icon = Icons.Filled.Inventory,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "کارتن",
                    value = day.cartonCount,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "قلم",
                    value = day.itemCount,
                    icon = Icons.Filled.Layers,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LocationRowReportCard(
    rowItem: RowReportItem,
    maxRowPallets: Int,
    onClick: () -> Unit,
) {
    val ratio = (rowItem.palletCount.toFloat() / maxRowPallets.toFloat()).coerceIn(0.06f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("report_location_row_${rowItem.rowId}"),
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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rowItem.rowName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "انبار: ${rowItem.warehouseName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "مشاهده پالت‌های ردیف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "پالت",
                    value = rowItem.palletCount,
                    icon = Icons.Filled.Inventory,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "کارتن",
                    value = rowItem.totalCartons,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "اقلام",
                    value = rowItem.totalItems,
                    icon = Icons.Filled.Layers,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "تیکت پالت: ${rowItem.palletTicketCount.toPersianNumber()}  •  تیکت کارتن: ${rowItem.cartonTicketCount.toPersianNumber()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "کل تیکت‌ها: ${rowItem.totalTicketCount.toPersianNumber()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
