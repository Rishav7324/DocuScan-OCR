package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false,
    val passwordHash: String? = null, // Encrypted verification marker for private folder access (not the raw PIN)
    val isSynced: Boolean = false
)
