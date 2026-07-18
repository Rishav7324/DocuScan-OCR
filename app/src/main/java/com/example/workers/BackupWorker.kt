package com.example.workers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SyncConfigStore
import com.example.data.api.SyncEngine
import com.example.data.database.AppDatabase
import com.example.data.database.DocumentRepository

/**
 * Scheduled by [com.example.workers.SyncScheduler] when auto-backup is enabled.
 * Honors the "Wi-Fi only" preference and skips when auto-backup is off.
 */
class BackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val config = SyncConfigStore.load(applicationContext)
        if (!config.autoBackup) return Result.success()

        if (config.wifiOnly && !isOnWifi(applicationContext)) {
            // Retry later when on an unmetered network.
            return Result.retry()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val repo = DocumentRepository(
            folderDao = db.folderDao(),
            documentDao = db.documentDao(),
            pageDao = db.pageDao(),
            auditLogDao = db.auditLogDao()
        )
        SyncEngine.runSync(repo, config)
        return Result.success()
    }

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
