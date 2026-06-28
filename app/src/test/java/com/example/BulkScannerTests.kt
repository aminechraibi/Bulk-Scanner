package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BulkScannerTests {

    private lateinit var app: Application
    private lateinit var database: ScannerDatabase
    private lateinit var repository: ScannerRepository
    private lateinit var viewModel: ScannerViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        
        // Use an isolated, in-memory database with a synchronous executor to prevent flaky test failures and threading races
        database = Room.inMemoryDatabaseBuilder(app, ScannerDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        ScannerDatabase.setTestDatabase(database)
        
        repository = ScannerRepository(app, database.scannerDao(), testDispatcher)
        viewModel = ScannerViewModel(app, testDispatcher, SharingStarted.Eagerly)
    }

    @After
    fun tearDown() {
        // Safe double-cancel to clean up any remaining VM coroutines
        try {
            viewModel.viewModelScope.cancel()
        } catch (e: Exception) {
            // Ignored
        }
        database.close()
        ScannerDatabase.setTestDatabase(null)
        Dispatchers.resetMain()
    }

    @Test
    fun testDefaultSampleBatchesLoaded() = runTest(testDispatcher) {
        try {
            println("[TEST] Starting testDefaultSampleBatchesLoaded")
            // ViewModel init starts a coroutine that pre-populates sample batches
            // Wait for batches flow to emit
            val batches = viewModel.allBatches.first { it.isNotEmpty() }
            println("[TEST] Loaded batches: ${batches.map { it.name }}")
            
            // Assert sample batches were loaded successfully
            assertTrue("Batches should not be empty", batches.isNotEmpty())
            assertTrue("Should contain sample Algebra midterm", batches.any { it.name.contains("Algebra") })
            assertTrue("Should contain sample Invoice", batches.any { it.name.contains("Invoice") })
            println("[TEST] testDefaultSampleBatchesLoaded passed successfully")
        } finally {
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun testCreateNewBatchAndSelect() = runTest(testDispatcher) {
        try {
            println("[TEST] Starting testCreateNewBatchAndSelect")
            // Create custom batch
            val customName = "Tax Receipts 2026"
            val preset = "Document"
            
            viewModel.createNewBatch(customName, preset)
            
            // Wait for the batch ID selection to update
            val activeBatchId = viewModel.currentBatchId.first { it != null }
            println("[TEST] Selected activeBatchId: $activeBatchId")
            assertNotNull(activeBatchId)
            
            // Query active batch metadata
            val activeBatch = viewModel.currentBatch.first { it != null }
            println("[TEST] Active batch metadata: ${activeBatch?.name}, pages: ${activeBatch?.pageCount}")
            assertEquals(customName, activeBatch?.name)
            assertEquals(0, activeBatch?.pageCount)
            println("[TEST] testCreateNewBatchAndSelect passed successfully")
        } finally {
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun testToggleProblemsFilter() = runTest(testDispatcher) {
        try {
            println("[TEST] Starting testToggleProblemsFilter")
            // Grab pre-populated batches
            val batches = viewModel.allBatches.first { it.isNotEmpty() }
            val sampleBatchId = batches.first().id
            println("[TEST] Selected batch: $sampleBatchId")
            
            // Select sample batch
            viewModel.selectBatch(sampleBatchId)
            
            // Get all pages
            val allPages = viewModel.currentPages.first { it.isNotEmpty() }
            println("[TEST] All pages count: ${allPages.size}")
            assertTrue("There should be pages in the sample batch", allPages.isNotEmpty())
            
            // Ensure showOnlyProblems is initially false
            assertFalse(viewModel.showOnlyProblems.value)
            
            // Timing-safe verification of displayedPages emission
            val displayedPagesInitial = viewModel.displayedPages.first { it.size == allPages.size }
            assertEquals(allPages.size, displayedPagesInitial.size)
            
            // Toggle the filter on
            viewModel.toggleProblemsFilter()
            assertTrue(viewModel.showOnlyProblems.value)
            
            // Verified problem count matches display list (timing-safe!)
            val warningPagesCount = allPages.count { it.status == "warning" || it.warningTypes.isNotEmpty() }
            val displayedPagesFiltered = viewModel.displayedPages.first { it.size == warningPagesCount }
            println("[TEST] Warning pages count: $warningPagesCount, displayed: ${displayedPagesFiltered.size}")
            assertEquals(warningPagesCount, displayedPagesFiltered.size)
            
            // Toggle filter off again
            viewModel.toggleProblemsFilter()
            assertFalse(viewModel.showOnlyProblems.value)
            
            val displayedPagesFinal = viewModel.displayedPages.first { it.size == allPages.size }
            assertEquals(allPages.size, displayedPagesFinal.size)
            println("[TEST] testToggleProblemsFilter passed successfully")
        } finally {
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun testSavePageEditsAndUndo() = runTest(testDispatcher) {
        try {
            println("[TEST] Starting testSavePageEditsAndUndo")
            val batches = viewModel.allBatches.first { it.isNotEmpty() }
            val sampleBatchId = batches.first().id
            viewModel.selectBatch(sampleBatchId)
            
            val pages = viewModel.currentPages.first { it.isNotEmpty() }
            val targetPage = pages.first()
            println("[TEST] Editing target page ID: ${targetPage.id}, initial rotation: ${targetPage.rotationDegrees}")
            
            // Edit page presets & crop coordinates
            viewModel.savePageEdits(
                page = targetPage,
                rotationDegrees = 90,
                topLeftX = 0.1f, topLeftY = 0.1f,
                topRightX = 0.9f, topRightY = 0.1f,
                bottomRightX = 0.9f, bottomRightY = 0.9f,
                bottomLeftX = 0.1f, bottomLeftY = 0.9f,
                enhancementPreset = "Black & White"
            )
            
            // Wait for update
            val updatedPage = viewModel.currentPages.first { it.isNotEmpty() && it.first().rotationDegrees == 90 }.first()
            println("[TEST] Page updated. New rotation: ${updatedPage.rotationDegrees}")
            assertEquals(90, updatedPage.rotationDegrees)
            assertEquals("Black & White", updatedPage.enhancementPreset)
            assertTrue(updatedPage.manualEdits.contains("manual_edit"))
            assertTrue(viewModel.canUndo.value)
            
            // Trigger undo
            viewModel.triggerUndo()
            
            // Wait for page to restore original rotation
            val restoredPage = viewModel.currentPages.first { it.isNotEmpty() && it.first().rotationDegrees == targetPage.rotationDegrees }.first()
            println("[TEST] Undo triggered. Restored rotation: ${restoredPage.rotationDegrees}")
            assertEquals(targetPage.rotationDegrees, restoredPage.rotationDegrees)
            assertEquals(targetPage.enhancementPreset, restoredPage.enhancementPreset)
            println("[TEST] testSavePageEditsAndUndo passed successfully")
        } finally {
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun testDeletePageAndUndo() = runTest(testDispatcher) {
        try {
            println("[TEST] Starting testDeletePageAndUndo")
            val batches = viewModel.allBatches.first { it.isNotEmpty() }
            val sampleBatchId = batches.first().id
            viewModel.selectBatch(sampleBatchId)
            
            val initialPages = viewModel.currentPages.first { it.isNotEmpty() }
            val initialCount = initialPages.size
            val pageToDelete = initialPages.first()
            println("[TEST] Deleting page: ${pageToDelete.id}, initial count: $initialCount")
            
            // Execute delete
            viewModel.deletePage(pageToDelete)
            
            // Wait for updated size
            val pagesAfterDelete = viewModel.currentPages.first { it.size == initialCount - 1 }
            println("[TEST] After delete count: ${pagesAfterDelete.size}")
            assertEquals(initialCount - 1, pagesAfterDelete.size)
            
            // Verify page numbers slid down appropriately
            for ((idx, p) in pagesAfterDelete.withIndex()) {
                assertEquals(idx + 1, p.pageNumber)
            }
            
            // Trigger Undo delete
            assertTrue(viewModel.canUndo.value)
            viewModel.triggerUndo()
            
            // Wait for restoration
            val restoredPages = viewModel.currentPages.first { it.size == initialCount }
            println("[TEST] After undo restore count: ${restoredPages.size}")
            assertEquals(initialCount, restoredPages.size)
            assertTrue(restoredPages.any { it.pageNumber == pageToDelete.pageNumber })
            println("[TEST] testDeletePageAndUndo passed successfully")
        } finally {
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun testPageReordering() = runTest(testDispatcher) {
        try {
            println("[TEST] Starting testPageReordering")
            val batches = viewModel.allBatches.first { it.isNotEmpty() }
            val sampleBatchId = batches.first().id
            viewModel.selectBatch(sampleBatchId)
            
            val initialPages = viewModel.currentPages.first { it.isNotEmpty() }
            println("[TEST] Initial pages for reorder: ${initialPages.size}")
            if (initialPages.size >= 2) {
                // Swap first two pages
                val reordered = initialPages.toMutableList()
                val temp = reordered[0]
                reordered[0] = reordered[1]
                reordered[1] = temp
                
                viewModel.reorderPages(reordered)
                
                // Wait for reorder to persist (since they are ORDER BY pageNumber ASC, temp which became second page will be at index 1)
                val resultPages = viewModel.currentPages.first { it.isNotEmpty() && it.size >= 2 && it[1].id == temp.id }
                println("[TEST] Reordered. First page original ID: ${temp.id}, new first page ID: ${resultPages[0].id}")
                assertEquals(1, resultPages[0].pageNumber)
                assertEquals(2, resultPages[1].pageNumber)
                assertEquals(initialPages[1].id, resultPages[0].id)
                assertEquals(temp.id, resultPages[1].id)
                
                // Undo reordering
                viewModel.triggerUndo()
                val undonePages = viewModel.currentPages.first { it.isNotEmpty() && it[0].id == initialPages[0].id }
                println("[TEST] Reorder Undo complete. First page number: ${undonePages[0].pageNumber}")
                assertEquals(1, undonePages[0].pageNumber)
                assertEquals(2, undonePages[1].pageNumber)
            }
            println("[TEST] testPageReordering passed successfully")
        } finally {
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun testExportPdfAndZip() = runTest(testDispatcher) {
        try {
            println("[TEST] Starting testExportPdfAndZip")
            val batches = viewModel.allBatches.first { it.isNotEmpty() }
            val sampleBatchId = batches.first().id
            viewModel.selectBatch(sampleBatchId)
            
            // Export PDF
            var exportedPdfFile: File? = null
            viewModel.exportCurrentBatchPdf("ExportedTestDoc") { file ->
                exportedPdfFile = file
            }
            
            // Wait for export state to succeed
            viewModel.exportState.first { it is ExportStatus.Success }
            println("[TEST] PDF export state success. File: ${exportedPdfFile?.name}, size: ${exportedPdfFile?.length()}")
            assertNotNull(exportedPdfFile)
            assertTrue(exportedPdfFile!!.exists())
            assertTrue(exportedPdfFile!!.name.endsWith(".pdf"))
            
            viewModel.clearExportState()
            assertEquals(ExportStatus.Idle, viewModel.exportState.value)
            
            // Export ZIP
            var exportedZipFile: File? = null
            viewModel.exportCurrentBatchZip("ExportedTestArchive") { file ->
                exportedZipFile = file
            }
            
            viewModel.exportState.first { it is ExportStatus.Success }
            println("[TEST] ZIP export state success. File: ${exportedZipFile?.name}, size: ${exportedZipFile?.length()}")
            assertNotNull(exportedZipFile)
            assertTrue(exportedZipFile!!.exists())
            assertTrue(exportedZipFile!!.name.endsWith(".zip"))
            println("[TEST] testExportPdfAndZip passed successfully")
        } finally {
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()
        }
    }
}
