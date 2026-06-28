package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.PageEntity
import com.example.ui.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    viewModel: ScannerViewModel,
    pageId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editingPage by viewModel.editingPage.collectAsState()

    // If page is null (e.g., cleared on save/back), don't render or show loading
    val page = editingPage ?: return

    // Original image file
    val originalFile = remember(page) { File(page.originalPath) }

    // Adjustable local states (copied from page)
    var rotationDegrees by remember(page) { mutableStateOf(page.rotationDegrees) }
    var topLeftX by remember(page) { mutableStateOf(page.topLeftX) }
    var topLeftY by remember(page) { mutableStateOf(page.topLeftY) }
    var topRightX by remember(page) { mutableStateOf(page.topRightX) }
    var topRightY by remember(page) { mutableStateOf(page.topRightY) }
    var bottomRightX by remember(page) { mutableStateOf(page.bottomRightX) }
    var bottomRightY by remember(page) { mutableStateOf(page.bottomRightY) }
    var bottomLeftX by remember(page) { mutableStateOf(page.bottomLeftX) }
    var bottomLeftY by remember(page) { mutableStateOf(page.bottomLeftY) }
    var enhancementPreset by remember(page) { mutableStateOf(page.enhancementPreset) }

    // Navigation and tools
    var selectedTool by remember { mutableStateOf("Crop") } // Crop, Rotate, Enhance
    var activeDraggingCorner by remember { mutableStateOf<String?>(null) } // "TL", "TR", "BR", "BL"
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page Editor (pg ${page.pageNumber})", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1117)),
                actions = {
                    // Quick Delete
                    IconButton(
                        onClick = {
                            viewModel.deletePage(page)
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = Color(0xFFE74C3C))
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117))
                    .padding(bottom = 24.dp)
            ) {
                // Toolbar Mode Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2229))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarTabItem(name = "Crop", icon = Icons.Default.Crop, isSelected = selectedTool == "Crop") { selectedTool = "Crop" }
                    ToolbarTabItem(name = "Rotate", icon = Icons.Outlined.RotateRight, isSelected = selectedTool == "Rotate") { selectedTool = "Rotate" }
                    ToolbarTabItem(name = "Enhance", icon = Icons.Default.AutoFixHigh, isSelected = selectedTool == "Enhance") { selectedTool = "Enhance" }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detail panels based on selected tool
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when (selectedTool) {
                        "Crop" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Drag corners to align edges.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                Button(
                                    onClick = {
                                        // Reset to default standard quad borders
                                        topLeftX = 0.05f; topLeftY = 0.05f
                                        topRightX = 0.95f; topRightY = 0.05f
                                        bottomRightX = 0.95f; bottomRightY = 0.95f
                                        bottomLeftX = 0.05f; bottomLeftY = 0.95f
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Reset Corner Crop", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        "Rotate" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { rotationDegrees = (rotationDegrees - 90 + 360) % 360 },
                                    modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.08f), CircleShape)
                                ) {
                                    Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left", tint = Color.White)
                                }

                                Text(
                                    "${rotationDegrees}°",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF00E676)
                                )

                                IconButton(
                                    onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                                    modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.08f), CircleShape)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right", tint = Color.White)
                                }
                            }
                        }
                        "Enhance" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enhancement Presets", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val presets = listOf("Original", "Document", "Handwriting", "Color", "Black & White")
                                    presets.forEach { preset ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (preset == enhancementPreset) Color(0xFF00E676) else Color(0xFF1E2229))
                                                .clickable { enhancementPreset = preset }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                preset.replace(" ", "\n"),
                                                color = if (preset == enhancementPreset) Color.Black else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom CTA action row (Cancel & Save)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            viewModel.savePageEdits(
                                page = page,
                                rotationDegrees = rotationDegrees,
                                topLeftX = topLeftX, topLeftY = topLeftY,
                                topRightX = topRightX, topRightY = topRightY,
                                bottomRightX = bottomRightX, bottomRightY = bottomRightY,
                                bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY,
                                enhancementPreset = enhancementPreset
                            )
                            Toast.makeText(context, "Page changes processed & saved", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp)
                            .testTag("save_edits_button")
                    ) {
                        Text("Apply & Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = Color(0xFF0D1117)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Visual Image Editor Canvas container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(selectedTool) {
                        if (selectedTool == "Crop") {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val px = offset.x / containerSize.width
                                    val py = offset.y / containerSize.height
                                    // Find closest corner within 50dp tolerance
                                    val distTL = Math.hypot((px - topLeftX).toDouble(), (py - topLeftY).toDouble())
                                    val distTR = Math.hypot((px - topRightX).toDouble(), (py - topRightY).toDouble())
                                    val distBR = Math.hypot((px - bottomRightX).toDouble(), (py - bottomRightY).toDouble())
                                    val distBL = Math.hypot((px - bottomLeftX).toDouble(), (py - bottomLeftY).toDouble())

                                    val threshold = 0.15 // Active boundary circle grab threshold
                                    val closest = listOf(
                                        "TL" to distTL,
                                        "TR" to distTR,
                                        "BR" to distBR,
                                        "BL" to distBL
                                    ).minByOrNull { it.second }

                                    if (closest != null && closest.second < threshold) {
                                        activeDraggingCorner = closest.first
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
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
                                },
                                onDragEnd = {
                                    activeDraggingCorner = null
                                }
                            )
                        }
                    }
            ) {
                // 1. Draw Original Image
                if (originalFile.exists()) {
                    AsyncImage(
                        model = originalFile,
                        contentDescription = "Original Page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationDegrees.toFloat())
                    )
                }

                // 2. Overlay draggable handles on Crop mode
                if (selectedTool == "Crop" && containerSize.width > 0) {
                    DraggableCropOverlayCanvas(
                        topLeftX = topLeftX, topLeftY = topLeftY,
                        topRightX = topRightX, topRightY = topRightY,
                        bottomRightX = bottomRightX, bottomRightY = bottomRightY,
                        bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY,
                        activeCorner = activeDraggingCorner
                    )
                }
            }

            // Drifting Magnifier Glass viewport bubble (Displays zoomed content of active dragging finger!)
            if (activeDraggingCorner != null && selectedTool == "Crop") {
                val dragX = when (activeDraggingCorner) {
                    "TL" -> topLeftX; "TR" -> topRightX; "BR" -> bottomRightX; else -> bottomLeftX
                }
                val dragY = when (activeDraggingCorner) {
                    "TL" -> topLeftY; "TR" -> topRightY; "BR" -> bottomRightY; else -> bottomLeftY
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (-40).dp)
                        .shadow(8.dp, CircleShape)
                        .border(3.dp, Color(0xFF00E676), CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    // Zoom into original document file
                    AsyncImage(
                        model = originalFile,
                        contentDescription = "Zoomed",
                        contentScale = ContentScale.None,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationDegrees.toFloat())
                            .scale(4.0f), // Magnified!
                        alpha = 0.9f
                    )
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Tiny target crosshair in magnifying glass
                        Box(modifier = Modifier.size(8.dp).border(1.dp, Color.Red, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun ToolbarTabItem(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = name,
            tint = if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            name,
            color = if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}

@Composable
fun DraggableCropOverlayCanvas(
    topLeftX: Float, topLeftY: Float,
    topRightX: Float, topRightY: Float,
    bottomRightX: Float, bottomRightY: Float,
    bottomLeftX: Float, bottomLeftY: Float,
    activeCorner: String?
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

        // Draw translucent dark shroud outside the cropping quad (to shade out discarded areas!)
        // Draw the inside lines
        drawPath(
            path = path,
            color = Color(0xFF00E676),
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw draggable anchor points
        val corners = listOf(
            Triple(p1, "TL", activeCorner == "TL"),
            Triple(p2, "TR", activeCorner == "TR"),
            Triple(p3, "BR", activeCorner == "BR"),
            Triple(p4, "BL", activeCorner == "BL")
        )

        for (corner in corners) {
            val point = corner.first
            val isActive = corner.third

            // Ripple glow
            drawCircle(
                color = Color(0xFF00E676).copy(alpha = if (isActive) 0.4f else 0.15f),
                radius = if (isActive) 24.dp.toPx() else 16.dp.toPx(),
                center = point
            )

            // Outer ring
            drawCircle(
                color = Color(0xFF00E676),
                radius = 10.dp.toPx(),
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner solid center dot
            drawCircle(
                color = if (isActive) Color.White else Color(0xFF00E676),
                radius = 5.dp.toPx(),
                center = point
            )
        }
    }
}
