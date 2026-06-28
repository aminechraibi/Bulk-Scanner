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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        
        repository = ScannerRepository(app, database.scannerDao())
        viewModel = ScannerViewModel(app)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        database.close()
        ScannerDatabase.setTestDatabase(null)
        Dispatchers.resetMain()
    }

    @Test
    fun testDefaultSampleBatchesLoaded() = runTest {
        // ViewModel init starts a coroutine that pre-populates sample batches
        // Wait for batches flow to emit
        val batches = viewModel.allBatches.first { it.isNotEmpty() }
        
        // Assert sample batches were loaded successfully
        assertTrue("Batches should not be empty", batches.isNotEmpty())
        assertTrue("Should contain sample Algebra midterm", batches.any { it.name.contains("Algebra") })
        assertTrue("Should contain sample Invoice", batches.any { it.name.contains("Invoice") })
    }

    @Test
    fun testCreateNewBatchAndSelect() = runTest {
        // Create custom batch
        val customName = "Tax Receipts 2026"
        val preset = "Document"
        
        viewModel.createNewBatch(customName, preset)
        
        // Wait for the batch ID selection to update
        val activeBatchId = viewModel.currentBatchId.first { it != null }
        assertNotNull(activeBatchId)
        
        // Query active batch metadata
        val activeBatch = viewModel.currentBatch.first { it != null }
        assertEquals(customName, activeBatch?.name)
        assertEquals(0, activeBatch?.pageCount)
    }

    @Test
    fun testToggleProblemsFilter() = runTest {
        // Grab pre-populated batches
        val batches = viewModel.allBatches.first { it.isNotEmpty() }
        val sampleBatchId = batches.first().id
        
        // Select sample batch
        viewModel.selectBatch(sampleBatchId)
        
        // Get all pages
        val allPages = viewModel.currentPages.first { it.isNotEmpty() }
        assertTrue("There should be pages in the sample batch", allPages.isNotEmpty())
        
        // Ensure showOnlyProblems is initially false
        assertFalse(viewModel.showOnlyProblems.value)
        assertEquals(allPages.size, viewModel.displayedPages.value.size)
        
        // Toggle the filter on
        viewModel.toggleProblemsFilter()
        assertTrue(viewModel.showOnlyProblems.value)
        
        // Verified problem count matches display list
        val warningPagesCount = allPages.count { it.status == "warning" || it.warningTypes.isNotEmpty() }
        assertEquals(warningPagesCount, viewModel.displayedPages.value.size)
        
        // Toggle filter off again
        viewModel.toggleProblemsFilter()
        assertFalse(viewModel.showOnlyProblems.value)
        assertEquals(allPages.size, viewModel.displayedPages.value.size)
    }

    @Test
    fun testSavePageEditsAndUndo() = runTest {
        val batches = viewModel.allBatches.first { it.isNotEmpty() }
        val sampleBatchId = batches.first().id
        viewModel.selectBatch(sampleBatchId)
        
        val pages = viewModel.currentPages.first { it.isNotEmpty() }
        val targetPage = pages.first()
        
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
        val updatedPage = viewModel.currentPages.first { it.first().rotationDegrees == 90 }.first()
        assertEquals(90, updatedPage.rotationDegrees)
        assertEquals("Black & White", updatedPage.enhancementPreset)
        assertTrue(updatedPage.manualEdits.contains("manual_edit"))
        assertTrue(viewModel.canUndo.value)
        
        // Trigger undo
        viewModel.triggerUndo()
        
        // Wait for page to restore original rotation
        val restoredPage = viewModel.currentPages.first { it.first().rotationDegrees == targetPage.rotationDegrees }.first()
        assertEquals(targetPage.rotationDegrees, restoredPage.rotationDegrees)
        assertEquals(targetPage.enhancementPreset, restoredPage.enhancementPreset)
    }

    @Test
    fun testDeletePageAndUndo() = runTest {
        val batches = viewModel.allBatches.first { it.isNotEmpty() }
        val sampleBatchId = batches.first().id
        viewModel.selectBatch(sampleBatchId)
        
        val initialPages = viewModel.currentPages.first { it.isNotEmpty() }
        val initialCount = initialPages.size
        val pageToDelete = initialPages.first()
        
        // Execute delete
        viewModel.deletePage(pageToDelete)
        
        // Wait for updated size
        val pagesAfterDelete = viewModel.currentPages.first { it.size == initialCount - 1 }
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
        assertEquals(initialCount, restoredPages.size)
        assertTrue(restoredPages.any { it.pageNumber == pageToDelete.pageNumber })
    }

    @Test
    fun testPageReordering() = runTest {
        val batches = viewModel.allBatches.first { it.isNotEmpty() }
        val sampleBatchId = batches.first().id
        viewModel.selectBatch(sampleBatchId)
        
        val initialPages = viewModel.currentPages.first { it.isNotEmpty() }
        if (initialPages.size >= 2) {
            // Swap first two pages
            val reordered = initialPages.toMutableList()
            val temp = reordered[0]
            reordered[0] = reordered[1]
            reordered[1] = temp
            
            viewModel.reorderPages(reordered)
            
            // Wait for reorder to persist
            val resultPages = viewModel.currentPages.first { it[0].id == temp.id }
            assertEquals(2, resultPages[0].pageNumber) // originally 1, now 2
            assertEquals(1, resultPages[1].pageNumber) // originally 2, now 1
            
            // Undo reordering
            viewModel.triggerUndo()
            val undonePages = viewModel.currentPages.first { it[0].id == initialPages[0].id }
            assertEquals(1, undonePages[0].pageNumber)
            assertEquals(2, undonePages[1].pageNumber)
        }
    }

    @Test
    fun testExportPdfAndZip() = runTest {
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
        assertNotNull(exportedZipFile)
        assertTrue(exportedZipFile!!.exists())
        assertTrue(exportedZipFile!!.name.endsWith(".zip"))
    }
}
