package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long, // 0 for Root/Uncategorized
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val fileFormat: String = "PDF", // PDF, DOCX, TXT
    val hasOcr: Boolean = false,
    val isEncrypted: Boolean = false,
    val isImageEncrypted: Boolean = false,
    val extractedTextSummary: String? = null,
    val isFavorite: Boolean = false,   // User-starred documents
    val tags: String = "",            // Comma-separated labels, e.g. "invoice,2026"
    val notes: String = ""            // Free-form user note attached to the document
)
