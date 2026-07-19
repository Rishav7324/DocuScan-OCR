package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.BiometricLockDialog
import com.example.ui.components.SetPasscodeDialog
import com.example.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit
) {
    val auditLogs by viewModel.auditLogs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }

    val filteredLogs = remember(auditLogs, searchQuery) {
        if (searchQuery.isEmpty()) {
            auditLogs
        } else {
            auditLogs.filter { log ->
                log.action.contains(searchQuery, ignoreCase = true) ||
                log.resourceType.contains(searchQuery, ignoreCase = true) ||
                log.details.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    var showSnack by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // App Lock (global security)
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    var showEnablePin by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Regulatory Compliance Audit", fontWeight = FontWeight.ExtraBold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("compliance_back_button")) {
                            Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black
                    ),
                    actions = {
                        var exporting by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                if (exporting) return@Button
                                exporting = true
                                scope.launch {
                                    val file = viewModel.exportAuditTrail(context)
                                    if (file != null) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            context.packageName + ".fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "DocuScan OCR Audit Trail")
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Export Audit Trail"))
                                    }
                                    viewModel.addAuditLog("EXPORT", "AUDIT", "Exported HIPAA/GDPR audit trail file 'AUDIT_TRAIL_GDPR.log'")
                                    showSnack = true
                                    exporting = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export Logs", modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Trail", fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.padding(end = 4.dp).testTag("clear_audit_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = Color(0xFFEF4444))
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Fact Cards Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // HIPAA Card
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 20.dp
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.HealthAndSafety, contentDescription = "HIPAA", tint = Color(0xFF16A34A))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("HIPAA Core", fontWeight = FontWeight.Black, color = Color(0xFF16A34A), fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Protects Protected Health Information (PHI) via offline sandboxing and AES-GCM 256 hardware cryptographic encryption protocols.",
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // GDPR Card
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 20.dp
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Gavel, contentDescription = "GDPR", tint = Color(0xFF2563EB))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GDPR Standard", fontWeight = FontWeight.Black, color = Color(0xFF2563EB), fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Enforces Right to Erasure, data minimization, local storage processing, and transparent cryptographic action-level consent checks.",
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // App Lock (global security gate)
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = "App Lock", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Global App Lock", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        if (appLockEnabled) "App locked with a 4-digit PIN on launch."
                                        else "Require a PIN to open the app.",
                                        fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Switch(
                                checked = appLockEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) showEnablePin = true
                                    else showDisableConfirm = true
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    // Search Bar for Audit Trail
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search actions, resources, or hashes...", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audit_search_input")
                    )

                    // Audit Log List Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cryptographic Audit Trail (${filteredLogs.size} logs)",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Immutable Sandbox", fontWeight = FontWeight.Bold, color = Color.Black) }
                        )
                    }

                    // Scrollable Logs Terminal List
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xE60F172A)) // Semi-translucent dark slate terminal background
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No matching compliance logs found.", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredLogs) { log ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                    ) {
                                        // Header of log
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = sdf.format(Date(log.timestamp)),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        when (log.action) {
                                                            "CREATE" -> Color(0xFF16A342)
                                                            "DELETE" -> Color(0xFFDC2626)
                                                            "DECRYPT" -> Color(0xFFD97706)
                                                            "EXPORT" -> Color(0xFF2563EB)
                                                            else -> Color(0xFF475569)
                                                        }
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = log.action,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "[${log.resourceType}]",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = log.details,
                                            color = Color(0xFFF1F5F9),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = Color(0x0FFFFFFF), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Snackbar toast
                AnimatedVisibility(
                    visible = showSnack,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("HIPAA / GDPR audit trail exported successfully!", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            TextButton(onClick = { showSnack = false }) {
                                Text("OK", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.clearAuditLogs()
                                showClearDialog = false
                            }) {
                                Text("Clear all", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("Cancel")
                            }
                        },
                        title = { Text("Erase audit trail?", fontWeight = FontWeight.Bold) },
                        text = { Text("This permanently deletes all compliance audit logs on this device. This action cannot be undone.") }
                    )
                }

                if (showEnablePin) {
                    SetPasscodeDialog(
                        onConfirm = { pin ->
                            viewModel.setAppLockPin(pin)
                            showEnablePin = false
                        },
                        onDismiss = { showEnablePin = false }
                    )
                }

                if (showDisableConfirm) {
                    BiometricLockDialog(
                        verify = { viewModel.unlockApp(it) },
                        onSuccess = {
                            viewModel.disableAppLock()
                            showDisableConfirm = false
                        },
                        onDismiss = { showDisableConfirm = false },
                        title = "Confirm to Disable App Lock"
                    )
                }
            }
        }
    }
}
