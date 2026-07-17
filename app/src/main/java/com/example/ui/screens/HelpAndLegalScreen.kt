package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpAndLegalScreen(
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Guides & Integration", "Privacy Policy", "Legal & Compliance")

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Integration Docs & Legal", fontWeight = FontWeight.ExtraBold, color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("help_legal_back_button")) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Secondary Tab Navigation Bar
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFFF1F5F9),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) },
                            unselectedContentColor = Color.Gray,
                            selectedContentColor = Color.Black
                        )
                    }
                }

                // Scrollable content pane
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // TAB 0: Guides and Integration Setup
                            item {
                                GuideHeader(
                                    title = "Cloudflare R2 Integration Guide",
                                    icon = Icons.Default.CloudQueue,
                                    iconColor = Color(0xFFF59E0B)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Cloudflare R2 storage provides fully S3-compatible cloud bucket syncing with zero egress fees. To configure S3 storage sync:", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                                        
                                        BulletStep("1", "Create an Account", "Log in to the Cloudflare Dashboard and navigate to the R2 Object Storage section.")
                                        BulletStep("2", "Provision a Bucket", "Create a new R2 storage bucket. Set a descriptive lowercase name (e.g., 'docuscan-secure-vault').")
                                        BulletStep("3", "Generate API Credentials", "Navigate to 'Manage R2 API Tokens' and select 'Create Token'. Ensure the token scope permits Edit / Write authorizations.")
                                        BulletStep("4", "Copy Endpoint & Keys", "Copy your custom S3 client Endpoint URL, Access Key ID, and Secret Access Key.")
                                        BulletStep("5", "Save in Settings", "Paste these credentials into the 'Cloud Sync' section in this app. Unsynced documents will automatically back up securely to Cloudflare R2.")
                                    }
                                }
                            }

                            item {
                                GuideHeader(
                                    title = "Google Drive API Integration Setup",
                                    icon = Icons.Default.Cloud,
                                    iconColor = Color(0xFF34A853)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Google Drive backup stores your OCR transcripts directly in your personal cloud. To connect Google Drive:", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                                        
                                        BulletStep("1", "Google Cloud Project", "Go to the Google Cloud Console and create a new project named 'DocuScan Mobile'.")
                                        BulletStep("2", "Enable Google Drive API", "Go to the API Library and enable the 'Google Drive API' service for your project.")
                                        BulletStep("3", "Configure OAuth Screen", "Set up your OAuth consent screen. Select 'External' or 'Internal' and input developer contact emails.")
                                        BulletStep("4", "Generate Access Token", "Request the 'https://www.googleapis.com/auth/drive.file' scope to allow this application to manage only files created by it.")
                                        BulletStep("5", "Copy Token into Settings", "Generate an OAuth access token, paste it under the Google Drive credentials card in settings, or type 'simulated_token' to run sandbox test uploads.")
                                    }
                                }
                            }

                            item {
                                GuideHeader(
                                    title = "Dropbox Console Sync Configuration",
                                    icon = Icons.Default.CloudSync,
                                    iconColor = Color(0xFF0061FE)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Dropbox offers robust REST endpoints for saving files. Follow these steps to authorize storage:", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                                        
                                        BulletStep("1", "Dropbox App Console", "Navigate to the Dropbox Developer App Console and select 'Create App'.")
                                        BulletStep("2", "App Type Selection", "Select 'Scoped Access API' and choose 'App folder' access for maximum sandboxed isolation.")
                                        BulletStep("3", "Configure Permissions", "Under the Permissions tab, enable 'files.metadata.write' and 'files.content.write'.")
                                        BulletStep("4", "Generate Bearer Token", "Under settings, scroll to 'Generated Access Token' and select 'Generate'. Copy this string.")
                                        BulletStep("5", "Enable App Integration", "Input your account name and token in the Cloud Sync page. Type 'simulated_token' to bypass live calls for local verification.")
                                    }
                                }
                            }
                        }

                        1 -> {
                            // TAB 1: Privacy Policy
                            item {
                                GuideHeader(
                                    title = "Privacy Policy & Zero-AI Promise",
                                    icon = Icons.Default.Security,
                                    iconColor = Color(0xFF10B981)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "Effective Date: July 17, 2026\n\n" +
                                            "We recognize the critical importance of privacy, data protection, and user sovereignty over sensitive documents. This Privacy Policy details our offline operations and zero-retention principles.",
                                            fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium, lineHeight = 18.sp
                                        )

                                        LegalSectionTitle("1. No AI-Processing and Data Sovereign Promise")
                                        Text(
                                            "Our software operates under a strict 'Zero AI-Server' architectural framework. We do NOT use, submit, or transmit your physical scans, processed text, or local indexes to generative AI platforms, unverified cloud servers, or third-party training pipelines. All OCR character matches are resolved on-device with 100% deterministic local layout heuristics.",
                                            fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium, lineHeight = 16.sp
                                        )

                                        LegalSectionTitle("2. Local Sandboxing & AES Encryption")
                                        Text(
                                            "Your files are isolated within sandboxed client directories. When secure folder vaults are activated, the internal documents are encrypted locally using advanced AES-GCM-256 hardware cryptographic routines. Keys are derived offline from your 4-digit PIN. We have no backend server containing keys, guaranteeing that only you hold the keys.",
                                            fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium, lineHeight = 16.sp
                                        )

                                        LegalSectionTitle("3. User-Controlled Integrations")
                                        Text(
                                            "We do not collect analytics, logs, or scanning metrics. The optional integrations (Cloudflare R2, Google Drive, and Dropbox) are completely under your control. When enabled, the app communicates directly and only with your S3 buckets or personal accounts. No middleware or intermediary proxy is ever involved.",
                                            fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium, lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            // TAB 2: Legal and Compliance
                            item {
                                GuideHeader(
                                    title = "Regulatory Compliance & HIPAA Statements",
                                    icon = Icons.Default.Gavel,
                                    iconColor = Color(0xFF1E3A8A)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        LegalSectionTitle("HIPAA Security Rule Standards (PHI)")
                                        Text(
                                            "Our architecture meets the Administrative, Physical, and Technical Safeguards defined under the Health Insurance Portability and Accountability Act (HIPAA) to secure Protected Health Information (PHI):\n\n" +
                                            "• Access Controls: Private folder vaults enforce authorization checks prior to document display.\n" +
                                            "• Integrity & Encryption: Files are stored under AES 256-bit GCM cipher encryption blocks.\n" +
                                            "• Transmission Security: All external backups occur via TLS 1.3 direct S3 or HTTPS REST interfaces.\n" +
                                            "• Action-Level Audit Trail: A secure, immutable local compliance log tracks all creations, views, deletions, and sync events.",
                                            fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Medium, lineHeight = 17.sp
                                        )

                                        HorizontalDivider(color = Color(0xFFE2E8F0))

                                        LegalSectionTitle("GDPR Standard Compliance Principles")
                                        Text(
                                            "We conform with European General Data Protection Regulation (GDPR) mandates natively:\n\n" +
                                            "• Right to Erasure (Article 17): Deleting a document or folder wipes the underlying disk blocks immediately.\n" +
                                            "• Data Minimization (Article 5): We only extract characters from scan targets explicitly approved by the user.\n" +
                                            "• Local Storage Processing: No data transfers outside the Union occur unless explicitly routed to user-configured regional servers.",
                                            fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Medium, lineHeight = 17.sp
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
}

@Composable
fun GuideHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = Color.Black
        )
    }
}

@Composable
fun BulletStep(
    stepNum: String,
    stepTitle: String,
    stepDesc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNum,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stepTitle,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = Color.Black
            )
            Text(
                text = stepDesc,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun LegalSectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        color = Color.Black,
        modifier = Modifier.padding(top = 4.dp)
    )
}
