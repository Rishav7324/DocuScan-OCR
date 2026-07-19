package com.example.data.api

import com.example.data.repository.DocumentRepository
import com.example.data.model.CloudSyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Core cloud-sync logic, shared by the ViewModel's manual "Sync Now" and the
 * WorkManager [com.example.workers.BackupWorker] for scheduled auto-backup.
 */
object SyncEngine {

    suspend fun runSync(
        repository: DocumentRepository,
        config: CloudSyncConfig
    ): SyncResult = withContext(Dispatchers.IO) {
        var uploadCount = 0
        var errorCount = 0
        val sbDetails = StringBuilder()

        // Refresh tokens up front so the run doesn't fail on expiry.
        var cfg = config
        if (cfg.googleDriveEnabled && cfg.googleDriveRefreshToken.isNotBlank()) {
            runCatching { OAuthManager.refreshAccessToken(OAuthManager.Provider.GOOGLE, cfg.googleDriveRefreshToken) }
                .onSuccess { cfg = cfg.copy(googleDriveToken = it) }
        }
        if (cfg.dropboxEnabled && cfg.dropboxRefreshToken.isNotBlank()) {
            runCatching { OAuthManager.refreshAccessToken(OAuthManager.Provider.DROPBOX, cfg.dropboxRefreshToken) }
                .onSuccess { cfg = cfg.copy(dropboxToken = it) }
        }

        val unsynced = repository.getUnsyncedDocuments()
        unsynced.forEach { doc ->
            val pages = repository.getPagesForDocumentSync(doc.id)
            var docUploads = 0
            var docErrors = 0
            pages.forEach { page ->
                val imagePath = page.processedImagePath ?: page.originalImagePath
                if (imagePath.isEmpty()) return@forEach
                val file = File(imagePath)
                if (!file.exists()) return@forEach
                val fileBytes = file.readBytes()
                val fileName = "docuscan_${doc.id}_page_${page.pageNumber}.jpg"

                if (cfg.googleDriveEnabled) {
                    CloudSyncIntegrator.uploadToGoogleDrive(fileBytes, fileName, "image/jpeg", cfg.googleDriveToken)
                        .onSuccess { uploadCount++; docUploads++; sbDetails.append("Google Drive: $fileName ok\n") }
                        .onFailure { errorCount++; docErrors++; sbDetails.append("Google Drive err $fileName: ${it.message}\n") }
                }
                if (cfg.dropboxEnabled) {
                    CloudSyncIntegrator.uploadToDropbox(fileBytes, fileName, cfg.dropboxToken)
                        .onSuccess { uploadCount++; docUploads++; sbDetails.append("Dropbox: $fileName ok\n") }
                        .onFailure { errorCount++; docErrors++; sbDetails.append("Dropbox err $fileName: ${it.message}\n") }
                }
                if (cfg.r2Enabled) {
                    CloudSyncIntegrator.uploadToCloudflareR2(fileBytes, fileName, cfg.r2Bucket, cfg.r2Endpoint, cfg.r2AccessKey, cfg.r2SecretKey)
                        .onSuccess { uploadCount++; docUploads++; sbDetails.append("R2: $fileName ok\n") }
                        .onFailure { errorCount++; docErrors++; sbDetails.append("R2 err $fileName: ${it.message}\n") }
                }
            }
            // Only mark synced when the doc actually uploaded somewhere with no errors.
            if (docUploads > 0 && docErrors == 0) {
                repository.updateDocument(doc.copy(isSynced = true))
            }
        }
        SyncResult(uploadCount, errorCount, sbDetails.toString())
    }

    data class SyncResult(val uploads: Int, val errors: Int, val details: String)
}
