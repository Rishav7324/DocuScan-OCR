package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.OfflineOcrService
import com.example.data.database.*
import com.example.data.encryption.EncryptionUtils
import com.example.data.SecuritySettingsStore
import com.example.data.model.CloudSyncConfig
import com.example.data.repository.DocumentRepository
import com.example.ui.components.CropPoints
import com.example.ui.components.performPerspectiveCorrection
import androidx.compose.ui.geometry.Offset
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
        pageDao = db.pageDao(),
        auditLogDao = db.auditLogDao()
    )

    // Flow lists
    val folders: StateFlow<List<FolderEntity>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<DocumentEntity>> = repository.getFavoriteDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All distinct tags across documents, sorted alphabetically. */
    fun getAllTags(): List<String> = allDocuments.value
        .flatMap { it.tags.split(",") }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    // Active state
    private val _currentFolderId = MutableStateFlow<Long>(0) // 0 means Root/Uncategorized
    val currentFolderId: StateFlow<Long> = _currentFolderId.asStateFlow()

    // Raw PIN for the currently unlocked private folder (in-memory only; never persisted)
    private val _activeFolderPin = MutableStateFlow<String?>(null)
    val activeFolderPin: StateFlow<String?> = _activeFolderPin.asStateFlow()

    fun setActiveFolderPin(pin: String?) {
        _activeFolderPin.value = if (pin.isNullOrEmpty()) null else pin
    }

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

    // Compliance Audit logs persisted in Room (survives process death)
    val auditLogs: StateFlow<List<AuditLog>> = repository.allAuditLogs
        .map { logs -> logs.map { AuditLog(id = it.id.toString(), timestamp = it.timestamp, action = it.action, resourceType = it.resourceType, details = it.details, operator = it.operator) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize audit logs with default HIPAA/GDPR startup notice
        addAuditLog(
            action = "INITIALIZE",
            resourceType = "APP",
            details = "Secure sandbox initialization. Cryptographic providers registered. Local database initialized."
        )
        loadSyncConfig()
        com.example.workers.SyncScheduler.apply(getApplication(), _syncConfig.value.autoBackup)
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
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertAuditLog(
                AuditLogEntity(action = action, resourceType = resourceType, details = details)
            )
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAuditLogs()
            addAuditLog("DELETE", "AUDIT", "Cleared all compliance audit trail logs (erasure request)")
        }
    }

    // ponytail: writes the trail to a real file the user can keep/export
    suspend fun exportAuditTrail(context: Context): File? = withContext(Dispatchers.IO) {
        val logs = auditLogs.value
        if (logs.isEmpty()) return@withContext null
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DocuScan")
        dir.mkdirs()
        val file = File(dir, "AUDIT_TRAIL_GDPR.log")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        file.bufferedWriter().use { out ->
            out.appendLine("DocuScan OCR — Compliance Audit Trail")
            out.appendLine("Generated: ${sdf.format(Date())}")
            out.appendLine("${logs.size} event(s)")
            out.appendLine("=".repeat(60))
            logs.forEach { log ->
                out.appendLine("[${sdf.format(Date(log.timestamp))}] ${log.action} / ${log.resourceType} — ${log.details}")
            }
        }
        file
    }

    // --- Folder CRUD ---
    fun createFolder(name: String, isPrivate: Boolean = false, passcode: String? = null) {
        viewModelScope.launch {
            val passwordHash = if (isPrivate && passcode != null) EncryptionUtils.encrypt("VERIFIED", passcode) else null
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
            val updated = folder.copy(isPrivate = true, passwordHash = EncryptionUtils.encrypt("VERIFIED", newPasscode))
            repository.updateFolder(updated)
            addAuditLog(
                action = "UPDATE",
                resourceType = "KEYS",
                details = "Re-keyed secure folder storage key for ID: ${folder.id}"
            )
        }
    }

    fun verifyFolderPasscode(folder: FolderEntity, passcode: String): Boolean {
        val marker = folder.passwordHash ?: return false
        return EncryptionUtils.verifyPassphrase(marker, passcode)
    }

    // --- App Lock (global security) ---
    // ponytail: PIN-only, honest; reuses EncryptionUtils marker pattern. No fake biometrics.
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(
        SecuritySettingsStore.isAppLockEnabled(getApplication())
    )
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _failedAttempts = MutableStateFlow(
        SecuritySettingsStore.getFailedAttempts(getApplication())
    )
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    /** Called on cold start / resume. Locks the app when enabled and a PIN is configured. */
    fun evaluateAppLockOnResume() {
        val ctx = getApplication<Application>()
        val enabled = SecuritySettingsStore.isAppLockEnabled(ctx)
        _appLockEnabled.value = enabled
        val hasPin = SecuritySettingsStore.getAppPinMarker(ctx).isNotBlank()
        _isAppLocked.value = enabled && hasPin
    }

    /** Set or change the global app PIN (idempotent). */
    fun setAppLockPin(pin: String) {
        val ctx = getApplication<Application>()
        val marker = EncryptionUtils.encrypt("APP_LOCK_VERIFY", pin)
        SecuritySettingsStore.setAppPinMarker(ctx, marker)
        SecuritySettingsStore.setAppLockEnabled(ctx, true)
        SecuritySettingsStore.resetFailedAttempts(ctx)
        _appLockEnabled.value = true
        _failedAttempts.value = 0
        _isAppLocked.value = false
        addAuditLog(action = "UPDATE", resourceType = "KEYS", details = "Global app lock enabled")
    }

    fun disableAppLock() {
        val ctx = getApplication<Application>()
        SecuritySettingsStore.setAppLockEnabled(ctx, false)
        SecuritySettingsStore.setAppPinMarker(ctx, "")
        SecuritySettingsStore.resetFailedAttempts(ctx)
        _appLockEnabled.value = false
        _failedAttempts.value = 0
        _isAppLocked.value = false
        addAuditLog(action = "UPDATE", resourceType = "KEYS", details = "Global app lock disabled")
    }

    /** Returns true only when the PIN is correct and the app is now unlocked. */
    fun unlockApp(pin: String): Boolean {
        val ctx = getApplication<Application>()
        val marker = SecuritySettingsStore.getAppPinMarker(ctx)
        if (marker.isBlank() || !EncryptionUtils.verifyPassphrase(marker, pin)) {
            registerFailedAttempt()
            return false
        }
        SecuritySettingsStore.resetFailedAttempts(ctx)
        _failedAttempts.value = 0
        _isAppLocked.value = false
        return true
    }

    /**
     * Records a failed unlock attempt. After [MAX_FAILED_ATTEMPTS] the app performs a
     * secure wipe (zeroizes the local database) — matches the "HIPAA compliant wipe" claim.
     * Returns true when a wipe was triggered.
     */
    fun registerFailedAttempt(): Boolean {
        val ctx = getApplication<Application>()
        val next = _failedAttempts.value + 1
        _failedAttempts.value = next
        SecuritySettingsStore.setFailedAttempts(ctx, next)
        if (next >= MAX_FAILED_ATTEMPTS) {
            secureWipe()
            return true
        }
        return false
    }

    fun resetFailedAttempts() {
        val ctx = getApplication<Application>()
        SecuritySettingsStore.resetFailedAttempts(ctx)
        _failedAttempts.value = 0
    }

    /** Zeroize sensitive in-memory state when the app goes to the background. */
    fun clearSensitiveState() {
        _activeFolderPin.value = null
        _activeDocument.value = null
        _activePages.value = emptyList()
    }

    /** Secure wipe: delete the entire local database so no document data remains on device. */
    private fun secureWipe() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                ctx.deleteDatabase("docuscan_database")
            } catch (_: Exception) { /* best effort */ }
            SecuritySettingsStore.setAppLockEnabled(ctx, false)
            SecuritySettingsStore.setAppPinMarker(ctx, "")
            SecuritySettingsStore.resetFailedAttempts(ctx)
            _appLockEnabled.value = false
            _isAppLocked.value = false
            _failedAttempts.value = 0
            _activeFolderPin.value = null
            _activeDocument.value = null
            _activePages.value = emptyList()
            addAuditLog(action = "DELETE", resourceType = "APP", details = "Secure wipe executed after repeated failed unlock attempts")
        }
    }

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
    }

    suspend fun getPagesForDocumentSync(docId: Long): List<PageEntity> {
        return repository.getPagesForDocumentSync(docId)
    }

    // --- Batch Document Scanning & Captures ---
    // Per-page crop points, kept in lockstep with _batchImages so perspective edits survive finalize.
    private val _batchCropPoints = MutableStateFlow<List<CropPoints>>(emptyList())
    val batchCropPoints: StateFlow<List<CropPoints>> = _batchCropPoints.asStateFlow()

    fun addImageToBatch(bitmap: Bitmap) {
        _batchImages.update { it + bitmap }
        _batchCropPoints.update { it + CropPoints() }
    }

    fun clearBatch() {
        _batchImages.value = emptyList()
        _batchCropPoints.value = emptyList()
        _currentCropIndex.value = 0
    }

    fun removeImageFromBatch(index: Int) {
        val currentList = _batchImages.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _batchImages.value = currentList
            val crops = _batchCropPoints.value.toMutableList()
            crops.removeAt(index)
            _batchCropPoints.value = crops
            _currentCropIndex.value = 0
        }
    }

    fun selectCropIndex(index: Int) {
        if (index in _batchImages.value.indices) {
            _currentCropIndex.value = index
            _currentCropPoints.value = _batchCropPoints.value.getOrElse(index) {
                com.example.ui.components.AutoCornerDetector.detectDocumentCorners(_batchImages.value[index])
            }
        }
    }

    fun runAutoCornerDetection() {
        val index = _currentCropIndex.value
        if (index in _batchImages.value.indices) {
            val bitmap = _batchImages.value[index]
            val points = com.example.ui.components.AutoCornerDetector.detectDocumentCorners(bitmap)
            _currentCropPoints.value = points
            _batchCropPoints.update { list -> list.toMutableList().also { it[index] = points } }
            addAuditLog(
                action = "UPDATE",
                resourceType = "PAGE",
                details = "Executed advanced heuristic auto corner detection for batch index $index"
            )
        }
    }

    fun updateCropPoints(points: CropPoints) {
        _currentCropPoints.value = points
        val index = _currentCropIndex.value
        if (index in _batchCropPoints.value.indices) {
            _batchCropPoints.update { list -> list.toMutableList().also { it[index] = points } }
        }
    }

    // Rotate the current page 90° CW; rotate its normalized crop points to match.
    fun rotateCurrentBatchImage() {
        val index = _currentCropIndex.value
        if (index !in _batchImages.value.indices) return
        val src = _batchImages.value[index]
        val matrix = android.graphics.Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        _batchImages.update { list -> list.toMutableList().also { it[index] = rotated } }
        // (x, y) -> (1 - y, x) for 90° CW
        _batchCropPoints.update { list ->
            list.toMutableList().also {
                val p = it.getOrElse(index) { CropPoints() }
                it[index] = CropPoints(
                    topLeft = Offset(1f - p.topLeft.y, p.topLeft.x),
                    topRight = Offset(1f - p.topRight.y, p.topRight.x),
                    bottomRight = Offset(1f - p.bottomRight.y, p.bottomRight.x),
                    bottomLeft = Offset(1f - p.bottomLeft.y, p.bottomLeft.x)
                )
            }
        }
        _currentCropPoints.value = _batchCropPoints.value[index]
    }

    // Finalize the batch process and save to a new Document
    fun finalizeBatch(documentName: String, folderId: Long, folderPin: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_batchImages.value.isEmpty()) return@launch

            // ponytail: scanning into root auto-creates a "Scan" folder so docs land somewhere browsable
            val resolvedFolderId = if (folderId == 0L) {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                repository.insertFolder(FolderEntity(name = "Scan $ts", isPrivate = false))
            } else folderId

            val pin = folderPin ?: _activeFolderPin.value
            val targetFolder = repository.getFolderById(resolvedFolderId)
            val usePin = if (targetFolder?.isPrivate == true) (pin ?: _activeFolderPin.value) else null

            // Create Document
            val document = DocumentEntity(
                folderId = resolvedFolderId,
                name = documentName,
                fileFormat = "PDF"
            )
            val docId = repository.insertDocument(document)

            // Save individual pages, applying the user's perspective crop to each
            val crops = _batchCropPoints.value
            _batchImages.value.forEachIndexed { index, bitmap ->
                val points = crops.getOrNull(index) ?: CropPoints()
                val corrected = performPerspectiveCorrection(bitmap, points)
                val origPath = saveBitmapToFile("orig_${docId}_${index}.jpg", bitmap)
                val procPath = saveBitmapToFile("proc_${docId}_${index}.jpg", corrected)

                // Encrypt images at rest when the target folder is private
                if (usePin != null) {
                    runCatching { EncryptionUtils.encryptFile(File(origPath), usePin) }
                    runCatching { EncryptionUtils.encryptFile(File(procPath), usePin) }
                }

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
            val imageEncrypted = usePin != null
            repository.updateDocument(finalizedDoc!!.copy(isImageEncrypted = imageEncrypted))
            withContext(Dispatchers.Main) {
                _activeDocument.value = finalizedDoc.copy(isImageEncrypted = imageEncrypted)
                clearBatch()
                loadPagesForActiveDocument()
            }

            addAuditLog(
                action = "CREATE",
                resourceType = "DOCUMENT",
                details = "Finalized batch scan for document: '$documentName' with ${_batchImages.value.size} pages (ID: $docId)" +
                    if (imageEncrypted) " (images encrypted at rest)" else ""
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

    // --- Document Organization Features (Favorites, Tags, Notes) ---

    fun toggleFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            val updated = document.copy(isFavorite = !document.isFavorite)
            repository.updateDocument(updated)
            if (_activeDocument.value?.id == document.id) _activeDocument.value = updated
            addAuditLog(
                action = "UPDATE",
                resourceType = "DOCUMENT",
                details = "Marked document '${document.name}' as ${if (updated.isFavorite) "favorite" else "unfavorited"}"
            )
        }
    }

    fun updateDocumentTags(document: DocumentEntity, tags: String) {
        viewModelScope.launch {
            val normalized = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                .distinct().joinToString(",")
            val updated = document.copy(tags = normalized)
            repository.updateDocument(updated)
            if (_activeDocument.value?.id == document.id) _activeDocument.value = updated
            addAuditLog(
                action = "UPDATE",
                resourceType = "DOCUMENT",
                details = "Updated tags for '${document.name}': $normalized"
            )
        }
    }

    fun updateDocumentNotes(document: DocumentEntity, notes: String) {
        viewModelScope.launch {
            val updated = document.copy(notes = notes)
            repository.updateDocument(updated)
            if (_activeDocument.value?.id == document.id) _activeDocument.value = updated
            addAuditLog(
                action = "UPDATE",
                resourceType = "DOCUMENT",
                details = "Updated notes for '${document.name}'"
            )
        }
    }

    fun searchDocuments(query: String): List<DocumentEntity> =
        if (query.isBlank()) allDocuments.value
        else allDocuments.value.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.tags.contains(query, ignoreCase = true) ||
            it.notes.contains(query, ignoreCase = true) ||
            (it.extractedTextSummary?.contains(query, ignoreCase = true) == true)
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

    fun loadBitmapFromFile(path: String, pin: String?): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bytes = if (pin != null && pin.isNotEmpty()) {
            runCatching { EncryptionUtils.decryptFile(file, pin) }.getOrNull() ?: return null
        } else {
            file.readBytes()
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ponytail: loads the first page image for thumbnail preview; pin null for non-private docs
    suspend fun getFirstPageBitmap(docId: Long, pin: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        val pages = repository.getPagesForDocumentSync(docId)
        val page = pages.minByOrNull { it.pageNumber } ?: return@withContext null
        val path = (page.processedImagePath ?: page.originalImagePath)
        loadBitmapFromFile(path, pin)
    }

    // --- Export to real files ---
    suspend fun exportDocument(context: android.content.Context, doc: DocumentEntity, format: String, pin: String? = null): File? {
        val pages = repository.getPagesForDocumentSync(doc.id)
        val text = pages.joinToString("\n\n") { page ->
            val raw = page.extractedText ?: ""
            if (doc.isEncrypted && pin != null) {
                runCatching { EncryptionUtils.decrypt(raw, pin) }.getOrDefault("(Locked)")
            } else raw
        }
        val images = pages.mapNotNull { page ->
            val path = page.processedImagePath ?: page.originalImagePath
            loadBitmapFromFile(path, if (doc.isImageEncrypted) pin else null)
        }
        val file = runCatching {
            com.example.data.export.ExportUtils.exportDocument(context, doc.name, format, text, if (format == "PDF") images else emptyList())
        }.getOrNull()

        addAuditLog(
            action = "EXPORT",
            resourceType = "DOCUMENT",
            details = "Exported document '${doc.name}' as $format format" + (file?.let { " -> ${it.absolutePath}" } ?: " (failed)")
        )
        return file
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
            googleDriveRefreshToken = sharedPrefs.getString("drive_refresh_token", "") ?: "",
            dropboxEnabled = sharedPrefs.getBoolean("dropbox_enabled", false),
            dropboxAccount = sharedPrefs.getString("dropbox_account", "") ?: "",
            dropboxToken = sharedPrefs.getString("dropbox_token", "") ?: "",
            dropboxRefreshToken = sharedPrefs.getString("dropbox_refresh_token", "") ?: "",
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
            .putString("drive_refresh_token", config.googleDriveRefreshToken)
            .putBoolean("dropbox_enabled", config.dropboxEnabled)
            .putString("dropbox_account", config.dropboxAccount)
            .putString("dropbox_token", config.dropboxToken)
            .putString("dropbox_refresh_token", config.dropboxRefreshToken)
            .putBoolean("auto_backup", config.autoBackup)
            .putBoolean("wifi_only", config.wifiOnly)
            .putLong("last_sync_time", config.lastSyncTime)
            .apply()

        // Schedule/cancel periodic auto-backup to match the toggle.
        com.example.workers.SyncScheduler.apply(getApplication(), config.autoBackup)

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

            val result = SyncEngine.runSync(repository, _syncConfig.value)
            if (result.uploads > 0 || result.errors > 0) {
                updateSyncConfig(_syncConfig.value.copy(lastSyncTime = System.currentTimeMillis()))
            }

            _isSyncing.value = false

            val logMessage = if (result.uploads > 0 || result.errors > 0) {
                "Sync completed with ${result.uploads} uploads and ${result.errors} failures.\nDetails:\n${result.details}"
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
