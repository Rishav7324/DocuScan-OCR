package com.example.ui.screens

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.components.CaptureStabilityTracker
import com.example.ui.components.CropPoints
import com.example.ui.components.DocumentDetector
import com.example.ui.components.LiveDocumentOverlay
import com.example.ui.components.performPerspectiveCorrection
import com.example.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "CameraScanScreen"
private const val ANALYSIS_INTERVAL_MS = 180L   // ~5-6 fps throttle
private const val MAX_ANALYSIS_EDGE = 640        // downscale long edge before detection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    viewModel: ScannerViewModel,
    folderId: Long,
    onNavigateBack: () -> Unit,
    onScanComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    // Live detection state surfaced to the UI.
    var liveCorners by remember { mutableStateOf<CropPoints?>(null) }
    var liveFrameW by remember { mutableIntStateOf(0) }
    var liveFrameH by remember { mutableIntStateOf(0) }
    var docDetected by remember { mutableStateOf(false) }
    var autoCaptureEnabled by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var pageCount by remember { mutableStateOf(0) }
    var readyToCapture by remember { mutableStateOf(false) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    // Latest analyzed frame (already rotated, downscaled) used for both auto and
    // manual capture so detection and capture share one pipeline.
    val latestFrame = remember { java.util.concurrent.atomic.AtomicReference<FrameHolder?>(null) }

    val tracker = remember { CaptureStabilityTracker() }
    val lastAnalysisTime = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val vibrator = remember {
        context.getSystemService(Vibrator::class.java)
    }

    fun haptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(40)
        }
    }

    // Single capture path for BOTH auto and manual capture.
    fun capturePage(corners: CropPoints?) {
        if (capturing) return
        val holder = latestFrame.get() ?: return
        capturing = true
        flash = true
        haptic()
        scope.launch {
            try {
                val src = holder.bitmap
                val bmp = if (corners != null) performPerspectiveCorrection(src, corners) else src
                viewModel.addImageToBatch(bmp) // persisted immediately; finalized in crop screen
                pageCount++
            } catch (e: Exception) {
                Log.e(TAG, "capture failed", e)
            } finally {
                delay(220)
                flash = false
                capturing = false
                tracker.onCaptured()
                readyToCapture = false
                liveCorners = null
                docDetected = false
            }
        }
    }

    val analysisUseCase = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(Size(480, 640))
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        val raw = proxy.toBitmap() ?: return@setAnalyzer
                        val rotation = proxy.imageInfo.rotationDegrees
                        // Rotate to upright so detection/capture work in display orientation.
                        val upright = if (rotation == 0) raw else rotateBitmap(raw, rotation)
                        // Downscale to keep detection cheap; full-res is unnecessary.
                        val scaled = downscale(upright, MAX_ANALYSIS_EDGE)
                        if (scaled !== upright) raw.recycle()

                        latestFrame.set(FrameHolder(scaled, scaled.width, scaled.height))

                        val quad = DocumentDetector.detect(scaled)
                        val valid = quad?.let { DocumentDetector.isQuadValid(it) } == true

                        // Throttle: only update the stability state machine every
                        // ANALYSIS_INTERVAL_MS. Skipped frames keep the last overlay
                        // state and do NOT touch the tracker (so a skip never resets
                        // the consecutive-frame counter).
                        val now = System.currentTimeMillis()
                        val shouldEval = now - lastAnalysisTime.get() >= ANALYSIS_INTERVAL_MS
                        if (!shouldEval) return@setAnalyzer
                        lastAnalysisTime.set(now)

                        val doCapture = tracker.update(if (valid) quad else null)
                        val ready = tracker.state == CaptureStabilityTracker.State.ReadyToCapture

                        scope.launch {
                            liveCorners = quad
                            liveFrameW = scaled.width
                            liveFrameH = scaled.height
                            docDetected = valid
                            readyToCapture = ready
                            if (doCapture && autoCaptureEnabled && !capturing) {
                                capturePage(quad)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "analysis error", e)
                    } finally {
                        proxy.close()
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Document", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { autoCaptureEnabled = !autoCaptureEnabled }) {
                        Icon(
                            if (autoCaptureEnabled) Icons.Filled.FlashAuto else Icons.Filled.FlashOff,
                            contentDescription = "Auto-capture"
                        )
                    }
                    Text(
                        "$pageCount pages",
                        modifier = Modifier.padding(end = 12.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = Color.Black) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            // Manual capture always works: if no real quad was detected,
                            // fall back to the full frame so the user is never blocked.
                            val corners = liveCorners ?: DocumentDetector.fallbackInset(liveFrameW, liveFrameH)
                            capturePage(corners)
                        },
                        enabled = !capturing
                    ) {
                        Icon(
                            Icons.Filled.RadioButtonChecked,
                            contentDescription = "Capture",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Button(
                        onClick = onScanComplete,
                        enabled = pageCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text("Next (${pageCount})", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            val selector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    selector,
                                    preview,
                                    imageCapture,
                                    analysisUseCase
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "bind failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                LiveDocumentOverlay(
                    points = liveCorners,
                    detected = docDetected,
                    modifier = Modifier.fillMaxSize(),
                    frameWidth = liveFrameW,
                    frameHeight = liveFrameH,
                    stable = readyToCapture
                )

                if (readyToCapture) {
                    Text(
                        "Hold still — capturing…",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                } else if (!docDetected) {
                    Text(
                        "Point camera at a document",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (flash) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera permission required", color = Color.White)
                    Button(onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                        Text("Grant")
                    }
                }
            }
        }
    }
}

private data class FrameHolder(val bitmap: Bitmap, val width: Int, val height: Int)

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Downscales [bitmap] so its longest edge is <= [maxEdge], preserving aspect. */
private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val long = maxOf(bitmap.width, bitmap.height)
    if (long <= maxEdge) return bitmap
    val scale = maxEdge.toFloat() / long
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, w, h, true)
}
