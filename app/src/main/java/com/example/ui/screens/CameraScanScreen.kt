package com.example.ui.screens

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.components.AutoCornerDetector
import com.example.ui.components.CropPoints
import com.example.ui.components.LiveDocumentOverlay
import com.example.ui.components.performPerspectiveCorrection
import com.example.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs

private const val TAG = "CameraScanScreen"

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

    // Live detection state
    var liveCorners by remember { mutableStateOf<CropPoints?>(null) }
    var docDetected by remember { mutableStateOf(false) }
    var autoCaptureEnabled by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var pageCount by remember { mutableStateOf(0) }
    // Stable-detection counter drives auto capture
    var stableFrames by remember { mutableStateOf(0) }

    // Shared ImageProxy -> Bitmap converter used by analysis + capture
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    // Latest preview frame kept for capture fallback so detection + capture share one pipeline
    val latestBitmap = remember { java.util.concurrent.atomic.AtomicReference<Bitmap?>(null) }

    fun capturePage(corners: CropPoints?) {
        if (capturing) return
        capturing = true
        flash = true
        scope.launch {
            try {
                val src = latestBitmap.get()
                if (src != null) {
                    val bmp = if (corners != null) {
                        performPerspectiveCorrection(src, corners)
                    } else src
                    // ponytail: persist immediately, batch finalized in crop screen
                    viewModel.addImageToBatch(bmp)
                    pageCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "capture failed", e)
            } finally {
                delay(220)
                flash = false
                capturing = false
                stableFrames = 0
            }
        }
    }

    val analysisUseCase = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(480, 640))
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    val bitmap = proxy.toBitmap().let { bmp ->
                        val rotation = proxy.imageInfo.rotationDegrees
                        if (rotation == 0) bmp else rotateBitmap(bmp, rotation)
                    } ?: run { proxy.close(); return@setAnalyzer }
                    latestBitmap.set(bitmap)
                    val corners = AutoCornerDetector.detectDocumentCorners(bitmap)
                    val detected = corners.topLeft != corners.topRight &&
                        corners.topRight != corners.bottomRight &&
                        corners.bottomRight != corners.bottomLeft &&
                        corners.areReasonable()
                    // post to main for overlay
                    scope.launch {
                        liveCorners = corners
                        docDetected = detected
                        if (detected && autoCaptureEnabled && !capturing) {
                            stableFrames++
                            if (stableFrames >= 5) capturePage(corners)
                        } else if (!detected) {
                            stableFrames = 0
                        }
                    }
                    proxy.close()
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
                    IconButton(onClick = {
                        // Manual capture using last detected corners (or whole frame)
                        capturePage(liveCorners)
                    }, enabled = !capturing) {
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

                // Live document overlay
                LiveDocumentOverlay(
                    points = liveCorners,
                    detected = docDetected,
                    modifier = Modifier.fillMaxSize()
                )

                if (!docDetected) {
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

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun CropPoints.areReasonable(): Boolean {
    // Reject degenerate quads: corners must occupy a meaningful area and be ordered.
    val xs = listOf(topLeft.x, topRight.x, bottomRight.x, bottomLeft.x)
    val ys = listOf(topLeft.y, topRight.y, bottomRight.y, bottomLeft.y)
    val width = (xs.maxOrNull()!! - xs.minOrNull()!!)
    val height = (ys.maxOrNull()!! - ys.minOrNull()!!)
    return width > 0.25f && height > 0.25f
}
