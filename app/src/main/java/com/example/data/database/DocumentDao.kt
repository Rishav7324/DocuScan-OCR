package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getDocumentsByFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE isSynced = 0")
    suspend fun getUnsyncedDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE tags LIKE '%' || :tag || '%' ORDER BY createdAt DESC")
    fun getDocumentsByTag(tag: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents WHERE
        name LIKE '%' || :query || '%' OR
        tags LIKE '%' || :query || '%' OR
        notes LIKE '%' || :query || '%' OR
        COALESCE(extractedTextSummary, '') LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>
}
