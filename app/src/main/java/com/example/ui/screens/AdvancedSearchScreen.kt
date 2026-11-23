package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CartonEntity
import com.example.data.local.PalletOptionItem
import com.example.data.local.TicketDetailItem
import com.example.ui.components.AppBottomNavigationBar
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTicketDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.StatBadge
import com.example.ui.components.ThemeToggleButton
import com.example.ui.components.TopLevelTab
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

private enum class TicketTypeFilter(val label: String) {
    ALL("همه"),
    PALLET_ONLY("فقط پالت"),
    CARTON_ONLY("فقط کارتن"),
}

private enum class DateRangePreset(val label: String, val daysBack: Int?) {
    ALL("همه تاریخ‌ها", null),
    TODAY("امروز", 0),
    LAST_7_DAYS("۷ روز اخیر", 6),
    LAST_30_DAYS("۳۰ روز اخیر", 29),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchScreen(
    allTickets: List<TicketDetailItem>,
    palletOptions: List<PalletOptionItem>,
    allCartons: List<CartonEntity>,
    isDarkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleTheme: () -> Unit,
    onSelectTab: (TopLevelTab) -> Unit,
    onOpenTicketViewer: (photoId: Long) -> Unit,
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
    onDeleteTicket: (Long) -> Unit,
) {
    var typeFilterName by rememberSaveable { mutableStateOf(TicketTypeFilter.ALL.name) }
    var datePresetName by rememberSaveable { mutableStateOf(DateRangePreset.ALL.name) }
    var exactDateQuery by rememberSaveable { mutableStateOf("") }
    var palletQuery by rememberSaveable { mutableStateOf("") }
    var cartonQuery by rememberSaveable { mutableStateOf("") }

    val typeFilter = remember(typeFilterName) { TicketTypeFilter.valueOf(typeFilterName) }
    val datePreset = remember(datePresetName) { DateRangePreset.valueOf(datePresetName) }

    var editingTicket by remember { mutableStateOf<TicketDetailItem?>(null) }
    var deletingTicket by remember { mutableStateOf<TicketDetailItem?>(null) }

    val hasActiveFilters = typeFilter != TicketTypeFilter.ALL ||
        datePreset != DateRangePreset.ALL ||
        exactDateQuery.isNotBlank() ||
        palletQuery.isNotBlank() ||
        cartonQuery.isNotBlank()

    val filteredTickets = remember(allTickets, typeFilter, datePreset, exactDateQuery, palletQuery, cartonQuery) {
        val cutoffMillis = datePreset.daysBack?.let { PersianUtils.startOfDaysAgoMillis(it) }
        val normalizedDateQuery = PersianUtils.toEnglishDigits(exactDateQuery.trim())
        val normalizedPalletQuery = PersianUtils.toEnglishDigits(palletQuery.trim()).lowercase()
        val normalizedCartonQuery = PersianUtils.toEnglishDigits(cartonQuery.trim()).lowercase()

        allTickets.filter { ticket ->
            val matchesType = when (typeFilter) {
                TicketTypeFilter.ALL -> true
                TicketTypeFilter.PALLET_ONLY -> !ticket.isCartonTicket
                TicketTypeFilter.CARTON_ONLY -> ticket.isCartonTicket
            }

            val matchesDatePreset = if (cutoffMillis != null) {
                ticket.capturedAt >= cutoffMillis
            } else {
                true
            }

            val matchesExactDate = if (normalizedDateQuery.isNotEmpty()) {
                val ticketDateEn = PersianUtils.toEnglishDigits(ticket.jalaliDateTime)
                ticketDateEn.contains(normalizedDateQuery)
            } else {
                true
            }

            val matchesPallet = if (normalizedPalletQuery.isNotEmpty()) {
                PersianUtils.toEnglishDigits(ticket.palletName).lowercase().contains(normalizedPalletQuery)
            } else {
                true
            }

            val matchesCarton = if (normalizedCartonQuery.isNotEmpty()) {
                val cartonNameEn = ticket.cartonName?.let { PersianUtils.toEnglishDigits(it).lowercase() }.orEmpty()
                cartonNameEn.contains(normalizedCartonQuery)
            } else {
                true
            }

            matchesType && matchesDatePreset && matchesExactDate && matchesPallet && matchesCarton
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
                            text = "جست‌وجوی پیشرفته تیکت‌ها",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "فیلتر بر اساس تاریخ، نوع کالا، شماره پالت و کارتن",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = TopLevelTab.SEARCH,
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
                item(key = "filter_panel") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "فیلترهای جست‌وجو",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (hasActiveFilters) {
                                    TextButton(
                                        onClick = {
                                            typeFilterName = TicketTypeFilter.ALL.name
                                            datePresetName = DateRangePreset.ALL.name
                                            exactDateQuery = ""
                                            palletQuery = ""
                                            cartonQuery = ""
                                        },
                                        modifier = Modifier.testTag("clear_search_filters_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ClearAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("پاک‌کردن فیلترها")
                                    }
                                }
                            }

                            // 1. Item Type (Pallet vs Carton)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "نوع کالا (تیکت)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    TicketTypeFilter.entries.forEachIndexed { index, option ->
                                        SegmentedButton(
                                            selected = typeFilter == option,
                                            onClick = { typeFilterName = option.name },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = TicketTypeFilter.entries.size,
                                            ),
                                            modifier = Modifier.testTag("search_type_${option.name.lowercase()}"),
                                        ) {
                                            Text(option.label, maxLines = 1)
                                        }
                                    }
                                }
                            }

                            // 2. Date Registration Filter (Presets + Exact Jalali Date)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "تاریخ ثبت",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    DateRangePreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = datePreset == preset,
                                            onClick = { datePresetName = preset.name },
                                            label = { Text(preset.label) },
                                            modifier = Modifier.testTag("search_date_preset_${preset.name.lowercase()}"),
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = exactDateQuery,
                                    onValueChange = { exactDateQuery = it },
                                    label = { Text("جست‌وجوی تاریخ شمسی دقیق یا بخشی از تاریخ") },
                                    placeholder = { Text("مثلاً: ۱۴۰۵/۰۹/۰۱ یا ۱۴۰۵/۰۹") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("search_exact_date_input"),
                                )
                            }

                            // 3. Pallet Number & Carton Number
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = palletQuery,
                                    onValueChange = { palletQuery = it },
                                    label = { Text("شماره/نام پالت") },
                                    placeholder = { Text("مثلاً: ۰۱") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Inventory,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("search_pallet_number_input"),
                                )

                                OutlinedTextField(
                                    value = cartonQuery,
                                    onValueChange = { cartonQuery = it },
                                    label = { Text("شماره/نام کارتن") },
                                    placeholder = { Text("مثلاً: ۵") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Inventory2,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("search_carton_number_input"),
                                )
                            }
                        }
                    }
                }

                // Results Header
                item(key = "results_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "نتایج جست‌وجو (${filteredTickets.size.toPersianNumber()} تیکت)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "از کل ${allTickets.size.toPersianNumber()} تیکت",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (allTickets.isEmpty()) {
                    item(key = "empty_all_tickets") {
                        EmptyStatePanel(
                            title = "هنوز تیکتی ثبت نشده است",
                            description = "از بخش «انبارها» وارد پالت یا کارتن مورد نظر شوید و عکس تیکت شمارش ثبت کنید تا اینجا قابل جست‌وجو باشد.",
                            icon = Icons.Filled.ManageSearch,
                        )
                    }
                } else if (filteredTickets.isEmpty()) {
                    item(key = "empty_filtered_tickets") {
                        EmptyStatePanel(
                            title = "تیکتی مطابق فیلترهای انتخابی یافت نشد",
                            description = "تاریخ، نوع کالا یا شماره پالت و کارتن واردشده را تغییر دهید یا فیلترها را پاک کنید.",
                            icon = Icons.Filled.ManageSearch,
                        )
                    }
                } else {
                    items(filteredTickets, key = { it.id }) { ticket ->
                        TicketSearchResultCard(
                            ticket = ticket,
                            onView = { onOpenTicketViewer(ticket.id) },
                            onEdit = { editingTicket = ticket },
                            onDelete = { deletingTicket = ticket },
                        )
                    }
                }
            }
        }
    }

    editingTicket?.let { target ->
        EditTicketDialog(
            ticket = target,
            palletOptions = palletOptions,
            allCartons = allCartons,
            onDismiss = { editingTicket = null },
            onSave = { newPalletId, newCartonId, dateStr, timeStr, note, pCartons, pItems, cItems, cb ->
                onUpdateTicket(
                    target.id,
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

    deletingTicket?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف تیکت ثبت‌شده",
            message = "آیا از حذف کامل این تیکت (${target.palletName}${target.cartonName?.let { " / $it" }.orEmpty()}) و پاک‌شدن عکس آن از حافظه گوشی مطمئن هستید؟",
            onDismiss = { deletingTicket = null },
            onConfirm = { onDeleteTicket(target.id) },
        )
    }
}

@Composable
private fun TicketSearchResultCard(
    ticket: TicketDetailItem,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onView)
            .testTag("search_result_ticket_${ticket.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AsyncImage(
                    model = ticket.mediaUri,
                    contentDescription = "عکس تیکت ${ticket.palletName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (ticket.isCartonTicket) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ) {
                            Text(
                                text = if (ticket.isCartonTicket) "تیکت کارتن" else "تیکت پالت",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (ticket.isCartonTicket) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }

                        Text(
                            text = ticket.jalaliDateTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    Text(
                        text = buildString {
                            append(ticket.palletName)
                            if (!ticket.cartonName.isNullOrBlank()) {
                                append("  ←  ${ticket.cartonName}")
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = "${ticket.warehouseName}  •  ${ticket.rowName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (ticket.note.isNotBlank()) {
                        Text(
                            text = "یادداشت: ${ticket.note}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ticket.isCartonTicket) {
                    StatBadge(
                        label = "اقلام کارتن",
                        value = ticket.cartonItemCount ?: 0,
                        icon = Icons.Filled.Layers,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    StatBadge(
                        label = "کارتن پالت",
                        value = ticket.palletCartonCount,
                        icon = Icons.Filled.Inventory2,
                        modifier = Modifier.weight(1f),
                    )
                    StatBadge(
                        label = "اقلام پالت",
                        value = ticket.palletItemCount,
                        icon = Icons.Filled.Layers,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = ticket.relativeFolderPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onView,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("search_view_ticket_${ticket.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاهده", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("search_edit_ticket_${ticket.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ویرایش", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("search_delete_ticket_${ticket.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "حذف",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
