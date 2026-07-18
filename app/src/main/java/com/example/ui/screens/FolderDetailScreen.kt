package com.example.ui.screens

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.example.data.encryption.EncryptionUtils
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.DocumentEntity
import com.example.data.database.FolderEntity
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.SetPasscodeDialog
import com.example.ui.components.BiometricLockDialog
import com.example.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: ScannerViewModel,
    folderId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToOcr: (String?) -> Unit // Pass the folder pin if it is private
) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()

    val currentFolder = remember(folders, folderId) {
        folders.find { it.id == folderId }
    }

    val folderDocs = remember(allDocs, folderId) {
        allDocs.filter { it.folderId == folderId }
    }

    var showSetPasscode by remember { mutableStateOf(false) }
    val activeFolderPin by viewModel.activeFolderPin.collectAsState()
    // Prompt for the PIN once when opening a private folder, so the key is available for new scans/exports
    var showUnlock by remember {
        mutableStateOf(currentFolder?.isPrivate == true && activeFolderPin == null)
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(currentFolder?.name ?: "Folder Details", fontWeight = FontWeight.ExtraBold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("folder_detail_back_button")) {
                            Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black
                    ),
                    actions = {
                        // Privacy toggle button
                        if (currentFolder != null) {
                            IconButton(
                                onClick = {
                                    if (currentFolder.isPrivate) {
                                        // Remove privacy
                                        coroutineScope.launch {
                                            viewModel.updateFolderPasscode(currentFolder.copy(isPrivate = false, passwordHash = null), "")
                                            viewModel.addAuditLog("UPDATE", "FOLDER", "Removed secure locked status from folder '${currentFolder.name}'")
                                        }
                                    } else {
                                        showSetPasscode = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (currentFolder.isPrivate) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Toggle privacy lock",
                                    tint = if (currentFolder.isPrivate) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { 
                                viewModel.deleteFolder(currentFolder)
                                onNavigateBack()
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Folder", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCapture,
                    icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Scan Document", tint = Color.Black) },
                    text = { Text("Scan/Import", fontWeight = FontWeight.Bold, color = Color.Black) },
                    modifier = Modifier.testTag("scan_doc_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (currentFolder != null) {
                    if (folderDocs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty folder",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "This folder is empty",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap the 'Scan/Import' button to capture paper files or upload images from your library into this folder.",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Folder overview statistics
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                cornerRadius = 20.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Folder Status", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, color = Color.Black)
                                        Text("${folderDocs.size} Documents saved offline", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    }
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(if (currentFolder.isPrivate) "🔒 Private AES" else "🔓 Standard Public", fontWeight = FontWeight.Bold, color = Color.Black) }
                                    )
                                }
                            }

                            // Documents vertical grid list
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(folderDocs) { doc ->
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn() + scaleIn(initialScale = 0.9f)
                                    ) {
                                        GlassCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("doc_item_card_${doc.id}"),
                                            cornerRadius = 20.dp,
                                            onClick = {
                                                viewModel.loadActiveDocument(doc)
                                                onNavigateToOcr(viewModel.activeFolderPin.value)
                                            }
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                // Doc thumbnail icon
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(110.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (doc.fileFormat == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Article,
                                                        contentDescription = "Doc Preview",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    if (doc.isSynced) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.CloudDone,
                                                                contentDescription = "Synced",
                                                                tint = Color(0xFF10B981),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Name and creation date
                                                Text(
                                                    text = doc.name,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = "Created: ${sdf.format(Date(doc.createdAt))}",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    fontWeight = FontWeight.Medium
                                                )

                                                Spacer(modifier = Modifier.height(6.dp))

                                                // OCR Indicator and Quick Share Row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (doc.hasOcr) Icons.Default.AutoAwesome else Icons.Default.HourglassEmpty,
                                                            contentDescription = "OCR status",
                                                            tint = if (doc.hasOcr) MaterialTheme.colorScheme.primary else Color.Gray,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = if (doc.hasOcr) "OCR" else "No OCR",
                                                            fontSize = 10.sp,
                                                            color = if (doc.hasOcr) MaterialTheme.colorScheme.primary else Color.Gray,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                        coroutineScope.launch {
                                                            val pages = viewModel.getPagesForDocumentSync(doc.id)
                                                            val folderPin = viewModel.activeFolderPin.value
                                                            val decryptedText = pages.map { page ->
                                                                val rawText = page.extractedText ?: ""
                                                                if (doc.isEncrypted && folderPin != null && folderPin.isNotEmpty()) {
                                                                    try {
                                                                        EncryptionUtils.decrypt(rawText, folderPin)
                                                                    } catch (e: Exception) {
                                                                        "(Locked PHI Data)"
                                                                    }
                                                                } else {
                                                                    rawText
                                                                }
                                                            }.filter { it.isNotBlank() }.joinToString("\n\n")

                                                                val shareMsg = """
                                                                    📄 DOCUMENT: ${doc.name}
                                                                    📅 CREATED: ${sdf.format(Date(doc.createdAt))}
                                                                    🔒 SECURITY: ${if (doc.isEncrypted) "AES Protected PHI" else "Offline local"}
                                                                    ------------------------------------------------------------
                                                                    ${if (decryptedText.isNotBlank()) decryptedText else "No text extracted yet."}
                                                                """.trimIndent()

                                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                    type = "text/plain"
                                                                    putExtra(Intent.EXTRA_SUBJECT, doc.name)
                                                                    putExtra(Intent.EXTRA_TEXT, shareMsg)
                                                                }
                                                                context.startActivity(Intent.createChooser(shareIntent, "Quick Share Document"))
                                                                viewModel.addAuditLog("READ", "DOCUMENT", "Quick shared document '${doc.name}' OCR text")
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp).testTag("quick_share_doc_${doc.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Share,
                                                            contentDescription = "Quick Share",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Folder properties not found.", color = Color(0xFF64748B))
                    }
                }

                // Set secure password modal dialog
                if (showSetPasscode && currentFolder != null) {
                    SetPasscodeDialog(
                        onConfirm = { code ->
                            viewModel.updateFolderPasscode(currentFolder, code)
                            viewModel.setActiveFolderPin(code)
                            showSetPasscode = false
                            viewModel.addAuditLog("UPDATE", "FOLDER", "Reconfigured folder '${currentFolder.name}' to Secure Mode (PIN Set)")
                        },
                        onDismiss = { showSetPasscode = false }
                    )
                }

                // Unlock private folder on entry
                if (showUnlock && currentFolder != null) {
                    BiometricLockDialog(
                        verify = { viewModel.verifyFolderPasscode(currentFolder, it) },
                        onSuccess = { entered ->
                            viewModel.setActiveFolderPin(entered)
                            showUnlock = false
                        },
                        onDismiss = { onNavigateBack() }
                    )
                }
            }
        }
    }
}
