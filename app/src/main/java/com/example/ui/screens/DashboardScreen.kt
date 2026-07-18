package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import com.example.data.encryption.EncryptionUtils
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.DocumentEntity
import com.example.data.database.FolderEntity
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.viewmodel.ScannerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ScannerViewModel,
    onNavigateToFolder: (Long) -> Unit,
    onNavigateToCapture: (Long) -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToCompliance: () -> Unit,
    onNavigateToOcr: (String?) -> Unit,
    onNavigateToHelpAndLegal: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()
    val activeFolderPin by viewModel.activeFolderPin.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var isNewFolderPrivate by remember { mutableStateOf(false) }
    var newFolderPasscode by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = remember(allDocs) { viewModel.getAllTags() }

    val filteredDocs = remember(allDocs, searchQuery, showFavoritesOnly, selectedTag) {
        allDocs.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                doc.name.contains(searchQuery, ignoreCase = true) ||
                doc.tags.contains(searchQuery, ignoreCase = true) ||
                doc.notes.contains(searchQuery, ignoreCase = true) ||
                (doc.extractedTextSummary?.contains(searchQuery, ignoreCase = true) == true)
            val matchesFav = !showFavoritesOnly || doc.isFavorite
            val matchesTag = selectedTag == null || doc.tags.split(",").map { it.trim() }.contains(selectedTag)
            matchesQuery && matchesFav && matchesTag
        }
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("DocuScan OCR Sandbox", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = onNavigateToHelpAndLegal, modifier = Modifier.testTag("help_legal_nav_button")) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Help & Legal", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNavigateToCompliance, modifier = Modifier.testTag("compliance_nav_button")) {
                            Icon(imageVector = Icons.Default.HealthAndSafety, contentDescription = "Compliance", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNavigateToSync, modifier = Modifier.testTag("sync_nav_button")) {
                            Icon(imageVector = Icons.Default.CloudSync, contentDescription = "Cloud Syncer", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onNavigateToCapture(0L) }, // 0L is Root Folder
                    modifier = Modifier.testTag("dashboard_scan_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = "Scan Document")
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Hero Section (Sage Green solid minimalist card)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Advanced Mobile OCR Engine",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Capture physical files, extract structured text offline, and secure documents under HIPAA standards.",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search scanned files...", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_document_input")
                    )
                }

                // Filter row: favorites toggle + tag chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = showFavoritesOnly,
                                onClick = { showFavoritesOnly = !showFavoritesOnly },
                                label = { Text("★ Favorites") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showFavoritesOnly) Icons.Default.Star else Icons.Default.StarOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("favorites_filter_toggle")
                            )
                            if (selectedTag != null) {
                                AssistChip(
                                    onClick = { selectedTag = null },
                                    label = { Text("Tag: $selectedTag ✕") },
                                    modifier = Modifier.testTag("clear_tag_filter")
                                )
                            }
                        }
                        if (allTags.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allTags) { tag ->
                                    FilterChip(
                                        selected = selectedTag == tag,
                                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                        label = { Text("#$tag") },
                                        modifier = Modifier.testTag("tag_filter_$tag")
                                    )
                                }
                            }
                        }
                    }
                }

                // Folders Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Folder Vaults",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                        TextButton(
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier.testTag("create_folder_button"),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Folder", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Folders Horizontal List
                item {
                    if (folders.isEmpty()) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            cornerRadius = 20.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No custom folder vaults created yet.", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders) { folder ->
                                val docCount = allDocs.count { it.folderId == folder.id }
                                GlassCard(
                                    modifier = Modifier
                                        .width(155.dp)
                                        .height(125.dp)
                                        .testTag("folder_card_${folder.id}"),
                                    cornerRadius = 20.dp,
                                    onClick = { onNavigateToFolder(folder.id) }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = if (folder.isPrivate) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                                contentDescription = "Folder",
                                                tint = if (folder.isPrivate) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            if (folder.isPrivate) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Locked",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = folder.name,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = Color.Black
                                            )
                                            Text(
                                                text = "$docCount files",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent Documents Header
                item {
                    Text(
                        text = "Recent Scanned Documents",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black
                    )
                }

                // Recent Documents List
                if (filteredDocs.isEmpty()) {
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            cornerRadius = 20.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.PostAdd, contentDescription = "No Docs", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No scanned documents found", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Capture your first batch using the scan button.", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                } else {
                    items(filteredDocs) { doc ->
                        val folderOfDoc = folders.find { it.id == doc.folderId }

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("doc_list_item_${doc.id}"),
                                cornerRadius = 20.dp,
                                onClick = {
                                    viewModel.loadActiveDocument(doc)
                                    onNavigateToOcr(folderOfDoc?.let { if (it.isPrivate) viewModel.activeFolderPin.value else null })
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Real thumbnail of the first scanned page
                                    val folderPin = remember(folderOfDoc) {
                                        if (folderOfDoc?.isPrivate == true) viewModel.activeFolderPin.value else null
                                    }
                                    var thumb by remember { mutableStateOf<Bitmap?>(null) }
                                    LaunchedEffect(doc.id) {
                                        thumb = viewModel.getFirstPageBitmap(doc.id, folderPin)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumb != null) {
                                            androidx.compose.foundation.Image(
                                                bitmap = thumb!!.asImageBitmap(),
                                                contentDescription = "Preview",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (doc.fileFormat == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Article,
                                                contentDescription = "Doc",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    // Meta details
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = doc.name,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.Black
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = sdf.format(Date(doc.createdAt)),
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (folderOfDoc != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFFF1F5F9))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = folderOfDoc.name,
                                                        fontSize = 10.sp,
                                                        color = Color.Black,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        val docTags = doc.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                        if (docTags.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                docTags.take(3).forEach { tag ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "#$tag",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Right status badges / actions
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.toggleFavorite(doc) },
                                            modifier = Modifier.size(24.dp).testTag("favorite_toggle_${doc.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                                                contentDescription = if (doc.isFavorite) "Remove from favorites" else "Add to favorites",
                                                tint = if (doc.isFavorite) Color(0xFFFFB020) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (doc.isSynced) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = "Synced",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (doc.hasOcr) {
                                            Icon(
                                                imageVector = Icons.Default.TextSnippet,
                                                contentDescription = "Ocr Present",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val pages = viewModel.getPagesForDocumentSync(doc.id)
                                                    val folderPin = folderOfDoc?.let { if (it.isPrivate) viewModel.activeFolderPin.value else null }

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
                                                    viewModel.addAuditLog("READ", "DOCUMENT", "Quick shared document '${doc.name}' OCR text from dashboard")
                                                }
                                            },
                                            modifier = Modifier.size(24.dp).testTag("quick_share_doc_${doc.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Quick Share",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteDocument(doc) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Create Folder Dialog
            if (showCreateFolderDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateFolderDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newFolderName.isNotEmpty()) {
                                    val code = if (isNewFolderPrivate) newFolderPasscode else null
                                    viewModel.createFolder(newFolderName, isNewFolderPrivate, code)
                                    showCreateFolderDialog = false
                                    newFolderName = ""
                                    isNewFolderPrivate = false
                                    newFolderPasscode = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("confirm_create_folder")
                        ) {
                            Text("Create", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCreateFolderDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                    },
                    title = { Text("Create New Folder", color = Color.Black, fontWeight = FontWeight.Black) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = newFolderName,
                                onValueChange = { newFolderName = it },
                                label = { Text("Folder Name", color = Color.Black) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("folder_name_input")
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isNewFolderPrivate,
                                    onCheckedChange = { isNewFolderPrivate = it },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.testTag("private_folder_checkbox")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Secure Folder Vault", fontWeight = FontWeight.Black, color = Color.Black)
                                    Text("Encrypt files offline using 4-digit PIN", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontWeight = FontWeight.Medium)
                                }
                            }

                            AnimatedVisibility(visible = isNewFolderPrivate) {
                                OutlinedTextField(
                                    value = newFolderPasscode,
                                    onValueChange = { if (it.length <= 4) newFolderPasscode = it },
                                    label = { Text("4-digit Security PIN/Code", color = Color.Black) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black
                                    ),
                                    placeholder = { Text("e.g. 1234") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("folder_pin_input")
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
}

