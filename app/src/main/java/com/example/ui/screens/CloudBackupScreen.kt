package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.cloud.CloudStorageConfig
import com.example.data.cloud.CloudTransferProgress
import com.example.data.local.BackupIntervalOption
import com.example.data.local.CloudBackupPreferences
import com.example.data.local.GlobalStats
import com.example.data.local.LastBackupStatus
import com.example.ui.components.StatBadge
import com.example.ui.components.ThemeToggleButton
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    config: CloudStorageConfig,
    backupPrefs: CloudBackupPreferences,
    globalStats: GlobalStats,
    syncedPhotoCount: Int,
    transferProgress: CloudTransferProgress,
    isTestingConnection: Boolean,
    isDarkTheme: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onSetAutoBackupEnabled: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetInterval: (BackupIntervalOption) -> Unit,
    onBackupNow: () -> Unit,
    onTestConnection: () -> Unit,
    onRestoreFromCloud: () -> Unit,
) {
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val pendingPhotoCount = (globalStats.totalPhotos - syncedPhotoCount).coerceAtLeast(0)
    val isBusy = transferProgress.isRunning || isTestingConnection

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "پشتیبان‌گیری ابری",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "ابر آروان و فضای ابری سازگار با S3",
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
                            .testTag("cloud_backup_back_button"),
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "cloud_hero") {
                    CloudStatusHeroCard(
                        config = config,
                        backupPrefs = backupPrefs,
                        pendingPhotoCount = pendingPhotoCount,
                        transferProgress = transferProgress,
                        isDarkTheme = isDarkTheme,
                    )
                }

                if (!config.isConfigured) {
                    item(key = "config_notice") {
                        CloudConfigurationGuideCard(
                            validationMessage = config.validationError().orEmpty(),
                        )
                    }
                }

                item(key = "coverage_stats") {
                    CloudCoverageCard(
                        globalStats = globalStats,
                        syncedPhotoCount = syncedPhotoCount,
                        pendingPhotoCount = pendingPhotoCount,
                    )
                }

                item(key = "auto_backup_settings") {
                    AutoBackupSettingsCard(
                        backupPrefs = backupPrefs,
                        enabledControls = !isBusy,
                        onSetAutoBackupEnabled = onSetAutoBackupEnabled,
                        onSetWifiOnly = onSetWifiOnly,
                        onSetInterval = onSetInterval,
                    )
                }

                item(key = "manual_actions") {
                    CloudActionsCard(
                        isConfigured = config.isConfigured,
                        isBusy = isBusy,
                        isTestingConnection = isTestingConnection,
                        onBackupNow = onBackupNow,
                        onTestConnection = onTestConnection,
                        onRequestRestore = { showRestoreConfirmDialog = true },
                    )
                }
            }
        }
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = {
                Text(
                    text = "بازیابی کامل از فضای ابری",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "با انجام بازیابی، آخرین نسخه پشتیبان ثبت‌شده در فضای ابری شامل تمام انبارها، ردیف‌ها، پالت‌ها، کارتن‌ها و تصاویر تیکت‌ها دانلود شده و جایگزین اطلاعات فعلی گوشی می‌شود. ادامه می‌دهید؟",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        onRestoreFromCloud()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier.testTag("confirm_cloud_restore_button"),
                ) {
                    Text("شروع بازیابی ابری")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_cloud_restore_button"),
                ) {
                    Text("انصراف")
                }
            },
        )
    }
}

@Composable
private fun CloudStatusHeroCard(
    config: CloudStorageConfig,
    backupPrefs: CloudBackupPreferences,
    pendingPhotoCount: Int,
    transferProgress: CloudTransferProgress,
    isDarkTheme: Boolean,
) {
    val gradientColors = if (isDarkTheme) {
        listOf(Color(0xFF1E293B), Color(0xFF131C27))
    } else {
        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    }

    val statusIcon = when {
        !config.isConfigured -> Icons.Filled.CloudOff
        transferProgress.isRunning -> Icons.Filled.CloudSync
        backupPrefs.lastBackupStatus == LastBackupStatus.ERROR -> Icons.Filled.CloudOff
        pendingPhotoCount == 0 && backupPrefs.lastBackupAtMillis > 0L -> Icons.Filled.CloudDone
        else -> Icons.Filled.CloudSync
    }

    val statusTint = when {
        !config.isConfigured -> Color(0xFFF59E0B)
        transferProgress.isRunning -> Color(0xFF38BDF8)
        backupPrefs.lastBackupStatus == LastBackupStatus.ERROR -> Color(0xFFF87171)
        pendingPhotoCount == 0 && backupPrefs.lastBackupAtMillis > 0L -> Color(0xFF34D399)
        else -> Color(0xFFF59E0B)
    }

    val statusHeadline = when {
        transferProgress.isRunning -> transferProgress.statusText.ifBlank { "در حال همگام‌سازی ابری…" }
        !config.isConfigured -> "نیاز به تنظیم کلیدهای فضای ابری"
        backupPrefs.lastBackupStatus == LastBackupStatus.ERROR -> "خطا در آخرین همگام‌سازی"
        pendingPhotoCount == 0 && backupPrefs.lastBackupAtMillis > 0L -> "تمام داده‌ها و تصاویر در ابر ایمن هستند"
        pendingPhotoCount > 0 -> "${pendingPhotoCount.toPersianNumber()} تصویر در انتظار پشتیبان‌گیری"
        else -> "آماده برای اولین پشتیبان‌گیری ابری"
    }

    val lastTimeLabel = if (backupPrefs.lastBackupAtMillis > 0L) {
        "آخرین پشتیبان موفق: ${PersianUtils.formatJalaliDateTime(backupPrefs.lastBackupAtMillis)}"
    } else {
        "هنوز نسخه پشتیبانی در فضای ابری ثبت نشده است"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(gradientColors))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = statusTint.copy(alpha = 0.18f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusTint,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusHeadline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lastTimeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                }
            }

            if (transferProgress.isRunning) {
                val fraction = if (transferProgress.totalSteps > 0) {
                    (transferProgress.currentStep.toFloat() / transferProgress.totalSteps.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                if (transferProgress.totalSteps > 0) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0x33FFFFFF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0x33FFFFFF),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }

            if (backupPrefs.lastBackupMessage.isNotBlank() && !transferProgress.isRunning) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1FFFFFFF),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = backupPrefs.lastBackupMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backupPrefs.lastBackupStatus == LastBackupStatus.ERROR) {
                            Color(0xFFFECACA)
                        } else {
                            Color(0xFFE2E8F0)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            // Read-only server & bucket display (never displays secret keys)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0x18FFFFFF),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "سرور / سطل ابری:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${config.hostDisplay} / ${config.bucket}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFDE68A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudConfigurationGuideCard(
    validationMessage: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cloud_config_guide_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "راهنمای اتصال به ابر آروان (یا هر فضای S3)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (validationMessage.isNotBlank()) {
                Text(
                    text = validationMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = "برای حفظ امنیت کلیدهای دسترسی، مقادیر زیر را در پنل Secrets در AI Studio تنظیم کنید تا هنگام ساخت برنامه به‌صورت امن تزریق شوند:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "ARVAN_S3_ENDPOINT=https://s3.ir-thr-at1.arvanstorage.ir\n" +
                            "ARVAN_S3_BUCKET=<نام سطل>\n" +
                            "ARVAN_S3_REGION=ir-thr-at1\n" +
                            "ARVAN_S3_ACCESS_KEY=<کلید دسترسی>\n" +
                            "ARVAN_S3_SECRET_KEY=<کلید محرمانه>",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudCoverageCard(
    globalStats: GlobalStats,
    syncedPhotoCount: Int,
    pendingPhotoCount: Int,
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
            Text(
                text = "پوشش پشتیبان‌گیری داده‌ها و تصاویر",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "کل تیکت‌ها",
                    value = globalStats.totalPhotos,
                    icon = Icons.Filled.PhotoLibrary,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "در ابر",
                    value = syncedPhotoCount,
                    icon = Icons.Filled.CloudDone,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "در انتظار",
                    value = pendingPhotoCount,
                    icon = Icons.Filled.CloudUpload,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBadge(
                    label = "انبار",
                    value = globalStats.warehouseCount,
                    icon = Icons.Filled.Warehouse,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "پالت",
                    value = globalStats.palletCount,
                    icon = Icons.Filled.Inventory,
                    modifier = Modifier.weight(1f),
                )
                StatBadge(
                    label = "کارتن",
                    value = globalStats.totalCartons,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AutoBackupSettingsCard(
    backupPrefs: CloudBackupPreferences,
    enabledControls: Boolean,
    onSetAutoBackupEnabled: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetInterval: (BackupIntervalOption) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "تنظیمات پشتیبان‌گیری خودکار",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "پشتیبان‌گیری خودکار در پس‌زمینه",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "ارسال خودکار تصاویر جدید پس از عکاسی و همگام‌سازی دوره‌ای با حفظ ساختار پوشه‌ها",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = backupPrefs.autoBackupEnabled,
                    onCheckedChange = onSetAutoBackupEnabled,
                    enabled = enabledControls,
                    modifier = Modifier.testTag("cloud_auto_backup_switch"),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = "ارسال فقط از طریق Wi-Fi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "جلوگیری از مصرف اینترنت همراه هنگام آپلود خودکار عکس‌ها",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = backupPrefs.wifiOnly,
                    onCheckedChange = onSetWifiOnly,
                    enabled = enabledControls && backupPrefs.autoBackupEnabled,
                    modifier = Modifier.testTag("cloud_wifi_only_switch"),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "بازه زمانی همگام‌سازی دوره‌ای",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BackupIntervalOption.entries.forEach { option ->
                        FilterChip(
                            selected = backupPrefs.interval == option,
                            onClick = { onSetInterval(option) },
                            enabled = enabledControls && backupPrefs.autoBackupEnabled,
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.testTag("cloud_interval_${option.minutes}"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudActionsCard(
    isConfigured: Boolean,
    isBusy: Boolean,
    isTestingConnection: Boolean,
    onBackupNow: () -> Unit,
    onTestConnection: () -> Unit,
    onRequestRestore: () -> Unit,
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
            Text(
                text = "عملیات پشتیبان‌گیری و بازیابی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Button(
                onClick = onBackupNow,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_backup_now_button"),
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "پشتیبان‌گیری فوری (تصاویر + دیتابیس)",
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !isBusy,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cloud_test_connection_button"),
                ) {
                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTestingConnection) "در حال بررسی…" else "تست اتصال",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalButton(
                    onClick = onRequestRestore,
                    enabled = !isBusy && isConfigured,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cloud_restore_button"),
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "بازیابی از ابر",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
