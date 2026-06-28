package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ScannerDatabase.getDatabase(application)
    private val repository = ScannerRepository(application, database.scannerDao())

    // All historic batches
    val allBatches: StateFlow<List<BatchEntity>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active batch details
    private val _currentBatchId = MutableStateFlow<String?>(null)
    val currentBatchId: StateFlow<String?> = _currentBatchId.asStateFlow()

    private val _currentBatch = MutableStateFlow<BatchEntity?>(null)
    val currentBatch: StateFlow<BatchEntity?> = _currentBatch.asStateFlow()

    // Pages list for the currently active/selected batch
    val currentPages: StateFlow<List<PageEntity>> = _currentBatchId
        .flatMapLatest { id ->
            if (id != null) repository.getPagesForBatch(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Problem filter state (Show only pages with warnings/errors)
    private val _showOnlyProblems = MutableStateFlow(false)
    val showOnlyProblems: StateFlow<Boolean> = _showOnlyProblems.asStateFlow()

    // Filtered list of pages for the review grid
    val displayedPages: StateFlow<List<PageEntity>> = combine(currentPages, _showOnlyProblems) { pages, onlyProblems ->
        if (onlyProblems) {
            pages.filter { it.status == "warning" || it.warningTypes.isNotEmpty() }
        } else {
            pages
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Page currently being edited in Page Editor Screen
    private val _editingPage = MutableStateFlow<PageEntity?>(null)
    val editingPage: StateFlow<PageEntity?> = _editingPage.asStateFlow()

    // Export status tracking
    private val _exportState = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportState: StateFlow<ExportStatus> = _exportState.asStateFlow()

    // Undo actions stack (Last action stored as lambda)
    private val undoStack = mutableListOf<suspend () -> Unit>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    init {
        // Pre-populate with sample batches if database is empty on first launch
        viewModelScope.launch {
            allBatches.first() // wait for first emission
            if (allBatches.value.isEmpty()) {
                createSampleBatch()
            }
        }
    }

    private suspend fun createSampleBatch() = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        
        // 1. Algebra Exam Sample Batch
        val batchId1 = "sample_batch_001"
        val batch1 = BatchEntity(
            id = batchId1,
            name = "Algebra Midterm Papers",
            createdAt = System.currentTimeMillis() - 86400000 * 2, // 2 days ago
            pageCount = 3,
            isExported = false
        )
        repository.insertBatch(batch1)

        val docTypes = listOf("math_exam", "written_notes", "grid_chart")
        for ((idx, docId) in docTypes.withIndex()) {
            val pageNum = idx + 1
            val sampleFile = SampleDocumentGenerator.generateSampleDocFile(app, docId)
            
            val originalDir = File(app.filesDir, "batches/$batchId1/originals")
            originalDir.mkdirs()
            val originalFile = File(originalDir, "page_${String.format("%03d", pageNum)}.jpg")
            sampleFile.copyTo(originalFile, overwrite = true)

            // Let's simulate a slight warning on the second page to demonstrate the problem filter
            val isWarning = pageNum == 2
            val warningString = if (isWarning) "crop_uncertain" else ""
            val statusString = if (isWarning) "warning" else "good"

            // Slightly custom cropping quad corners to show adjustable points
            val topLeftX = if (isWarning) 0.15f else 0.05f
            val topLeftY = if (isWarning) 0.10f else 0.05f

            val pageEntity = PageEntity(
                batchId = batchId1,
                pageNumber = pageNum,
                originalPath = originalFile.absolutePath,
                processedPath = File(app.filesDir, "batches/$batchId1/processed/page_${String.format("%03d", pageNum)}.jpg").absolutePath,
                status = statusString,
                warningTypes = warningString,
                topLeftX = topLeftX, topLeftY = topLeftY,
                enhancementPreset = "Document"
            )
            // Generate processed
            repository.updatePageSettings(pageEntity)
        }

        // 2. Corporate Receipts Sample Batch
        val batchId2 = "sample_batch_002"
        val batch2 = BatchEntity(
            id = batchId2,
            name = "Office Expenses Invoice",
            createdAt = System.currentTimeMillis() - 3600000 * 4, // 4 hours ago
            pageCount = 2,
            isExported = false
        )
        repository.insertBatch(batch2)

        val docTypes2 = listOf("office_invoice", "sketchbook")
        for ((idx, docId) in docTypes2.withIndex()) {
            val pageNum = idx + 1
            val sampleFile = SampleDocumentGenerator.generateSampleDocFile(app, docId)
            
            val originalDir = File(app.filesDir, "batches/$batchId2/originals")
            originalDir.mkdirs()
            val originalFile = File(originalDir, "page_${String.format("%03d", pageNum)}.jpg")
            sampleFile.copyTo(originalFile, overwrite = true)

            val pageEntity = PageEntity(
                batchId = batchId2,
                pageNumber = pageNum,
                originalPath = originalFile.absolutePath,
                processedPath = File(app.filesDir, "batches/$batchId2/processed/page_${String.format("%03d", pageNum)}.jpg").absolutePath,
                status = "good",
                enhancementPreset = "Handwriting"
            )
            // Generate processed
            repository.updatePageSettings(pageEntity)
        }
    }

    fun selectBatch(batchId: String?) {
        _currentBatchId.value = batchId
        if (batchId != null) {
            viewModelScope.launch {
                _currentBatch.value = repository.getBatchById(batchId)
            }
        } else {
            _currentBatch.value = null
        }
    }

    fun toggleProblemsFilter() {
        _showOnlyProblems.value = !_showOnlyProblems.value
    }

    fun setEditingPage(page: PageEntity?) {
        _editingPage.value = page
    }

    // Create a new empty scan batch
    fun createNewBatch(customName: String, preset: String) {
        val id = "batch_" + UUID.randomUUID().toString().take(8)
        val name = customName.ifBlank { "Batch ${System.currentTimeMillis() / 1000}" }
        val batch = BatchEntity(id = id, name = name, isExported = false)
        
        viewModelScope.launch {
            repository.insertBatch(batch)
            selectBatch(id)
        }
    }

    // Fast action to start a default scan batch
    fun startDefaultBatch() {
        val id = "batch_" + UUID.randomUUID().toString().take(8)
        val batch = BatchEntity(id = id, name = "My Document Scan", isExported = false)
        viewModelScope.launch {
            repository.insertBatch(batch)
            selectBatch(id)
        }
    }

    // Capture/scan action
    fun addPageToCurrentBatch(tempFile: File, preset: String, onComplete: (PageEntity) -> Unit = {}) {
        val bId = _currentBatchId.value ?: return
        viewModelScope.launch {
            val page = repository.addNewPageToBatch(
                batchId = bId,
                tempOriginalFile = tempFile,
                enhancementPreset = preset
            )
            // Refresh batch details
            _currentBatch.value = repository.getBatchById(bId)
            onComplete(page)
        }
    }

    // Save changes from Page Editor Screen
    fun savePageEdits(
        page: PageEntity,
        rotationDegrees: Int,
        topLeftX: Float, topLeftY: Float,
        topRightX: Float, topRightY: Float,
        bottomRightX: Float, bottomRightY: Float,
        bottomLeftX: Float, bottomLeftY: Float,
        enhancementPreset: String
    ) {
        viewModelScope.launch {
            val originalPageSnapshot = page.copy() // For Undo support
            
            val updatedPage = page.copy(
                rotationDegrees = rotationDegrees,
                topLeftX = topLeftX, topLeftY = topLeftY,
                topRightX = topRightX, topRightY = topRightY,
                bottomRightX = bottomRightX, bottomRightY = bottomRightY,
                bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY,
                enhancementPreset = enhancementPreset,
                manualEdits = (page.manualEdits.split(",").filter { it.isNotEmpty() } + "manual_edit").distinct().joinToString(",")
            )
            
            repository.updatePageSettings(updatedPage)
            
            // Push undo action
            pushUndo {
                repository.updatePageSettings(originalPageSnapshot)
            }
            
            // Refresh editor focus
            _editingPage.value = updatedPage
        }
    }

    // Delete page with Undo support
    fun deletePage(page: PageEntity) {
        viewModelScope.launch {
            val originalPagesList = currentPages.value.toList() // For exact restoration
            
            repository.deletePageFromBatch(page)
            
            // Re-fetch batch to refresh counts
            _currentBatchId.value?.let { bId ->
                _currentBatch.value = repository.getBatchById(bId)
            }

            pushUndo {
                // To undo delete, we truncate database, restore list
                val bId = page.batchId
                val dao = database.scannerDao()
                dao.deletePagesForBatch(bId)
                dao.insertPages(originalPagesList)
                // Copy physical files if needed, but since we didn't delete physical files or can just write them
                // Refresh batch
                _currentBatch.value = repository.getBatchById(bId)
            }
        }
    }

    // Page replace action
    fun replacePage(page: PageEntity, newTempFile: File) {
        viewModelScope.launch {
            val originalFileBackup = File(page.originalPath).readBytes() // Keep original in memory for undo
            val updatedPage = repository.replacePageImage(page, newTempFile)
            _editingPage.value = updatedPage

            pushUndo {
                val origFile = File(page.originalPath)
                FileOutputStream(origFile).use { it.write(originalFileBackup) }
                repository.updatePageSettings(page)
            }
        }
    }

    // Reorder pages with Undo support
    fun reorderPages(reorderedList: List<PageEntity>) {
        val bId = _currentBatchId.value ?: return
        viewModelScope.launch {
            val originalOrder = currentPages.value.toList()
            repository.reorderPages(bId, reorderedList)

            pushUndo {
                repository.reorderPages(bId, originalOrder)
            }
        }
    }

    // Batch deletion
    fun deleteBatch(batchId: String) {
        viewModelScope.launch {
            repository.deleteBatch(batchId)
            if (_currentBatchId.value == batchId) {
                selectBatch(null)
            }
        }
    }

    // Undo management
    private fun pushUndo(action: suspend () -> Unit) {
        undoStack.add(action)
        _canUndo.value = true
    }

    fun triggerUndo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.lastIndex)
            _canUndo.value = undoStack.isNotEmpty()
            viewModelScope.launch {
                action()
                // Refresh active batch page lists
                _currentBatchId.value?.let { bId ->
                    _currentBatch.value = repository.getBatchById(bId)
                }
            }
        }
    }

    // Export PDF
    fun exportCurrentBatchPdf(fileName: String, onComplete: (File) -> Unit) {
        val bId = _currentBatchId.value ?: return
        _exportState.value = ExportStatus.Loading
        viewModelScope.launch {
            try {
                val file = repository.exportBatchAsPdf(bId, fileName)
                _exportState.value = ExportStatus.Success(file, "PDF")
                onComplete(file)
            } catch (e: Exception) {
                _exportState.value = ExportStatus.Error(e.message ?: "PDF compilation failed")
            }
        }
    }

    // Export ZIP
    fun exportCurrentBatchZip(fileName: String, onComplete: (File) -> Unit) {
        val bId = _currentBatchId.value ?: return
        _exportState.value = ExportStatus.Loading
        viewModelScope.launch {
            try {
                val file = repository.exportBatchAsZip(bId, fileName)
                _exportState.value = ExportStatus.Success(file, "ZIP Archive")
                onComplete(file)
            } catch (e: Exception) {
                _exportState.value = ExportStatus.Error(e.message ?: "ZIP compression failed")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportStatus.Idle
    }
}

sealed interface ExportStatus {
    object Idle : ExportStatus
    object Loading : ExportStatus
    data class Success(val file: File, val format: String) : ExportStatus
    data class Error(val message: String) : ExportStatus
}
