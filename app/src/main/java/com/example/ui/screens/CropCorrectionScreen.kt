package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.CropPoints
import com.example.ui.components.InteractiveCropper
import com.example.ui.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropCorrectionScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOcr: () -> Unit
) {
    val batchImages by viewModel.batchImages.collectAsState()
    val currentCropIndex by viewModel.currentCropIndex.collectAsState()
    val cropPoints by viewModel.currentCropPoints.collectAsState()

    val currentBitmap = remember(currentCropIndex, batchImages) {
        if (currentCropIndex in batchImages.indices) {
            batchImages[currentCropIndex]
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perspective Crop", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("crop_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.updateCropPoints(CropPoints()) }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Corners", tint = Color.Black)
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
            if (currentBitmap != null) {
                // Main Interactive Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    InteractiveCropper(
                        bitmap = currentBitmap,
                        points = cropPoints,
                        onPointsChanged = { viewModel.updateCropPoints(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Controls Row
                Surface(
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Page ${currentCropIndex + 1} of ${batchImages.size} — Drag corners to correct perspective",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             // Rotate 90 Degrees button
                            Button(
                                onClick = { viewModel.rotateCurrentBatchImage() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0))
                            ) {
                                Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate 90°", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            // Auto-detect corners button
                            Button(
                                onClick = {
                                    viewModel.runAutoCornerDetection()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0)),
                                modifier = Modifier.testTag("auto_detect_corners_button")
                            ) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Auto Detect", tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto Detect", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            // Correct Perspective Action Button
                            Button(
                                onClick = {
                                    onNavigateToOcr()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("apply_crop_button")
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Next", tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Next", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No document pages queued. Please capture or import images first.", color = Color.Gray)
                }
            }
        }
    }
}
