package com.example.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules a daily [BackupWorker] when auto-backup is enabled, or cancels it otherwise.
 * ponytail: daily cadence is enough; no fancy backoff beyond WorkManager defaults.
 */
object SyncScheduler {
    private const val WORK_NAME = "docuscan_auto_backup"

    fun apply(context: Context, autoBackupEnabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!autoBackupEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
