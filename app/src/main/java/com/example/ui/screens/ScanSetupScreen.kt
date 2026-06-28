package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ScannerViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSetupScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (batchId: String, preset: String) -> Unit
) {
    var batchName by remember { mutableStateOf("") }
    var scanMode by remember { mutableStateOf("Bulk") } // Bulk or Single
    var captureMode by remember { mutableStateOf("Auto") } // Auto or Manual
    var enhancementPreset by remember { mutableStateOf("Document") } // Document, Handwriting, Color, Black & White, Low Light
    var exportFormat by remember { mutableStateOf("PDF") } // PDF, Images, Both

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner Setup", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117)
                )
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Batch Name Input
                Column {
                    Text(
                        "Batch Name (Optional)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = batchName,
                        onValueChange = { batchName = it },
                        placeholder = { 
                            val defaultName = "Batch " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            Text(defaultName, color = Color.White.copy(alpha = 0.3f)) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF1E2229),
                            unfocusedContainerColor = Color(0xFF1E2229)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Scan Mode
                SelectionGroup(
                    title = "Scan Mode",
                    options = listOf("Bulk", "Single"),
                    selectedOption = scanMode,
                    onOptionSelected = { scanMode = it },
                    tagPrefix = "scan_mode"
                )

                // Capture Mode
                SelectionGroup(
                    title = "Capture Mode",
                    options = listOf("Auto", "Manual"),
                    selectedOption = captureMode,
                    onOptionSelected = { captureMode = it },
                    tagPrefix = "capture_mode"
                )

                // Default Enhancement Preset
                SelectionGroup(
                    title = "Default Enhancement Preset",
                    options = listOf("Document", "Handwriting", "Color", "Black & White", "Low Light", "Original"),
                    selectedOption = enhancementPreset,
                    onOptionSelected = { enhancementPreset = it },
                    gridMode = true,
                    tagPrefix = "enhancement"
                )

                // Export Format
                SelectionGroup(
                    title = "Default Export Format",
                    options = listOf("PDF", "Images", "Both"),
                    selectedOption = exportFormat,
                    onOptionSelected = { exportFormat = it },
                    tagPrefix = "export_format"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start scanning button
            Button(
                onClick = {
                    val finalName = batchName.ifBlank {
                        "Scan ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
                    }
                    viewModel.createNewBatch(finalName, enhancementPreset)
                    
                    // We wait for currentBatchId to update and navigate
                    // But we can trigger immediate transition since viewModel handles active ID.
                    // Let's watch the flow or fetch the next ID. Since viewmodel does it instantly:
                    val bId = viewModel.currentBatchId.value
                    if (bId != null) {
                        onNavigateToCamera(bId, enhancementPreset)
                    } else {
                        // Quick fallback or just query current state
                        scope.launch {
                            viewModel.currentBatchId.collectFirst { activeId ->
                                onNavigateToCamera(activeId, enhancementPreset)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_scan_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Start Camera Scan",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// Suspend helper to grab first non-null batch ID
suspend fun StateFlow<String?>.collectFirst(action: (String) -> Unit) {
    this.filterNotNull().first { 
        action(it)
        true
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionGroup(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    gridMode: Boolean = false,
    tagPrefix: String = ""
) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (gridMode) {
            // Display options in a wrap grid-like Row flow
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    PillItem(
                        option = option,
                        isSelected = option == selectedOption,
                        onClick = { onOptionSelected(option) },
                        modifier = Modifier.testTag("${tagPrefix}_pill_$option")
                    )
                }
            }
        } else {
            // Horizontally aligned rounded pill group
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2229))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { option ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(if (option == selectedOption) Color(0xFF00E676) else Color.Transparent)
                            .clickable { onOptionSelected(option) }
                            .testTag("${tagPrefix}_option_$option"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            option,
                            color = if (option == selectedOption) Color.Black else Color.White.copy(alpha = 0.7f),
                            fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PillItem(
    option: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(if (isSelected) Color(0xFF00E676) else Color(0xFF1E2229))
            .border(
                1.dp,
                if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(32.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            option,
            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}
