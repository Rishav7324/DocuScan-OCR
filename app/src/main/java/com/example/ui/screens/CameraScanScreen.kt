package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ScannerViewModel
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    viewModel: ScannerViewModel,
    folderId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToCrop: () -> Unit
) {
    val context = LocalContext.current
    val batchImages by viewModel.batchImages.collectAsState()
    var docName by remember { mutableStateOf("Scanned Doc ${System.currentTimeMillis() % 100000}") }
    var selectedSimType by remember { mutableStateOf("HIPAA_MEDICAL") }

    // Gallery Import Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                bitmap?.let { b ->
                    viewModel.addImageToBatch(b)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Document", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("scan_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import", tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Document Name Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Document Icon",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = docName,
                    onValueChange = { docName = it },
                    label = { Text("Document Title", color = Color.Black, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("doc_name_input")
                )
            }

            // Simulated Viewfinder Frame
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(2.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121212)),
                contentAlignment = Alignment.Center
            ) {
                // Outer corners layout indicator
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val inset = 30f
                    val length = 80f
                    val stroke = 8f
                    val activeColor = Color(0xFF64B5F6)

                    // Top Left
                    drawLine(activeColor, Offset(inset, inset), Offset(inset + length, inset), strokeWidth = stroke)
                    drawLine(activeColor, Offset(inset, inset), Offset(inset, inset + length), strokeWidth = stroke)

                    // Top Right
                    drawLine(activeColor, Offset(size.width - inset, inset), Offset(size.width - inset - length, inset), strokeWidth = stroke)
                    drawLine(activeColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + length), strokeWidth = stroke)

                    // Bottom Left
                    drawLine(activeColor, Offset(inset, size.height - inset), Offset(inset + length, size.height - inset), strokeWidth = stroke)
                    drawLine(activeColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - length), strokeWidth = stroke)

                    // Bottom Right
                    drawLine(activeColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - length, size.height - inset), strokeWidth = stroke)
                    drawLine(activeColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - length), strokeWidth = stroke)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Scanner Eye",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Document Autofocus Active",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Position your document inside the alignment indicators. Choose simulated test standard below:",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Simulated document standard picker
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "HIPAA_MEDICAL" to "🩺 Medical Case File",
                            "GDPR_AGREEMENT" to "⚖️ GDPR Privacy Pack",
                            "BUSINESS_INVOICE" to "🧾 Business Invoice",
                            "AISTUDIO_README" to "📂 Workspace Readme",
                            "ID_CARD" to "💳 ID Card / License",
                            "RECEIPT" to "🎟️ Sales Receipt",
                            "HANDWRITTEN" to "📝 Creative Note",
                            "SHIPPING" to "📦 Shipping Label"
                        ).forEach { (type, label) ->
                            val selected = selectedSimType == type
                            FilterChip(
                                selected = selected,
                                onClick = { selectedSimType = type },
                                label = { Text(label, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF222222),
                                    labelColor = Color.LightGray
                                )
                            )
                        }
                    }
                }
            }

            // Staggered Batch Scans Tray
            if (batchImages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Batch Queue (${batchImages.size} pages added)",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(batchImages) { index, bitmap ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectCropIndex(index) }
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page Scan",
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Clear button on thumbnail
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                                        .clickable { viewModel.removeImageFromBatch(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("P${index + 1}", color = Color.White, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Capture Controls Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel batch button
                IconButton(
                    onClick = {
                        viewModel.clearBatch()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE2E8F0), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Queue", tint = Color.Black)
                }

                // Shutter capture button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .clickable {
                            val simulatedBitmap = generateSimulatedDocument(selectedSimType)
                            viewModel.addImageToBatch(simulatedBitmap)
                        }
                        .padding(4.dp)
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                // Finalize batch button
                IconButton(
                    onClick = {
                        if (batchImages.isNotEmpty()) {
                            // Automatically selects first page for initial cropping
                            viewModel.selectCropIndex(0)
                            viewModel.finalizeBatch(docName, folderId)
                            onNavigateToCrop()
                        }
                    },
                    enabled = batchImages.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (batchImages.isNotEmpty()) MaterialTheme.colorScheme.primary 
                            else Color(0xFFF1F5F9), 
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check, 
                        contentDescription = "Confirm Documents", 
                        tint = if (batchImages.isNotEmpty()) Color.Black else Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * Procedurally generates a document standard image containing real words, lines, boxes, and structures.
 * This guarantees the user gets highly realistic and fully functional OCR and perspective warping testing.
 */
fun generateSimulatedDocument(type: String): Bitmap {
    val bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Background paper styling
    val paperPaint = AndroidPaint().apply {
        color = AndroidColor.rgb(250, 249, 245)
        style = AndroidPaint.Style.FILL
    }
    canvas.drawRect(0f, 0f, 800f, 1100f, paperPaint)

    // Shadow border to allow perspective warp visibility
    val borderPaint = AndroidPaint().apply {
        color = AndroidColor.LTGRAY
        style = AndroidPaint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRect(5f, 5f, 795f, 1095f, borderPaint)

    // Font styles
    val titlePaint = AndroidPaint().apply {
        color = AndroidColor.DKGRAY
        textSize = 32f
        isFakeBoldText = true
        isAntiAlias = true
    }

    val bodyPaint = AndroidPaint().apply {
        color = AndroidColor.BLACK
        textSize = 18f
        isAntiAlias = true
    }

    val captionPaint = AndroidPaint().apply {
        color = AndroidColor.GRAY
        textSize = 14f
        isAntiAlias = true
    }

    when (type) {
        "HIPAA_MEDICAL" -> {
            canvas.drawText("🩺 CLINICAL INTAKE CASE RECORD", 50f, 80f, titlePaint)
            canvas.drawText("HIPAA SECURITY AND PRIVACY STANDARD COMPLIANT", 50f, 110f, captionPaint)
            canvas.drawLine(50f, 130f, 750f, 130f, titlePaint)

            canvas.drawText("PATIENT NAME: Alice J. Thornton", 50f, 180f, bodyPaint)
            canvas.drawText("DATE OF BIRTH: 11/04/1982", 50f, 210f, bodyPaint)
            canvas.drawText("RECORD NUMBER: #H82-7491-039B", 50f, 240f, bodyPaint)

            canvas.drawText("CLINICAL DIAGNOSIS & REMARKS:", 50f, 300f, titlePaint.apply { textSize = 22f })
            canvas.drawText("Patient presents with recurrent muscular strains on the left upper scapula.", 50f, 340f, bodyPaint)
            canvas.drawText("Symptoms exacerbated by prolonged screen usage and poor workspace ergonomics.", 50f, 370f, bodyPaint)
            canvas.drawText("Recommend immediate occupational therapy assessment and bi-weekly stretches.", 50f, 400f, bodyPaint)

            canvas.drawText("SECURE CLASSIFIED PHI - DO NOT DISTRIBUTE OUTSIDE CLINIC DIRECTIVES", 50f, 1000f, captionPaint)
        }
        "GDPR_AGREEMENT" -> {
            canvas.drawText("⚖️ GDPR PRIVACY AGREEMENT & DIRECTIVE", 50f, 80f, titlePaint)
            canvas.drawText("REGULATORY AUDITING PROTOCOL v4.11", 50f, 110f, captionPaint)
            canvas.drawLine(50f, 130f, 750f, 130f, titlePaint)

            canvas.drawText("CONTRACT PARTY A: Sandbox Automation Services", 50f, 180f, bodyPaint)
            canvas.drawText("CONTRACT PARTY B: Authorized Workspace Developer", 50f, 210f, bodyPaint)

            canvas.drawText("Article 6: Lawfulness of Processing", 50f, 280f, titlePaint.apply { textSize = 22f })
            canvas.drawText("1. Processing shall be lawful only if and to the extent that at least one of the", 50f, 320f, bodyPaint)
            canvas.drawText("following applies: (a) the data subject has given consent to the processing of", 50f, 350f, bodyPaint)
            canvas.drawText("his or her personal data for one or more specific, validated purposes.", 50f, 380f, bodyPaint)
            canvas.drawText("(b) processing is necessary for the performance of a secure cloud integration contract.", 50f, 410f, bodyPaint)

            canvas.drawText("GDPR COMPLIANCE CERTIFICATE - SECURE OFFLINE DISPOSITION ASSURED", 50f, 1000f, captionPaint)
        }
        "BUSINESS_INVOICE" -> {
            canvas.drawText("🧾 ENTERPRISE DIGITAL INVOICE", 50f, 80f, titlePaint)
            canvas.drawText("BILLING PERIOD: Q3 EXPORT FISCAL SYNC", 50f, 110f, captionPaint)
            canvas.drawLine(50f, 130f, 750f, 130f, titlePaint)

            canvas.drawText("INVOICE NO: INV-2026-9048", 50f, 180f, bodyPaint)
            canvas.drawText("DATE ISSUED: July 17, 2026", 50f, 210f, bodyPaint)
            canvas.drawText("DUE DATE: August 17, 2026", 50f, 240f, bodyPaint)

            canvas.drawText("ITEM DESCRIPTION", 50f, 320f, titlePaint.apply { textSize = 18f })
            canvas.drawText("1. Cloudflare R2 High-Speed Storage (2TB Standard Allocation) - $15.00", 50f, 360f, bodyPaint)
            canvas.drawText("2. Gemini OCR High-Throughput Processing Engine Tier - $45.00", 50f, 390f, bodyPaint)
            canvas.drawText("3. End-to-End Local Cryptographic HSM Setup (HIPAA Kit) - $120.00", 50f, 420f, bodyPaint)

            canvas.drawText("TOTAL AMOUNT DUE: $180.00 USD", 50f, 520f, titlePaint.apply { textSize = 24f })
        }
        "ID_CARD" -> {
            val cardPaint = AndroidPaint().apply {
                color = AndroidColor.rgb(230, 242, 255)
                style = AndroidPaint.Style.FILL
            }
            canvas.drawRoundRect(80f, 150f, 720f, 550f, 20f, 20f, cardPaint)
            
            val cardBorder = AndroidPaint().apply {
                color = AndroidColor.rgb(51, 102, 204)
                style = AndroidPaint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRoundRect(80f, 150f, 720f, 550f, 20f, 20f, cardBorder)

            val headerPaint = AndroidPaint().apply {
                color = AndroidColor.rgb(0, 51, 153)
                textSize = 24f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("💳 DRIVER LICENSE & STATE CARD", 110f, 200f, headerPaint)
            canvas.drawLine(100f, 220f, 700f, 220f, cardBorder)

            val photoPaint = AndroidPaint().apply {
                color = AndroidColor.GRAY
                style = AndroidPaint.Style.FILL
            }
            canvas.drawRect(120f, 250f, 260f, 420f, photoPaint)
            canvas.drawText("PHOTO", 155f, 340f, captionPaint.apply { color = AndroidColor.WHITE })

            canvas.drawText("LAST NAME: SMITH", 300f, 275f, bodyPaint)
            canvas.drawText("FIRST NAME: JOHN ADAMS", 300f, 310f, bodyPaint)
            canvas.drawText("LIC NO: DL-2918374-B3", 300f, 345f, bodyPaint)
            canvas.drawText("CLASS: C STANDARD", 300f, 380f, bodyPaint)
            canvas.drawText("EXPIRES: 09/24/2030", 300f, 415f, bodyPaint)
            
            canvas.drawText("FEDERAL ID AND HIPAA COMPLIANT PROOF", 100f, 500f, captionPaint)
        }
        "RECEIPT" -> {
            val receiptTitlePaint = AndroidPaint().apply {
                color = AndroidColor.BLACK
                textSize = 28f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("🎟️ METRO GROCERY STORE #92", 200f, 80f, receiptTitlePaint)
            canvas.drawText("100 BROADWAY, NEW YORK, NY", 250f, 110f, captionPaint)
            canvas.drawText("PHONE: (212) 555-0199", 290f, 130f, captionPaint)
            canvas.drawLine(80f, 160f, 720f, 160f, titlePaint)

            canvas.drawText("CASHIER: Marcus (Register 4)", 80f, 200f, bodyPaint)
            canvas.drawText("DATE: July 17, 2026 12:45 PM", 80f, 230f, bodyPaint)
            canvas.drawText("TRANSACTION ID: TXN-9908123-AB", 80f, 260f, bodyPaint)
            canvas.drawLine(80f, 280f, 720f, 280f, captionPaint)

            canvas.drawText("1. Organic Honey Crisp Apples (2.3 lbs)   $6.88", 80f, 320f, bodyPaint)
            canvas.drawText("2. Almond Milk Unsweetened (0.5 Gal)      $3.99", 80f, 350f, bodyPaint)
            canvas.drawText("3. Whole Wheat Sourdough Bread            $4.50", 80f, 380f, bodyPaint)
            canvas.drawText("4. Roasted French Beans Pack              $5.25", 80f, 410f, bodyPaint)
            canvas.drawText("5. Decaf Colombian Ground Coffee (12oz)   $10.99", 80f, 440f, bodyPaint)

            canvas.drawLine(80f, 480f, 720f, 480f, captionPaint)
            canvas.drawText("SUBTOTAL:                             $31.61", 80f, 520f, bodyPaint)
            canvas.drawText("TAX (8.875%):                          $2.81", 80f, 550f, bodyPaint)
            canvas.drawText("TOTAL COST:                           $34.42", 80f, 590f, titlePaint.apply { textSize = 22f })

            canvas.drawText("THANK YOU FOR SHOPPING WITH METRO!", 200f, 1000f, captionPaint)
        }
        "HANDWRITTEN" -> {
            val penPaint = AndroidPaint().apply {
                color = AndroidColor.rgb(0, 51, 204)
                textSize = 24f
                isAntiAlias = true
            }
            val scribblesPaint = AndroidPaint().apply {
                color = AndroidColor.rgb(0, 77, 255)
                strokeWidth = 3f
                style = AndroidPaint.Style.STROKE
            }
            canvas.drawText("📝 PROJECT BRAINSTORM & THOUGHTS", 50f, 80f, penPaint.apply { isFakeBoldText = true })
            canvas.drawText("Date: July 17th, Friday morning", 50f, 115f, penPaint.apply { isFakeBoldText = false })
            canvas.drawLine(40f, 130f, 720f, 135f, scribblesPaint)

            canvas.drawText("- Need to implement custom adaptive corners", 60f, 200f, penPaint)
            canvas.drawText("- Check if Room DB correctly persists batch queues", 60f, 240f, penPaint)
            canvas.drawText("- Must look amazing! (use M3 tokens and nice spacing)", 60f, 280f, penPaint)
            
            canvas.drawCircle(400f, 500f, 80f, scribblesPaint)
            canvas.drawLine(360f, 540f, 440f, 540f, scribblesPaint)
            canvas.drawLine(370f, 560f, 430f, 560f, scribblesPaint)
            canvas.drawText("💡 Core Idea: Fast local processing + cloud sync", 180f, 650f, penPaint)
        }
        "SHIPPING" -> {
            canvas.drawText("📦 EXPRESS PARCEL SERVICE", 50f, 80f, titlePaint)
            canvas.drawText("CLASS: PRIORITY OVERNIGHT DELIVERY", 50f, 110f, captionPaint)
            canvas.drawLine(50f, 130f, 750f, 130f, titlePaint)

            canvas.drawText("FROM: DocuScan Labs, Suite 500, New York, NY", 50f, 180f, bodyPaint)
            canvas.drawText("SHIP TO: RISHAV RAJ", 50f, 230f, titlePaint.apply { textSize = 22f })
            canvas.drawText("STREET: 123 SILICON BOULEVARD", 50f, 265f, bodyPaint)
            canvas.drawText("CITY: SAN FRANCISCO, CA 94107", 50f, 300f, bodyPaint)
            canvas.drawLine(50f, 350f, 750f, 350f, titlePaint)

            val barcodePaint = AndroidPaint().apply {
                color = AndroidColor.BLACK
                style = AndroidPaint.Style.FILL
            }
            var barX = 100f
            while (barX < 700f) {
                val barWidth = if (Math.random() > 0.5) 8f else 18f
                canvas.drawRect(barX, 400f, barX + barWidth, 520f, barcodePaint)
                barX += barWidth + (if (Math.random() > 0.4) 6f else 12f)
            }
            canvas.drawText("TRACKING NUMBER: (99) 1Z 999 AA1 01 2345 6784", 180f, 550f, captionPaint)
        }
        else -> {
            canvas.drawText("📂 GOOGLE AI STUDIO APPLET SPECIFICATION", 50f, 80f, titlePaint)
            canvas.drawText("DEVELOPER ENVIRONMENT REPOSITORY SCHEMA", 50f, 110f, captionPaint)
            canvas.drawLine(50f, 130f, 750f, 130f, titlePaint)

            canvas.drawText("This DocuScan OCR scanner applet is developed to run natively in", 50f, 180f, bodyPaint)
            canvas.drawText("a secure sandboxed browser virtual space. It leverages Kotlin, Jetpack", 50f, 210f, bodyPaint)
            canvas.drawText("Compose, Room DB, and direct Gemini API REST interfaces for optimal stability.", 50f, 240f, bodyPaint)

            canvas.drawText("The local Room persistence model manages files and directories seamlessly.", 50f, 280f, bodyPaint)
            canvas.drawText("Cloudflare R2 provides unified low-cost object cloud synchronization.", 50f, 310f, bodyPaint)
        }
    }
    
    return bitmap
}
