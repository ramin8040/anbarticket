package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.anbarPreferencesDataStore by preferencesDataStore(name = "anbar_ticket_prefs")

enum class BackupIntervalOption(val minutes: Long, val label: String) {
    EVERY_15_MIN(15L, "هر ۱۵ دقیقه"),
    HOURLY(60L, "هر ۱ ساعت"),
    EVERY_6_HOURS(360L, "هر ۶ ساعت"),
    DAILY(1440L, "روزانه"),
    ;

    companion object {
        fun fromMinutes(minutes: Long): BackupIntervalOption =
            entries.firstOrNull { it.minutes == minutes } ?: HOURLY
    }
}

enum class LastBackupStatus {
    NEVER,
    SUCCESS,
    ERROR,
}

data class CloudBackupPreferences(
    val autoBackupEnabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val interval: BackupIntervalOption = BackupIntervalOption.HOURLY,
    val lastBackupAtMillis: Long = 0L,
    val lastBackupStatus: LastBackupStatus = LastBackupStatus.NEVER,
    val lastBackupMessage: String = "",
    val lastBackupUploadedCount: Int = 0,
)

class UserPreferences(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val WATERMARK_ENABLED = booleanPreferencesKey("watermark_enabled")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("cloud_auto_backup_enabled")
        val BACKUP_WIFI_ONLY = booleanPreferencesKey("cloud_backup_wifi_only")
        val BACKUP_INTERVAL_MINUTES = longPreferencesKey("cloud_backup_interval_minutes")
        val LAST_BACKUP_AT_MILLIS = longPreferencesKey("cloud_last_backup_at_millis")
        val LAST_BACKUP_STATUS = stringPreferencesKey("cloud_last_backup_status")
        val LAST_BACKUP_MESSAGE = stringPreferencesKey("cloud_last_backup_message")
        val LAST_BACKUP_UPLOADED_COUNT = intPreferencesKey("cloud_last_backup_uploaded_count")
    }

    val isDarkTheme: Flow<Boolean> = context.anbarPreferencesDataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_THEME] ?: false
    }

    val watermarkEnabled: Flow<Boolean> = context.anbarPreferencesDataStore.data.map { prefs ->
        prefs[Keys.WATERMARK_ENABLED] ?: true
    }

    val cloudBackupPreferences: Flow<CloudBackupPreferences> = context.anbarPreferencesDataStore.data.map { prefs ->
        val statusRaw = prefs[Keys.LAST_BACKUP_STATUS] ?: LastBackupStatus.NEVER.name
        val status = runCatching { LastBackupStatus.valueOf(statusRaw) }.getOrDefault(LastBackupStatus.NEVER)
        CloudBackupPreferences(
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: true,
            wifiOnly = prefs[Keys.BACKUP_WIFI_ONLY] ?: false,
            interval = BackupIntervalOption.fromMinutes(prefs[Keys.BACKUP_INTERVAL_MINUTES] ?: 60L),
            lastBackupAtMillis = prefs[Keys.LAST_BACKUP_AT_MILLIS] ?: 0L,
            lastBackupStatus = status,
            lastBackupMessage = prefs[Keys.LAST_BACKUP_MESSAGE].orEmpty(),
            lastBackupUploadedCount = prefs[Keys.LAST_BACKUP_UPLOADED_COUNT] ?: 0,
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.anbarPreferencesDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = enabled
        }
    }

    suspend fun setWatermarkEnabled(enabled: Boolean) {
        context.anbarPreferencesDataStore.edit { prefs ->
            prefs[Keys.WATERMARK_ENABLED] = enabled
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.anbarPreferencesDataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setBackupWifiOnly(wifiOnly: Boolean) {
        context.anbarPreferencesDataStore.edit { prefs ->
            prefs[Keys.BACKUP_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setBackupInterval(interval: BackupIntervalOption) {
        context.anbarPreferencesDataStore.edit { prefs ->
            prefs[Keys.BACKUP_INTERVAL_MINUTES] = interval.minutes
        }
    }

    suspend fun recordBackupOutcome(
        status: LastBackupStatus,
        timestampMillis: Long,
        message: String,
        uploadedCount: Int,
    ) {
        context.anbarPreferencesDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_STATUS] = status.name
            if (status == LastBackupStatus.SUCCESS) {
                prefs[Keys.LAST_BACKUP_AT_MILLIS] = timestampMillis
            }
            prefs[Keys.LAST_BACKUP_MESSAGE] = message
            prefs[Keys.LAST_BACKUP_UPLOADED_COUNT] = uploadedCount
        }
    }
}
