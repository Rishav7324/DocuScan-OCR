package com.example.data

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.example.data.model.CloudSyncConfig

/** Reads [CloudSyncConfig] from the shared "sync_prefs" the ViewModel writes. */
object SyncConfigStore {
    private const val PREFS = "sync_prefs"

    fun load(context: Context): CloudSyncConfig {
        val p = context.getSharedPreferences(PREFS, MODE_PRIVATE)
        return CloudSyncConfig(
            r2Enabled = p.getBoolean("r2_enabled", false),
            r2Bucket = p.getString("r2_bucket", "") ?: "",
            r2Endpoint = p.getString("r2_endpoint", "") ?: "",
            r2AccessKey = p.getString("r2_access_key", "") ?: "",
            r2SecretKey = p.getString("r2_secret_key", "") ?: "",
            googleDriveEnabled = p.getBoolean("drive_enabled", false),
            googleDriveAccount = p.getString("drive_account", "") ?: "",
            googleDriveToken = p.getString("drive_token", "") ?: "",
            googleDriveRefreshToken = p.getString("drive_refresh_token", "") ?: "",
            dropboxEnabled = p.getBoolean("dropbox_enabled", false),
            dropboxAccount = p.getString("dropbox_account", "") ?: "",
            dropboxToken = p.getString("dropbox_token", "") ?: "",
            dropboxRefreshToken = p.getString("dropbox_refresh_token", "") ?: "",
            autoBackup = p.getBoolean("auto_backup", true),
            wifiOnly = p.getBoolean("wifi_only", false),
            lastSyncTime = p.getLong("last_sync_time", 0L)
        )
    }
}
