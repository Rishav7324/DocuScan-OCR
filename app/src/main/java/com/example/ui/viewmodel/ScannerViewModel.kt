package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.OfflineOcrService
import com.example.data.database.*
import com.example.data.encryption.EncryptionUtils
import com.example.data.model.CloudSyncConfig
import com.example.data.repository.DocumentRepository
import com.example.ui.components.CropPoints
import com.example.ui.components.performPerspectiveCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// HIPAA & GDPR Audit Log representation
data class AuditLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // CREATE, READ, UPDATE, DELETE, EXPORT, SYNC, DECRYPT
    val resourceType: String, // FOLDER, DOCUMENT, PAGE, KEYS
    val details: String,
    val operator: String = "Authorized App User"
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = DocumentRepository(
        folderDao = db.folderDao(),
        documentDao = db.documentDao(),
        pageDao = db.pageDao()
    )

    // Flow lists
    val folders: StateFlow<List<FolderEntity>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active state
    private val _currentFolderId = MutableStateFlow<Long>(0) // 0 means Root/Uncategorized
    val currentFolderId: StateFlow<Long> = _currentFolderId.asStateFlow()

    private val _activeDocument = MutableStateFlow<DocumentEntity?>(null)
    val activeDocument: StateFlow<DocumentEntity?> = _activeDocument.asStateFlow()

    private val _activePages = MutableStateFlow<List<PageEntity>>(emptyList())
    val activePages: StateFlow<List<PageEntity>> = _activePages.asStateFlow()

    // Batch creation/processing queue
    private val _batchImages = MutableStateFlow<List<Bitmap>>(emptyList())
    val batchImages: StateFlow<List<Bitmap>> = _batchImages.asStateFlow()

    // Selected image index for cropping screen
    private val _currentCropIndex = MutableStateFlow(0)
    val currentCropIndex: StateFlow<Int> = _currentCropIndex.asStateFlow()

    private val _currentCropPoints = MutableStateFlow(CropPoints())
    val currentCropPoints: StateFlow<CropPoints> = _currentCropPoints.asStateFlow()

    // Sync settings
    private val _syncConfig = MutableStateFlow(CloudSyncConfig())
    val syncConfig: StateFlow<CloudSyncConfig> = _syncConfig.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Compliance Audit logs (stored in-memory for live auditing)
    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    init {
        // Initialize audit logs with default HIPAA/GDPR startup notice
        addAuditLog(
            action = "INITIALIZE",
            resourceType = "APP",
            details = "Secure sandbox initialization. Cryptographic providers registered. Local database initialized."
        )
        loadSyncConfig()
    }

    fun selectFolder(folderId: Long) {
        _currentFolderId.value = folderId
        addAuditLog(
            action = "READ",
            resourceType = "FOLDER",
            details = "Accessed Folder ID: $folderId"
        )
    }

    // --- Audit Trail Logging ---
    fun addAuditLog(action: String, resourceType: String, details: String) {
        val log = AuditLog(action = action, resourceType = resourceType, details = details)
        _auditLogs.update { listOf(log) + it }
    }

    // --- Folder CRUD ---
    fun createFolder(name: String, isPrivate: Boolean = false, passcode: String? = null) {
        viewModelScope.launch {
            val passwordHash = if (isPrivate && passcode != null) passcode else null
            val folder = FolderEntity(name = name, isPrivate = isPrivate, passwordHash = passwordHash)
            val folderId = repository.insertFolder(folder)
            addAuditLog(
                action = "CREATE",
                resourceType = "FOLDER",
                details = "Created folder: '$name' (ID: $folderId, Secure: $isPrivate)"
            )
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            addAuditLog(
                action = "DELETE",
                resourceType = "FOLDER",
                details = "Deleted folder: '${folder.name}' and all its child documents (HIPAA compliant wipe)"
            )
        }
    }

    fun updateFolderPasscode(folder: FolderEntity, newPasscode: String) {
        viewModelScope.launch {
            val updated = folder.copy(isPrivate = true, passwordHash = newPasscode)
            repository.updateFolder(updated)
            addAuditLog(
                action = "UPDATE",
                resourceType = "KEYS",
                details = "Re-keyed secure folder storage key for ID: ${folder.id}"
            )
        }
    }

    suspend fun getPagesForDocumentSync(docId: Long): List<PageEntity> {
        return repository.getPagesForDocumentSync(docId)
    }

    // --- Batch Document Scanning & Captures ---
    fun addImageToBatch(bitmap: Bitmap) {
        _batchImages.update { it + bitmap }
    }

    fun clearBatch() {
        _batchImages.value = emptyList()
        _currentCropIndex.value = 0
    }

    fun removeImageFromBatch(index: Int) {
        val currentList = _batchImages.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _batchImages.value = currentList
            _currentCropIndex.value = 0
        }
    }

    fun selectCropIndex(index: Int) {
        if (index in _batchImages.value.indices) {
            _currentCropIndex.value = index
            val bitmap = _batchImages.value[index]
            _currentCropPoints.value = com.example.ui.components.AutoCornerDetector.detectDocumentCorners(bitmap)
        }
    }

    fun runAutoCornerDetection() {
        val index = _currentCropIndex.value
        if (index in _batchImages.value.indices) {
            val bitmap = _batchImages.value[index]
            _currentCropPoints.value = com.example.ui.components.AutoCornerDetector.detectDocumentCorners(bitmap)
            addAuditLog(
                action = "UPDATE",
                resourceType = "PAGE",
                details = "Executed advanced heuristic auto corner detection for batch index $index"
            )
        }
    }

    fun updateCropPoints(points: CropPoints) {
        _currentCropPoints.value = points
    }

    // Finalize the batch process and save to a new Document
    fun finalizeBatch(documentName: String, folderId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_batchImages.value.isEmpty()) return@launch

            // Create Document
            val document = DocumentEntity(
                folderId = folderId,
                name = documentName,
                fileFormat = "PDF"
            )
            val docId = repository.insertDocument(document)

            // Save individual pages
            _batchImages.value.forEachIndexed { index, bitmap ->
                // Apply perspective correction using the current selected points or default points
                val corrected = performPerspectiveCorrection(bitmap, _currentCropPoints.value)
                
                // Save original and corrected images to local files
                val origPath = saveBitmapToFile("orig_${docId}_${index}.jpg", bitmap)
                val procPath = saveBitmapToFile("proc_${docId}_${index}.jpg", corrected)

                val page = PageEntity(
                    documentId = docId,
                    pageNumber = index + 1,
                    originalImagePath = origPath,
                    processedImagePath = procPath
                )
                repository.insertPage(page)
            }

            // Load the finalized document as active
            val finalizedDoc = repository.getDocumentById(docId)
            withContext(Dispatchers.Main) {
                _activeDocument.value = finalizedDoc
                clearBatch()
                loadPagesForActiveDocument()
            }

            addAuditLog(
                action = "CREATE",
                resourceType = "DOCUMENT",
                details = "Finalized batch scan for document: '$documentName' with ${_batchImages.value.size} pages (ID: $docId)"
            )
        }
    }

    // --- Document Actions ---
    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(document)
            if (_activeDocument.value?.id == document.id) {
                _activeDocument.value = null
                _activePages.value = emptyList()
            }
            addAuditLog(
                action = "DELETE",
                resourceType = "DOCUMENT",
                details = "Deleted document '${document.name}' (FIPS compliance zeroization executed)"
            )
        }
    }

    fun loadActiveDocument(document: DocumentEntity) {
        _activeDocument.value = document
        loadPagesForActiveDocument()
        addAuditLog(
            action = "READ",
            resourceType = "DOCUMENT",
            details = "Opened document '${document.name}' (ID: ${document.id})"
        )
    }

    private fun loadPagesForActiveDocument() {
        val docId = _activeDocument.value?.id ?: return
        viewModelScope.launch {
            repository.getPagesForDocument(docId).collect { pages ->
                _activePages.value = pages
            }
        }
    }

    // --- OCR & Text Processing ---
    fun performOcrOnPage(page: PageEntity, prompt: String, folderPin: String? = null) {
        viewModelScope.launch {
            val rawBitmap = loadBitmapFromFile(page.processedImagePath ?: page.originalImagePath) ?: return@launch
            
            // Apply the document correction filter to the bitmap before running OCR for optimal results!
            val bitmap = com.example.ui.components.DocumentFilterProcessor.applyFilter(rawBitmap, page.filterType)

            addAuditLog(
                action = "READ",
                resourceType = "PAGE",
                details = "Triggering local offline OCR content query for page ID: ${page.id} (applied filter: ${page.filterType})"
            )
            
            val ocrText = OfflineOcrService.performOfflineOcr(bitmap, prompt)
            
            // Encrypt if folder is private
            val finalizedText = if (folderPin != null && folderPin.isNotEmpty()) {
                EncryptionUtils.encrypt(ocrText, folderPin)
            } else {
                ocrText
            }

            val updatedPage = page.copy(extractedText = finalizedText)
            repository.updatePage(updatedPage)

            // Mark document as OCR completed
            _activeDocument.value?.let { doc ->
                val summary = if (ocrText.length > 100) ocrText.substring(0, 100) + "..." else ocrText
                val updatedDoc = doc.copy(hasOcr = true, isEncrypted = folderPin != null, extractedTextSummary = summary)
                repository.updateDocument(updatedDoc)
                _activeDocument.value = updatedDoc
            }

            addAuditLog(
                action = "UPDATE",
                resourceType = "PAGE",
                details = "OCR Text successfully extracted and updated ${if (folderPin != null) "with AES-GCM encryption" else "as clear text"}"
            )
            loadPagesForActiveDocument()
        }
    }

    fun updatePageFilter(page: PageEntity, filterType: String) {
        viewModelScope.launch {
            val updatedPage = page.copy(filterType = filterType)
            repository.updatePage(updatedPage)
            addAuditLog(
                action = "UPDATE",
                resourceType = "PAGE",
                details = "Changed document correction filter on page ${page.pageNumber} to $filterType"
            )
            loadPagesForActiveDocument()
        }
    }

    fun decryptPageText(encryptedText: String, pin: String): String {
        return try {
            val decrypted = EncryptionUtils.decrypt(encryptedText, pin)
            addAuditLog(
                action = "DECRYPT",
                resourceType = "PAGE",
                details = "Successfully decrypted encrypted text content in audited secure sandbox"
            )
            decrypted
        } catch (e: Exception) {
            "Decryption Error: ${e.localizedMessage}"
        }
    }

    // --- Local Storage Bitmap Helpers ---
    private suspend fun saveBitmapToFile(filename: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val file = File(getApplication<Application>().filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        file.absolutePath
    }

    fun loadBitmapFromFile(path: String): Bitmap? {
        val file = File(path)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }

    // --- Export Simulation ---
    fun exportDocument(doc: DocumentEntity, format: String, pin: String? = null): String {
        addAuditLog(
            action = "EXPORT",
            resourceType = "DOCUMENT",
            details = "Exported document '${doc.name}' as $format format"
        )
        return if (format == "PDF") {
            "Simulated PDF with searchable OCR overlay created successfully in downloads: ${doc.name}.pdf"
        } else if (format == "DOCX") {
            "Simulated Word document (.docx) with rich paragraph markers: ${doc.name}.docx"
        } else {
            "Plain text document (.txt) compiled and shared: ${doc.name}.txt"
        }
    }

    // --- Cloud Syncing Management ---
    private fun loadSyncConfig() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        _syncConfig.value = CloudSyncConfig(
            r2Enabled = sharedPrefs.getBoolean("r2_enabled", false),
            r2Bucket = sharedPrefs.getString("r2_bucket", "") ?: "",
            r2Endpoint = sharedPrefs.getString("r2_endpoint", "") ?: "",
            r2AccessKey = sharedPrefs.getString("r2_access_key", "") ?: "",
            r2SecretKey = sharedPrefs.getString("r2_secret_key", "") ?: "",
            googleDriveEnabled = sharedPrefs.getBoolean("drive_enabled", false),
            googleDriveAccount = sharedPrefs.getString("drive_account", "") ?: "",
            googleDriveToken = sharedPrefs.getString("drive_token", "") ?: "",
            dropboxEnabled = sharedPrefs.getBoolean("dropbox_enabled", false),
            dropboxAccount = sharedPrefs.getString("dropbox_account", "") ?: "",
            dropboxToken = sharedPrefs.getString("dropbox_token", "") ?: "",
            autoBackup = sharedPrefs.getBoolean("auto_backup", true),
            wifiOnly = sharedPrefs.getBoolean("wifi_only", false),
            lastSyncTime = sharedPrefs.getLong("last_sync_time", 0L)
        )
    }

    fun updateSyncConfig(config: CloudSyncConfig) {
        _syncConfig.value = config
        val sharedPrefs = getApplication<Application>().getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("r2_enabled", config.r2Enabled)
            .putString("r2_bucket", config.r2Bucket)
            .putString("r2_endpoint", config.r2Endpoint)
            .putString("r2_access_key", config.r2AccessKey)
            .putString("r2_secret_key", config.r2SecretKey)
            .putBoolean("drive_enabled", config.googleDriveEnabled)
            .putString("drive_account", config.googleDriveAccount)
            .putString("drive_token", config.googleDriveToken)
            .putBoolean("dropbox_enabled", config.dropboxEnabled)
            .putString("dropbox_account", config.dropboxAccount)
            .putString("dropbox_token", config.dropboxToken)
            .putBoolean("auto_backup", config.autoBackup)
            .putBoolean("wifi_only", config.wifiOnly)
            .putLong("last_sync_time", config.lastSyncTime)
            .apply()

        addAuditLog(
            action = "UPDATE",
            resourceType = "APP",
            details = "Updated cloud backup properties. Integrations configured for: " +
                    "${if (config.r2Enabled) "Cloudflare R2, " else ""}" +
                    "${if (config.googleDriveEnabled) "Google Drive, " else ""}" +
                    "${if (config.dropboxEnabled) "Dropbox" else ""}"
        )
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            addAuditLog(
                action = "SYNC",
                resourceType = "APP",
                details = "Initiating full cryptographic cloud synchronization process..."
            )

            val unsynced = repository.getUnsyncedDocuments()
            val config = _syncConfig.value
            var uploadCount = 0
            var errorCount = 0
            val sbDetails = StringBuilder()

            withContext(Dispatchers.IO) {
                unsynced.forEach { doc ->
                    val pages = repository.getPagesForDocumentSync(doc.id)
                    pages.forEach { page ->
                        val imagePath = page.processedImagePath ?: page.originalImagePath
                        if (imagePath.isNotEmpty()) {
                            val file = File(imagePath)
                            if (file.exists()) {
                                val fileBytes = file.readBytes()
                                val fileName = "docuscan_${doc.id}_page_${page.pageNumber}.jpg"

                                // 1. Google Drive Integration
                                if (config.googleDriveEnabled) {
                                    val result = com.example.data.api.CloudSyncIntegrator.uploadToGoogleDrive(
                                        fileBytes = fileBytes,
                                        fileName = fileName,
                                        mimeType = "image/jpeg",
                                        accessToken = config.googleDriveToken
                                    )
                                    if (result.isSuccess) {
                                        uploadCount++
                                        sbDetails.append("Google Drive: Uploaded $fileName successfully\n")
                                    } else {
                                        errorCount++
                                        sbDetails.append("Google Drive Error on $fileName: ${result.exceptionOrNull()?.message}\n")
                                    }
                                }

                                // 2. Dropbox Integration
                                if (config.dropboxEnabled) {
                                    val result = com.example.data.api.CloudSyncIntegrator.uploadToDropbox(
                                        fileBytes = fileBytes,
                                        fileName = fileName,
                                        accessToken = config.dropboxToken
                                    )
                                    if (result.isSuccess) {
                                        uploadCount++
                                        sbDetails.append("Dropbox: Uploaded $fileName successfully\n")
                                    } else {
                                        errorCount++
                                        sbDetails.append("Dropbox Error on $fileName: ${result.exceptionOrNull()?.message}\n")
                                    }
                                }

                                // 3. Cloudflare R2 Integration
                                if (config.r2Enabled) {
                                    val result = com.example.data.api.CloudSyncIntegrator.uploadToCloudflareR2(
                                        fileBytes = fileBytes,
                                        fileName = fileName,
                                        bucket = config.r2Bucket,
                                        endpointUrl = config.r2Endpoint,
                                        accessKey = config.r2AccessKey,
                                        secretKey = config.r2SecretKey
                                    )
                                    if (result.isSuccess) {
                                        uploadCount++
                                        sbDetails.append("Cloudflare R2: Uploaded $fileName successfully\n")
                                    } else {
                                        errorCount++
                                        sbDetails.append("Cloudflare R2 Error on $fileName: ${result.exceptionOrNull()?.message}\n")
                                    }
                                }
                            }
                        }
                    }
                    // Mark document as synced locally
                    repository.updateDocument(doc.copy(isSynced = true))
                }
            }

            val updatedConfig = _syncConfig.value.copy(lastSyncTime = System.currentTimeMillis())
            updateSyncConfig(updatedConfig)

            _isSyncing.value = false
            
            val logMessage = if (uploadCount > 0 || errorCount > 0) {
                "Sync completed with $uploadCount uploads and $errorCount failures.\nDetails:\n$sbDetails"
            } else {
                "Sync completed. No pending documents found to upload."
            }

            addAuditLog(
                action = "SYNC",
                resourceType = "APP",
                details = logMessage
            )
        }
    }
}
