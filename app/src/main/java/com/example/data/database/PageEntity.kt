package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_pages")
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val originalImagePath: String,
    val processedImagePath: String? = null,
    val extractedText: String? = null,
    val cropPointsJson: String? = null, // Stores crop coordinates as JSON (e.g. [[0.1,0.1],[0.9,0.1],...])
    val filterType: String = "ORIGINAL" // ORIGINAL, B&W, SHARPEN, GREYSCALE
)
