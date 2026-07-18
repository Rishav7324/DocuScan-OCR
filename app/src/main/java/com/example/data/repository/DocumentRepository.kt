package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val auditLogDao: AuditLogDao
) {
    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    fun getDocumentsByFolder(folderId: Long): Flow<List<DocumentEntity>> {
        return documentDao.getDocumentsByFolder(folderId)
    }

    suspend fun getFolderById(id: Long): FolderEntity? {
        return folderDao.getFolderById(id)
    }

    suspend fun insertFolder(folder: FolderEntity): Long {
        return folderDao.insertFolder(folder)
    }

    suspend fun updateFolder(folder: FolderEntity) {
        folderDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        folderDao.deleteFolder(folder)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun insertDocument(document: DocumentEntity): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        // First delete all pages associated with this document
        pageDao.deletePagesForDocument(document.id)
        documentDao.deleteDocument(document)
    }

    fun getPagesForDocument(documentId: Long): Flow<List<PageEntity>> {
        return pageDao.getPagesForDocument(documentId)
    }

    suspend fun getPagesForDocumentSync(documentId: Long): List<PageEntity> {
        return pageDao.getPagesForDocumentSync(documentId)
    }

    suspend fun insertPage(page: PageEntity): Long {
        return pageDao.insertPage(page)
    }

    suspend fun updatePage(page: PageEntity) {
        pageDao.updatePage(page)
    }

    suspend fun deletePage(page: PageEntity) {
        pageDao.deletePage(page)
    }

    suspend fun getUnsyncedDocuments(): List<DocumentEntity> {
        return documentDao.getUnsyncedDocuments()
    }

    fun getFavoriteDocuments(): Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()

    fun getDocumentsByTag(tag: String): Flow<List<DocumentEntity>> = documentDao.getDocumentsByTag(tag)

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> = documentDao.searchDocuments(query)

    val allAuditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllLogs()

    suspend fun insertAuditLog(log: AuditLogEntity): Long = auditLogDao.insertLog(log)

    suspend fun clearAuditLogs() = auditLogDao.clearLogs()
}
