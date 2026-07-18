package com.example.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalPageViewer(
    pageName: String,
    onNavigateBack: () -> Unit
) {
    val titles = mapOf(
        "privacy-policy" to "Privacy Policy",
        "terms-of-service" to "Terms of Service",
        "security-policy" to "Security Policy",
        "acceptable-use-policy" to "Acceptable Use Policy",
        "grievance-redressal" to "Grievance Redressal",
        "cookie-policy" to "Cookie Policy",
        "disclaimer" to "Disclaimer",
        "refund-policy" to "Refund Policy",
        "cancellation-policy" to "Cancellation Policy",
        "accessibility-statement" to "Accessibility Statement",
        "about-us" to "About Us",
        "index" to "Legal Hub"
    )
    val title = titles[pageName] ?: pageName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color.Black)
            )
        }
    ) { innerPadding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    loadUrl("file:///android_asset/legal/$pageName.html")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
