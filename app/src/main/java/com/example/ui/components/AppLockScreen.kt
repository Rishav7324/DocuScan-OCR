package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ScannerViewModel

/**
 * Full-screen gate shown when the global app lock is active.
 * PIN entry reuses [BiometricLockDialog]; the dialog cannot be dismissed (onDismiss = no-op)
 * so the app stays locked until the correct PIN is entered.
 */
@Composable
fun AppLockScreen(viewModel: ScannerViewModel) {
    var wipeMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "App Locked",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enter your app PIN to continue.",
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (wipeMessage != null) {
                Text(wipeMessage!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }

        // Non-dismissable PIN entry dialog
        BiometricLockDialog(
            verify = { viewModel.unlockApp(it) },
            onSuccess = { /* VM already unlocked; screen will unmount */ },
            onDismiss = { /* lock cannot be dismissed */ },
            title = "App Locked"
        )
    }
}
