package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudSyncConfig
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.viewmodel.ScannerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit
) {
    val syncConfig by viewModel.syncConfig.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()

    var r2Bucket by remember { mutableStateOf(syncConfig.r2Bucket) }
    var r2Endpoint by remember { mutableStateOf(syncConfig.r2Endpoint) }
    var r2AccessKey by remember { mutableStateOf(syncConfig.r2AccessKey) }
    var r2SecretKey by remember { mutableStateOf(syncConfig.r2SecretKey) }

    var driveAccount by remember { mutableStateOf(syncConfig.googleDriveAccount) }
    var dropboxAccount by remember { mutableStateOf(syncConfig.dropboxAccount) }
    var driveToken by remember { mutableStateOf(syncConfig.googleDriveToken) }
    var dropboxToken by remember { mutableStateOf(syncConfig.dropboxToken) }

    val unsyncedCount = remember(allDocs) { allDocs.count { !it.isSynced } }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val lastSyncStr = if (syncConfig.lastSyncTime > 0L) sdf.format(Date(syncConfig.lastSyncTime)) else "Never"

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Cloud Sync & Backups", fontWeight = FontWeight.ExtraBold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("sync_back_button")) {
                            Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black
                    )
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
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Synchronization Status Card
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (unsyncedCount == 0) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                contentDescription = "Sync Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (unsyncedCount == 0) "All Files Synchronized" else "$unsyncedCount Documents Pending Sync",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                            Text(
                                text = "Last synced: $lastSyncStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.syncNow() },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sync_now_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(18.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now (Cloudflare R2 & GDrive)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Cloudflare R2 Secure Credentials (E2E secure upload)
                    Text("Cloudflare R2 Storage (Direct Backend)", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Dns, contentDescription = "R2 Icon", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Enable Cloudflare R2 Sync", fontWeight = FontWeight.Black, color = Color.Black)
                                }
                                Switch(
                                    checked = syncConfig.r2Enabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSyncConfig(syncConfig.copy(
                                            r2Enabled = checked,
                                            r2Bucket = r2Bucket,
                                            r2Endpoint = r2Endpoint,
                                            r2AccessKey = r2AccessKey,
                                            r2SecretKey = r2SecretKey
                                        ))
                                    }
                                )
                            }

                            AnimatedVisibility(visible = syncConfig.r2Enabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = r2Bucket,
                                        onValueChange = { r2Bucket = it },
                                        label = { Text("R2 Bucket Name") },
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
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = r2Endpoint,
                                        onValueChange = { r2Endpoint = it },
                                        label = { Text("R2 S3 endpoint url") },
                                        placeholder = { Text("https://<account>.r2.cloudflarestorage.com") },
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
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = r2AccessKey,
                                        onValueChange = { r2AccessKey = it },
                                        label = { Text("Access Key ID") },
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
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = r2SecretKey,
                                        onValueChange = { r2SecretKey = it },
                                        label = { Text("Secret Access Key") },
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
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Google Drive and Dropbox Syncing
                    Text("Third-Party Integrations", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Google Drive
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CloudQueue, contentDescription = "GDrive", tint = Color(0xFF34A853))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Google Drive Integration", fontWeight = FontWeight.Black, color = Color.Black)
                                        }
                                        if (syncConfig.googleDriveEnabled && driveAccount.isNotEmpty()) {
                                            Text("Account: $driveAccount", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Switch(
                                        checked = syncConfig.googleDriveEnabled,
                                        onCheckedChange = { checked ->
                                            if (checked && driveAccount.isEmpty()) {
                                                driveAccount = "user_active@gmail.com"
                                                if (driveToken.isEmpty()) driveToken = "simulated_token"
                                            }
                                            viewModel.updateSyncConfig(syncConfig.copy(
                                                googleDriveEnabled = checked,
                                                googleDriveAccount = if (checked) driveAccount else "",
                                                googleDriveToken = if (checked) driveToken else ""
                                            ))
                                        }
                                    )
                                }

                                AnimatedVisibility(visible = syncConfig.googleDriveEnabled) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(top = 10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = driveAccount,
                                            onValueChange = { driveAccount = it },
                                            label = { Text("Google Drive Account Email") },
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
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = driveToken,
                                            onValueChange = { driveToken = it },
                                            label = { Text("Google OAuth Access Token") },
                                            placeholder = { Text("Paste Google OAuth Token or 'simulated_token'") },
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
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "💡 Type 'simulated_token' for sandbox test upload, or use a genuine access token for live Google Drive uploads.",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            // Dropbox
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Cloud, contentDescription = "Dropbox", tint = Color(0xFF0061FE))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Dropbox Integration", fontWeight = FontWeight.Black, color = Color.Black)
                                        }
                                        if (syncConfig.dropboxEnabled && dropboxAccount.isNotEmpty()) {
                                            Text("Account: $dropboxAccount", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Switch(
                                        checked = syncConfig.dropboxEnabled,
                                        onCheckedChange = { checked ->
                                            if (checked && dropboxAccount.isEmpty()) {
                                                dropboxAccount = "user_active_db"
                                                if (dropboxToken.isEmpty()) dropboxToken = "simulated_token"
                                            }
                                            viewModel.updateSyncConfig(syncConfig.copy(
                                                dropboxEnabled = checked,
                                                dropboxAccount = if (checked) dropboxAccount else "",
                                                dropboxToken = if (checked) dropboxToken else ""
                                            ))
                                        }
                                    )
                                }

                                AnimatedVisibility(visible = syncConfig.dropboxEnabled) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(top = 10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = dropboxAccount,
                                            onValueChange = { dropboxAccount = it },
                                            label = { Text("Dropbox Account User") },
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
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = dropboxToken,
                                            onValueChange = { dropboxToken = it },
                                            label = { Text("Dropbox API Access Token") },
                                            placeholder = { Text("Paste Dropbox Token or 'simulated_token'") },
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
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "💡 Type 'simulated_token' for sandbox test upload, or use a live Dropbox HTTP bearer token.",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // General Sync Settings
                    Text("Sync Constraints", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Automatic background backups", fontWeight = FontWeight.Bold, color = Color.Black)
                                Switch(
                                    checked = syncConfig.autoBackup,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSyncConfig(syncConfig.copy(autoBackup = checked))
                                    }
                                )
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Backup over Wi-Fi only", fontWeight = FontWeight.Bold, color = Color.Black)
                                Switch(
                                    checked = syncConfig.wifiOnly,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSyncConfig(syncConfig.copy(wifiOnly = checked))
                                    }
                                )
                            }
                        }
                    }

                    // Action: Save Credentials Configuration
                    Button(
                        onClick = {
                            viewModel.updateSyncConfig(syncConfig.copy(
                                r2Bucket = r2Bucket,
                                r2Endpoint = r2Endpoint,
                                r2AccessKey = r2AccessKey,
                                r2SecretKey = r2SecretKey,
                                googleDriveAccount = driveAccount,
                                googleDriveToken = driveToken,
                                dropboxAccount = dropboxAccount,
                                dropboxToken = dropboxToken
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_cloud_setup_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save Configurations", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Cloud Credentials", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
