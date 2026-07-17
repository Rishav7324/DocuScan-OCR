package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BiometricLockDialog
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrExportScreen(
    viewModel: ScannerViewModel,
    folderPin: String?, // Pass PIN if inside a secure folder
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val activeDoc by viewModel.activeDocument.collectAsState()
    val activePages by viewModel.activePages.collectAsState()

    var customOcrPrompt by remember { mutableStateOf("Perform highly accurate OCR on this document image. Extract all text exactly as written, preserving paragraph breaks.") }
    var selectedPageIdForOcr by remember { mutableStateOf<Long?>(null) }
    var isOcrRunning by remember { mutableStateOf(false) }

    // Decrypted cache for secure folder viewing
    var folderDecryptionPin by remember { mutableStateOf("") }
    var showSecureFolderUnlock by remember { mutableStateOf(folderPin != null && folderPin.isNotEmpty()) }
    var hasDecryptedValue by remember { mutableStateOf(folderPin == null || folderPin.isEmpty()) }

    var editingTextMap = remember { mutableStateMapOf<Long, String>() }

    // Initialize editing values
    LaunchedEffect(activePages) {
        activePages.forEach { page ->
            if (page.extractedText != null && !editingTextMap.containsKey(page.id)) {
                if (folderPin == null || folderPin.isEmpty()) {
                    editingTextMap[page.id] = page.extractedText
                } else if (hasDecryptedValue) {
                    val dec = viewModel.decryptPageText(page.extractedText, folderDecryptionPin)
                    editingTextMap[page.id] = dec
                }
            }
        }
    }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(activeDoc?.name ?: "Document Details", fontWeight = FontWeight.ExtraBold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("ocr_back_button")) {
                            Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black
                    ),
                    actions = {
                        IconButton(onClick = onNavigateToHome) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = "Go Home", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (activeDoc != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Document Header Info
                        item {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 20.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = activeDoc!!.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                    // Security Badge
                                    if (folderPin != null && folderPin.isNotEmpty()) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("AES-256 Secured", fontWeight = FontWeight.Bold) },
                                            icon = { Icon(Icons.Default.Lock, contentDescription = "Private", modifier = Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Format: ${activeDoc!!.fileFormat}  •  Pages: ${activePages.size}  •  OCR: ${if (activeDoc!!.hasOcr) "Complete" else "Pending"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Export Formats Row
                        item {
                            Text("Export & Share Document", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "PDF" to Icons.Default.PictureAsPdf,
                                    "DOCX" to Icons.Default.Article,
                                    "TXT" to Icons.Default.Notes
                                ).forEach { (format, icon) ->
                                    Button(
                                        onClick = {
                                            val exportMsg = viewModel.exportDocument(activeDoc!!, format, folderPin)
                                            // Trigger share intent
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, activeDoc!!.name)
                                                putExtra(Intent.EXTRA_TEXT, "$exportMsg\n\nContent:\n" + activePages.map { p -> 
                                                    editingTextMap[p.id] ?: "(Unextracted Text)"
                                                }.joinToString("\n\n"))
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Scanned Document"))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = icon, contentDescription = format, modifier = Modifier.size(18.dp), tint = Color.Black)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(format, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Document Custom Prompt Config
                        item {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 20.dp
                            ) {
                                Text("Advanced OCR Prompt (Gemini AI)", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, color = Color.Black)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customOcrPrompt,
                                    onValueChange = { customOcrPrompt = it },
                                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Pages List
                        item {
                            Text("Scanned Pages", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                        }

                        items(activePages) { page ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + expandVertically()
                            ) {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 20.dp
                                ) {
                                    Text("Page ${page.pageNumber}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, color = Color.Black)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Cropped/Warped Image Preview with live Filter Correction
                                        Box(
                                            modifier = Modifier
                                                .size(110.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                                .background(Color(0xFFF1F5F9))
                                        ) {
                                            val rawBitmap = page.processedImagePath?.let { viewModel.loadBitmapFromFile(it) } 
                                                ?: viewModel.loadBitmapFromFile(page.originalImagePath)

                                            val bitmap = remember(rawBitmap, page.filterType) {
                                                rawBitmap?.let { com.example.ui.components.DocumentFilterProcessor.applyFilter(it, page.filterType) }
                                            }

                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "Page Warp (with ${page.filterType} correction)",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(imageVector = Icons.Default.BrokenImage, contentDescription = "Load Error", tint = Color.Gray)
                                                }
                                            }
                                        }

                                        // Extraction Status or Extracted Text box
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (page.extractedText == null) {
                                                Text(
                                                    "Text has not been extracted yet. Select below to run advanced Gemini API OCR.",
                                                    color = Color.Gray,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = {
                                                        selectedPageIdForOcr = page.id
                                                        isOcrRunning = true
                                                        viewModel.performOcrOnPage(page, customOcrPrompt, folderPin)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.testTag("extract_ocr_button_${page.id}")
                                                ) {
                                                    if (selectedPageIdForOcr == page.id && isOcrRunning) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Gemini", modifier = Modifier.size(16.dp), tint = Color.Black)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Extract Text", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            } else {
                                                // Show text box (decrypted/encrypted appropriately)
                                                val textToShow = if (folderPin != null && folderPin.isNotEmpty() && !hasDecryptedValue) {
                                                    "•••••• [ENCRYPTED - ENTER PASSCODE ABOVE TO VIEW]"
                                                } else {
                                                    editingTextMap[page.id] ?: "Decryption verified. Preparing editor..."
                                                }

                                                OutlinedTextField(
                                                    value = textToShow,
                                                    onValueChange = {
                                                        if (hasDecryptedValue) {
                                                            editingTextMap[page.id] = it
                                                        }
                                                    },
                                                    label = { Text("Extracted Content", color = Color.Black) },
                                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Black),
                                                    maxLines = 6,
                                                    readOnly = !hasDecryptedValue,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = Color.White,
                                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                                        focusedTextColor = Color.Black,
                                                        unfocusedTextColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Document Scan Enhancement (Correct Over-Dark / Over-Light)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val filterOptions = listOf(
                                            "ORIGINAL" to "Original",
                                            "LIGHTEN" to "💡 Lighten (Over-Dark)",
                                            "DARKEN" to "🕶️ Darken (Over-Light)",
                                            "HIGH_CONTRAST" to "⚡ Contrast Boost",
                                            "B_W" to "📰 Crisp B&W",
                                            "GREYSCALE" to "Grey"
                                        )
                                        items(filterOptions) { (filter, label) ->
                                            val isSelected = page.filterType.uppercase() == filter
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    viewModel.updatePageFilter(page, filter)
                                                },
                                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFFF1F5F9),
                                                    labelColor = Color.DarkGray
                                                ),
                                                modifier = Modifier.testTag("filter_chip_${page.id}_$filter")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Document details not found.", style = MaterialTheme.typography.titleMedium, color = Color(0xFF64748B))
                    }
                }

                // Biometric / PIN Lock Screen modal for secure folders
                if (showSecureFolderUnlock) {
                    BiometricLockDialog(
                        correctPin = folderPin ?: "",
                        onSuccess = {
                            folderDecryptionPin = folderPin ?: ""
                            hasDecryptedValue = true
                            showSecureFolderUnlock = false
                            viewModel.addAuditLog("DECRYPT", "KEYS", "Private folder vault authenticated with PIN/Biometrics")
                            // Reload pages to trigger decryption in editingTextMap
                            activePages.forEach { page ->
                                if (page.extractedText != null) {
                                    val dec = viewModel.decryptPageText(page.extractedText, folderPin ?: "")
                                    editingTextMap[page.id] = dec
                                }
                            }
                        },
                        onDismiss = {
                            onNavigateBack()
                        }
                    )
                }
            }
        }
    }
}
