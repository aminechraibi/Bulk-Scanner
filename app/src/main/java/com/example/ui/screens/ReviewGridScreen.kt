package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.PageEntity
import com.example.ui.viewmodel.ExportStatus
import com.example.ui.viewmodel.ScannerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewGridScreen(
    viewModel: ScannerViewModel,
    batchId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (batchId: String, preset: String) -> Unit,
    onNavigateToEditor: (pageId: Long) -> Unit
) {
    val context = LocalContext.current
    val currentBatch by viewModel.currentBatch.collectAsState()
    val pages by viewModel.displayedPages.collectAsState()
    val allPagesRaw by viewModel.currentPages.collectAsState()
    val showProblemsOnly by viewModel.showOnlyProblems.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var showExportSheet by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf("") }
    var exportFormat by remember { mutableStateOf("PDF") } // PDF, ZIP

    // Sync default export file name
    LaunchedEffect(currentBatch) {
        currentBatch?.let {
            exportFileName = it.name.replace(" ", "_")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(currentBatch?.name ?: "Review Pages", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text("${allPagesRaw.size} Pages total", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Undo trigger
                    if (canUndo) {
                        IconButton(onClick = { viewModel.triggerUndo() }) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color(0xFF00E676))
                        }
                    }

                    // Problems Filter Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.toggleProblemsFilter() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Problems Only",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showProblemsOnly) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = showProblemsOnly,
                            onCheckedChange = { viewModel.toggleProblemsFilter() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF00E676),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                uncheckedTrackColor = Color(0xFF1E2229)
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1117))
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Large Export Button
                if (allPagesRaw.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showExportSheet = true },
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.testTag("export_batch_fab")
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export Batch")
                    }
                }

                // Add Page Button
                FloatingActionButton(
                    onClick = { onNavigateToCamera(batchId, "Document") },
                    containerColor = Color(0xFF00B0FF),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("add_page_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Page")
                }
            }
        },
        containerColor = Color(0xFF0D1117)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (showProblemsOnly) Icons.Outlined.CheckCircleOutline else Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (showProblemsOnly) "No Problems Detected!" else "No Pages Scanned Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (showProblemsOnly) "All pages look great! No blur, bad crops, or dark shadows found."
                            else "Tap the blue button to scan pages or add them.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(280.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 12.dp)
                ) {
                    items(pages, key = { it.id }) { page ->
                        PageGridCard(
                            page = page,
                            onEditClick = {
                                viewModel.setEditingPage(page)
                                onNavigateToEditor(page.id)
                            },
                            onDeleteClick = {
                                viewModel.deletePage(page)
                                Toast.makeText(context, "Page ${page.pageNumber} deleted. (Tap Undo in top bar to restore)", Toast.LENGTH_SHORT).show()
                            },
                            onMoveLeft = {
                                val index = allPagesRaw.indexOfFirst { it.id == page.id }
                                if (index > 0) {
                                    val mutableList = allPagesRaw.toMutableList()
                                    val temp = mutableList[index]
                                    mutableList[index] = mutableList[index - 1]
                                    mutableList[index - 1] = temp
                                    viewModel.reorderPages(mutableList)
                                }
                            },
                            onMoveRight = {
                                val index = allPagesRaw.indexOfFirst { it.id == page.id }
                                if (index < allPagesRaw.size - 1 && index >= 0) {
                                    val mutableList = allPagesRaw.toMutableList()
                                    val temp = mutableList[index]
                                    mutableList[index] = mutableList[index + 1]
                                    mutableList[index + 1] = temp
                                    viewModel.reorderPages(mutableList)
                                }
                            },
                            isFirst = allPagesRaw.firstOrNull()?.id == page.id,
                            isLast = allPagesRaw.lastOrNull()?.id == page.id
                        )
                    }
                }
            }

            // 6. Fast Manual Review Mode CTA
            val warningPagesCount = allPagesRaw.count { it.status == "warning" || it.warningTypes.isNotEmpty() }
            if (warningPagesCount > 0 && !showProblemsOnly) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE74C3C).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFE74C3C).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable {
                                // Jump directly to edit the first problematic page!
                                val firstProblem = allPagesRaw.firstOrNull { it.status == "warning" || it.warningTypes.isNotEmpty() }
                                firstProblem?.let {
                                    viewModel.setEditingPage(it)
                                    onNavigateToEditor(it.id)
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE74C3C))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("$warningPagesCount Warnings Found", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Blur or crop uncertainties detected.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fix Warnings", color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFE74C3C), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Export bottom sheet popup
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showExportSheet = false
                viewModel.clearExportState()
            },
            containerColor = Color(0xFF1E2229),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Compile and Export Batch",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Export File Name Input
                OutlinedTextField(
                    value = exportFileName,
                    onValueChange = { exportFileName = it },
                    label = { Text("Export File Name", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF13171D),
                        unfocusedContainerColor = Color(0xFF13171D)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Select Export Format Block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D1117)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("PDF", "ZIP Archive").forEach { format ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(if (format == exportFormat) Color(0xFF00E676) else Color.Transparent)
                                .clickable { exportFormat = format }
                                .testTag("export_format_$format"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                format,
                                color = if (format == exportFormat) Color.Black else Color.White.copy(alpha = 0.7f),
                                fontWeight = if (format == exportFormat) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action triggers
                when (val state = exportState) {
                    is ExportStatus.Idle -> {
                        Button(
                            onClick = {
                                val cleanName = exportFileName.ifBlank { "scan_${System.currentTimeMillis()}" }
                                if (exportFormat == "PDF") {
                                    viewModel.exportCurrentBatchPdf(cleanName) { file ->
                                        Toast.makeText(context, "Exported successfully to: ${file.name}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    viewModel.exportCurrentBatchZip(cleanName) { file ->
                                        Toast.makeText(context, "ZIP Compiled successfully: ${file.name}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("confirm_export_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compile & Export", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    is ExportStatus.Loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00E676))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Compiling documents... please wait.", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    is ExportStatus.Success -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Compilation Success!", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Text("Saved as: ${state.file.name}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                            Text("File Path: ${state.file.absolutePath}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { showExportSheet = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Done", color = Color.Black)
                            }
                        }
                    }
                    is ExportStatus.Error -> {
                        Text("Error occurred: ${state.message}", color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PageGridCard(
    page: PageEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val file = File(page.processedPath)
    val hasWarning = page.status == "warning" || page.warningTypes.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (hasWarning) Color(0xFFE74C3C).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2229))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Page processed image thumbnail preview
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "Page Preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onEditClick() }
                )
            } else {
                // Fallback placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121417)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(48.dp))
                }
            }

            // Dark translucent overlay at top and bottom to make controls crisp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )

            // Page Number Badge & Status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "pg ${page.pageNumber}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Check / Warn badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasWarning) Color(0xFFE74C3C) else Color(0xFF00E676)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (hasWarning) Icons.Default.PriorityHigh else Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Bottom action bar (Shift left/right, edit, delete)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reorder shifts buttons
                Row {
                    IconButton(
                        onClick = onMoveLeft,
                        enabled = !isFirst,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Move Left",
                            tint = if (isFirst) Color.White.copy(alpha = 0.2f) else Color.White
                        )
                    }
                    IconButton(
                        onClick = onMoveRight,
                        enabled = !isLast,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Move Right",
                            tint = if (isLast) Color.White.copy(alpha = 0.2f) else Color.White
                        )
                    }
                }

                // Edit and Delete
                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit page", tint = Color.White)
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete page", tint = Color(0xFFE74C3C))
                    }
                }
            }
        }
    }
}
