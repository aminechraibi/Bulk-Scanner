package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import com.example.data.SampleDocumentGenerator
import com.example.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    viewModel: ScannerViewModel,
    batchId: String,
    initialPreset: String,
    onNavigateBack: () -> Unit,
    onNavigateToReview: (batchId: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentBatch by viewModel.currentBatch.collectAsState()
    val pages by viewModel.currentPages.collectAsState()

    // Camera settings
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isRealCamera by remember { mutableStateOf(hasCameraPermission) } // Use real camera if permission is already granted

    var flashEnabled by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    // Simulator specific parameters
    var selectedSimDocIndex by remember { mutableStateOf(0) }
    val simDoc = SampleDocumentGenerator.sampleTypes[selectedSimDocIndex]
    
    // Virtual camera controls (Simulate alignment and movement)
    var cameraWobbleX by remember { mutableStateOf(0f) }
    var cameraWobbleY by remember { mutableStateOf(0f) }
    var alignmentProgress by remember { mutableStateOf(0.7f) } // 0f to 1f (0.7f = good alignment)
    var isBlurry by remember { mutableStateOf(false) }
    var isLowLight by remember { mutableStateOf(false) }
    
    // Auto Capture trigger states
    var autoCaptureTimerProgress by remember { mutableStateOf(0f) } // 0f to 1f
    var autoCaptureLocked by remember { mutableStateOf(false) }
    var showFlashOverlay by remember { mutableStateOf(false) }

    // Draggable crop corners (Paper boundary / automatic detection handles)
    var topLeftX by remember { mutableStateOf(0.18f) }
    var topLeftY by remember { mutableStateOf(0.22f) }
    var topRightX by remember { mutableStateOf(0.82f) }
    var topRightY by remember { mutableStateOf(0.22f) }
    var bottomRightX by remember { mutableStateOf(0.82f) }
    var bottomRightY by remember { mutableStateOf(0.78f) }
    var bottomLeftX by remember { mutableStateOf(0.18f) }
    var bottomLeftY by remember { mutableStateOf(0.78f) }

    var activeDraggingCorner by remember { mutableStateOf<String?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Automatically adjust paper boundary corners dynamically (Real-time Paper Detection)
    // only if the user is not actively dragging/customizing them manually.
    LaunchedEffect(alignmentProgress, cameraWobbleX, cameraWobbleY, activeDraggingCorner, isRealCamera) {
        if (activeDraggingCorner == null) {
            val tX = if (isRealCamera) 0f else cameraWobbleX * 0.0006f
            val tY = if (isRealCamera) 0f else cameraWobbleY * 0.0006f
            val deltaAlign = (alignmentProgress - 0.7f) * 0.03f

            topLeftX = 0.18f + tX
            topLeftY = 0.22f + tY

            topRightX = 0.82f + tX + deltaAlign
            topRightY = 0.22f + tY - deltaAlign

            bottomRightX = 0.82f + tX + deltaAlign
            bottomRightY = 0.78f + tY + deltaAlign

            bottomLeftX = 0.18f + tX - deltaAlign
            bottomLeftY = 0.78f + tY
        }
    }

    // Real CameraX instance objects
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            isRealCamera = true
        } else {
            Toast.makeText(context, "Camera permission denied. Defaulting to Simulator.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-request camera permission on entering the screen if not yet granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Trigger drifting wobble to simulate physical hand holding
    LaunchedEffect(isPaused) {
        if (!isPaused) {
            while (true) {
                // Smooth sinus drift
                val time = System.currentTimeMillis() / 1000f
                cameraWobbleX = Math.sin(time * 2.0).toFloat() * 15f
                cameraWobbleY = Math.cos(time * 1.5).toFloat() * 10f
                
                // Randomly trigger minor blurs or low-light to make scanning dynamic
                isBlurry = (Math.sin(time * 0.5) > 0.85)
                isLowLight = (Math.cos(time * 0.3) > 0.90)
                
                delay(50)
            }
        }
    }

    // Capture execution block
    val triggerCaptureAction = {
        if (!isPaused && !showFlashOverlay) {
            scope.launch {
                // 1. Play flash animation
                showFlashOverlay = true
                delay(100)
                showFlashOverlay = false

                // 2. Generate actual file content
                if (isRealCamera) {
                    val file = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                    imageCapture?.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                viewModel.addPageToCurrentBatch(
                                    tempFile = file,
                                    preset = initialPreset,
                                    topLeftX = topLeftX, topLeftY = topLeftY,
                                    topRightX = topRightX, topRightY = topRightY,
                                    bottomRightX = bottomRightX, bottomRightY = bottomRightY,
                                    bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY
                                )
                            }
                            override fun onError(exception: ImageCaptureException) {
                                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } else {
                    // Simulator Mode: generate programmatic document texture
                    val tempSimFile = SampleDocumentGenerator.generateSampleDocFile(context, simDoc.id)
                    viewModel.addPageToCurrentBatch(
                        tempFile = tempSimFile,
                        preset = initialPreset,
                        topLeftX = topLeftX, topLeftY = topLeftY,
                        topRightX = topRightX, topRightY = topRightY,
                        bottomRightX = bottomRightX, bottomRightY = bottomRightY,
                        bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY
                    )
                }
                
                // Reset countdown
                autoCaptureTimerProgress = 0f
                autoCaptureLocked = false
            }
        }
    }

    // Auto capture loop
    LaunchedEffect(isPaused, isRealCamera, selectedSimDocIndex, cameraWobbleX, isBlurry, isLowLight, alignmentProgress) {
        if (!isPaused) {
            while (true) {
                // Determine paper state: Green if stable, aligned, bright, and not blurry.
                val isAligned = alignmentProgress > 0.5f
                val isStable = Math.abs(cameraWobbleX) < 12f
                val canLock = isAligned && isStable && !isBlurry && !isLowLight

                if (canLock) {
                    autoCaptureLocked = true
                    // Increment countdown
                    autoCaptureTimerProgress += 0.15f
                    if (autoCaptureTimerProgress >= 1f) {
                        triggerCaptureAction()
                    }
                } else {
                    autoCaptureLocked = false
                    autoCaptureTimerProgress = (autoCaptureTimerProgress - 0.2f).coerceAtLeast(0f)
                }
                delay(100)
            }
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onSizeChanged { containerSize = it }
                .pointerInput(isPaused) {
                    if (!isPaused) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (containerSize.width > 0 && containerSize.height > 0) {
                                    val px = offset.x / containerSize.width
                                    val py = offset.y / containerSize.height
                                    val distTL = Math.hypot((px - topLeftX).toDouble(), (py - topLeftY).toDouble())
                                    val distTR = Math.hypot((px - topRightX).toDouble(), (py - topRightY).toDouble())
                                    val distBR = Math.hypot((px - bottomRightX).toDouble(), (py - bottomRightY).toDouble())
                                    val distBL = Math.hypot((px - bottomLeftX).toDouble(), (py - bottomLeftY).toDouble())

                                    val threshold = 0.15 // Grab radius threshold
                                    val closest = listOf(
                                        "TL" to distTL,
                                        "TR" to distTR,
                                        "BR" to distBR,
                                        "BL" to distBL
                                    ).minByOrNull { it.second }

                                    if (closest != null && closest.second < threshold) {
                                        activeDraggingCorner = closest.first
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (containerSize.width > 0 && containerSize.height > 0) {
                                    val dx = dragAmount.x / containerSize.width
                                    val dy = dragAmount.y / containerSize.height

                                    when (activeDraggingCorner) {
                                        "TL" -> {
                                            topLeftX = (topLeftX + dx).coerceIn(0f, topRightX - 0.05f)
                                            topLeftY = (topLeftY + dy).coerceIn(0f, bottomLeftY - 0.05f)
                                        }
                                        "TR" -> {
                                            topRightX = (topRightX + dx).coerceIn(topLeftX + 0.05f, 1f)
                                            topRightY = (topRightY + dy).coerceIn(0f, bottomRightY - 0.05f)
                                        }
                                        "BR" -> {
                                            bottomRightX = (bottomRightX + dx).coerceIn(bottomLeftX + 0.05f, 1f)
                                            bottomRightY = (bottomRightY + dy).coerceIn(topRightY + 0.05f, 1f)
                                        }
                                        "BL" -> {
                                            bottomLeftX = (bottomLeftX + dx).coerceIn(0f, bottomRightX - 0.05f)
                                            bottomLeftY = (bottomLeftY + dy).coerceIn(topLeftY + 0.05f, 1f)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                activeDraggingCorner = null
                            }
                        )
                    }
                }
        ) {
            // 1. Viewfinder Screen (Real CameraX or Simulator desk view)
            if (isRealCamera && hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                                // Connect flash control
                                camera.cameraControl.enableTorch(flashEnabled)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Interactive Desk Simulator Layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF15181C)),
                    contentAlignment = Alignment.Center
                ) {
                    // Floating table desk grid
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeColor = Color.White.copy(alpha = 0.03f)
                        for (i in 0..size.width.toInt() step 60) {
                            drawLine(strokeColor, Offset(i.toFloat(), 0f), Offset(i.toFloat(), size.height))
                        }
                        for (i in 0..size.height.toInt() step 60) {
                            drawLine(strokeColor, Offset(0f, i.toFloat()), Offset(size.width, i.toFloat()))
                        }
                    }

                    // Virtual Document on Desk
                    Box(
                        modifier = Modifier
                            .size(280.dp, 370.dp)
                            .offset(
                                x = (cameraWobbleX * (1f - alignmentProgress)).dp,
                                y = (cameraWobbleY * (1f - alignmentProgress)).dp
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(simDoc.baseColor))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.size(24.dp).background(Color.Red.copy(alpha = 0.2f), CircleShape))
                                Box(modifier = Modifier.width(100.dp).height(12.dp).background(Color.Black.copy(alpha = 0.15f)))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(Color.Black.copy(alpha = 0.1f)))
                                Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(Color.Black.copy(alpha = 0.1f)))
                                Box(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp).background(Color.Black.copy(alpha = 0.1f)))
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("📄 ${simDoc.displayName}", color = Color.Black.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("SAMPLE", color = Color.Black.copy(alpha = 0.2f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            val borderStateColor = when {
                alignmentProgress < 0.4f -> Color(0xFFE74C3C) // Red - bad
                autoCaptureLocked -> Color(0xFF00E676)       // Green - lock
                else -> Color(0xFFF1C40F)                    // Yellow - stabilizing
            }

            // 2. Neon Perspective Border Overlay (Draws responsive crop rectangle)
            OverlayBorderCanvas(
                topLeftX = topLeftX, topLeftY = topLeftY,
                topRightX = topRightX, topRightY = topRightY,
                bottomRightX = bottomRightX, bottomRightY = bottomRightY,
                bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY,
                borderStateColor = borderStateColor
            )

            // 3. Header HUD (Page counters, pause, source toggle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            currentBatch?.name ?: "Bulk Scanning",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Pages Scanned: ${pages.size}",
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Camera Source Toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable {
                                if (!hasCameraPermission) {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    isRealCamera = !isRealCamera
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isRealCamera) Icons.Default.Videocam else Icons.Default.Dvr,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isRealCamera) "Real Cam" else "Demo Desk",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Document Selection Bar (Only visible in simulator mode to simulate changing physical sheets!)
                if (!isRealCamera) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Next Sheet to Scan:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                selectedSimDocIndex = (selectedSimDocIndex - 1 + SampleDocumentGenerator.sampleTypes.size) % SampleDocumentGenerator.sampleTypes.size
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ArrowLeft, contentDescription = "Prev", tint = Color.White)
                            }
                            Text(
                                simDoc.displayName,
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(130.dp)
                            )
                            IconButton(onClick = {
                                selectedSimDocIndex = (selectedSimDocIndex + 1) % SampleDocumentGenerator.sampleTypes.size
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ArrowRight, contentDescription = "Next", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // 4. Live Warnings & Calibration HUD (Blur, light alerts)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Warning Banners
                    if (isPaused) {
                        WarningBadge("Scanning Paused")
                    } else {
                        if (isBlurry) {
                            WarningBadge("⚠️ Image is blurry! Keep steady")
                        }
                        if (isLowLight) {
                            WarningBadge("⚡ Lighting is too dark!")
                        }
                        if (alignmentProgress < 0.4f) {
                            WarningBadge("❌ Paper not fully visible / Align border")
                        } else if (Math.abs(cameraWobbleX) > 12f) {
                            WarningBadge("⚠️ Camera moving!")
                        }
                    }

                    // Auto capture lock status circular progress
                    if (autoCaptureLocked && autoCaptureTimerProgress > 0.05f) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = autoCaptureTimerProgress,
                                color = Color(0xFF00E676),
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                "${(autoCaptureTimerProgress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. Camera Shutter Shading Flash effect
            if (showFlashOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
            }

            // 6. Bottom Controls Deck (Shutter button, Pause, Flash, Finish)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, top = 16.dp)
            ) {
                // Calibration sliders if in Simulator to let user test drift and distance!
                if (!isRealCamera) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Align Lens:", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Slider(
                            value = alignmentProgress,
                            onValueChange = { alignmentProgress = it },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E676),
                                activeTrackColor = Color(0xFF00E676)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        Text(
                            if (alignmentProgress > 0.5f) "LOCK" else "MISALIGNED",
                            color = if (alignmentProgress > 0.5f) Color(0xFF00E676) else Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Toggle
                    IconButton(
                        onClick = { flashEnabled = !flashEnabled },
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (flashEnabled) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (flashEnabled) Color.Black else Color.White
                        )
                    }

                    // Shutter Button (Manual Capture)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(if (isPaused) Color.Gray else Color.White)
                            .clickable(enabled = !isPaused) { triggerCaptureAction() }
                            .testTag("camera_shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.1f))
                        )
                    }

                    // Pause scanning button
                    IconButton(
                        onClick = { isPaused = !isPaused },
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (isPaused) Color.White else Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause Scan",
                            tint = if (isPaused) Color.Black else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom bar finish confirmation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick thumbnail showing latest capture if any
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { if (pages.isNotEmpty()) onNavigateToReview(batchId) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (pages.isNotEmpty()) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Review", tint = Color(0xFF00E676))
                        } else {
                            Box(modifier = Modifier.size(24.dp).background(Color.White.copy(alpha = 0.2f), CircleShape))
                        }
                    }

                    // Complete Batch Button
                    Button(
                        onClick = { onNavigateToReview(batchId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .width(180.dp)
                            .testTag("finish_scan_button")
                    ) {
                        Text("Finish (${pages.size} pgs)", color = Color.Black, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WarningBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.dp, Color(0xFFE74C3C).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OverlayBorderCanvas(
    topLeftX: Float, topLeftY: Float,
    topRightX: Float, topRightY: Float,
    bottomRightX: Float, bottomRightY: Float,
    bottomLeftX: Float, bottomLeftY: Float,
    borderStateColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val p1 = Offset(topLeftX * width, topLeftY * height)
        val p2 = Offset(topRightX * width, topRightY * height)
        val p3 = Offset(bottomRightX * width, bottomRightY * height)
        val p4 = Offset(bottomLeftX * width, bottomLeftY * height)

        val path = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
            close()
        }

        // Draw boundaries
        drawPath(
            path = path,
            color = borderStateColor,
            style = Stroke(width = 4.dp.toPx(), miter = 4f)
        )

        // Draw targets/crosshairs at corners
        val radius = 12.dp.toPx()
        val corners = listOf(p1, p2, p3, p4)
        for (corner in corners) {
            drawCircle(
                color = borderStateColor,
                radius = radius,
                center = corner,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = borderStateColor,
                radius = 4.dp.toPx(),
                center = corner
            )
        }
    }
}
