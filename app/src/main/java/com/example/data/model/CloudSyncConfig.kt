package com.example.data.model

data class CloudSyncConfig(
    val r2Enabled: Boolean = false,
    val r2Bucket: String = "",
    val r2Endpoint: String = "",
    val r2AccessKey: String = "",
    val r2SecretKey: String = "",
    
    val googleDriveEnabled: Boolean = false,
    val googleDriveAccount: String = "",
    val googleDriveToken: String = "",
    val googleDriveRefreshToken: String = "",
    
    val dropboxEnabled: Boolean = false,
    val dropboxAccount: String = "",
    val dropboxToken: String = "",
    val dropboxRefreshToken: String = "",
    
    val autoBackup: Boolean = true,
    val wifiOnly: Boolean = false,
    val lastSyncTime: Long = 0L
)

