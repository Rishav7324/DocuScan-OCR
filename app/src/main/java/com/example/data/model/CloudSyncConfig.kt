package com.example.data.model

data class CloudSyncConfig(
    val r2Enabled: Boolean = false,
    val r2Bucket: String = "",
    val r2Endpoint: String = "",
    val r2AccessKey: String = "",
    val r2SecretKey: String = "",
    
    val googleDriveEnabled: Boolean = false,
    val googleDriveAccount: String = "",
    
    val dropboxEnabled: Boolean = false,
    val dropboxAccount: String = "",
    
    val autoBackup: Boolean = true,
    val wifiOnly: Boolean = false,
    val lastSyncTime: Long = 0L
)
