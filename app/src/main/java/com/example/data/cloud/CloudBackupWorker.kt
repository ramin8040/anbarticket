package com.example.data.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.BackupIntervalOption
import com.example.data.local.UserPreferences
import com.example.util.InternalImageFolderManager
import java.util.concurrent.TimeUnit

/**
 * Background CoroutineWorker that executes automatic incremental cloud backups
 * of ticket images and Room database data to ArvanCloud / S3-compatible storage.
 */
class CloudBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val folderManager = InternalImageFolderManager(applicationContext)
        val prefs = UserPreferences(applicationContext)
        val manager = CloudBackupManager(
            context = applicationContext,
            database = db,
            folderManager = folderManager,
            preferences = prefs,
        )

        if (!manager.config.isConfigured) {
            return Result.success()
        }

        return when (val outcome = manager.backupNow()) {
            is CloudResult.Success -> Result.success()
            is CloudResult.Error -> {
                if (outcome.isTransientNetworkError && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}

/**
 * Manages WorkManager periodic schedules and debounced immediate auto-backup jobs.
 */
object CloudBackupScheduler {
    private const val PERIODIC_WORK_NAME = "anbar_cloud_periodic_backup"
    private const val IMMEDIATE_AUTO_WORK_NAME = "anbar_cloud_immediate_auto_backup"

    fun applySchedule(
        context: Context,
        enabled: Boolean,
        interval: BackupIntervalOption,
        wifiOnly: Boolean,
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(IMMEDIATE_AUTO_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(
            repeatInterval = interval.minutes.coerceAtLeast(15L),
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Enqueues a debounced background sync shortly after a new ticket photo or data edit,
     * batching rapid consecutive camera captures into a single efficient upload session.
     */
    fun triggerDebouncedAutoBackup(
        context: Context,
        wifiOnly: Boolean,
    ) {
        if (!CloudStorageConfig.fromBuildConfig().isConfigured) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setInitialDelay(12, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_AUTO_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
