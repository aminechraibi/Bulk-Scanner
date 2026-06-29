package com.example

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BulkScannerTests {

    private lateinit var app: Application
    private lateinit var database: ScannerDatabase
    private lateinit var repository: ScannerRepository
    private var viewModel: ScannerViewModel? = null
    private val testDispatcher = StandardTestDispatcher()

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
    }

    @After
    fun tearDown() {
        // Safe double-cancel to clean up any remaining VM coroutines
        try {
            viewModel?.viewModelScope?.cancel()
        } catch (e: Exception) {
            // Ignored
        }
        database.close()
        ScannerDatabase.setTestDatabase(null)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ScannerViewModel {
        val vm = ScannerViewModel(app, testDispatcher, SharingStarted.Eagerly, populateSampleData = true)
        viewModel = vm
        return vm
    }

    @Test
    fun testDefaultSampleBatchesLoaded() = runTest(testDispatcher) {
        println("[TEST] Starting testDefaultSampleBatchesLoaded")
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        val batches = viewModel.allBatches.value
        println("[TEST] Loaded batches: ${batches.map { it.name }}")
        
        assertTrue("Batches should not be empty", batches.isNotEmpty())
        assertTrue("Should contain sample Algebra midterm", batches.any { it.name.contains("Algebra") })
        assertTrue("Should contain sample Invoice", batches.any { it.name.contains("Invoice") })
        println("[TEST] testDefaultSampleBatchesLoaded passed successfully")
    }

    @Test
    fun testCreateNewBatchAndSelect() = runTest(testDispatcher) {
        println("[TEST] Starting testCreateNewBatchAndSelect")
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        val customName = "Tax Receipts 2026"
        val preset = "Document"
        
        viewModel.createNewBatch(customName, preset)
        advanceUntilIdle()
        
        val activeBatchId = viewModel.currentBatchId.value
        println("[TEST] Selected activeBatchId: $activeBatchId")
        assertNotNull(activeBatchId)
        
        val activeBatch = viewModel.currentBatch.value
        println("[TEST] Active batch metadata: ${activeBatch?.name}, pages: ${activeBatch?.pageCount}")
        assertNotNull(activeBatch)
        assertEquals(customName, activeBatch?.name)
        assertEquals(0, activeBatch?.pageCount)
        println("[TEST] testCreateNewBatchAndSelect passed successfully")
    }

    @Test
    fun testToggleProblemsFilter() = runTest(testDispatcher) {
        println("[TEST] Starting testToggleProblemsFilter")
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        val batches = viewModel.allBatches.value
        val sampleBatchId = batches.first().id
        println("[TEST] Selected batch: $sampleBatchId")
        
        viewModel.selectBatch(sampleBatchId)
        advanceUntilIdle()
        
        val allPages = viewModel.currentPages.value
        println("[TEST] All pages count: ${allPages.size}")
        assertTrue("There should be pages in the sample batch", allPages.isNotEmpty())
        
        assertFalse(viewModel.showOnlyProblems.value)
        assertEquals(allPages.size, viewModel.displayedPages.value.size)
        
        viewModel.toggleProblemsFilter()
        advanceUntilIdle()
        
        assertTrue(viewModel.showOnlyProblems.value)
        
        val warningPagesCount = allPages.count { it.status == "warning" || it.warningTypes.isNotEmpty() }
        println("[TEST] warningPagesCount: $warningPagesCount, displayed: ${viewModel.displayedPages.value.size}")
        assertEquals(warningPagesCount, viewModel.displayedPages.value.size)
        
        viewModel.toggleProblemsFilter()
        advanceUntilIdle()
        
        assertFalse(viewModel.showOnlyProblems.value)
        assertEquals(allPages.size, viewModel.displayedPages.value.size)
        println("[TEST] testToggleProblemsFilter passed successfully")
    }

    @Test
    fun testSavePageEditsAndUndo() = runTest(testDispatcher) {
        println("[TEST] Starting testSavePageEditsAndUndo")
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        val batches = viewModel.allBatches.value
        val sampleBatchId = batches.first().id
        viewModel.selectBatch(sampleBatchId)
        advanceUntilIdle()
        
        val pages = viewModel.currentPages.value
        val targetPage = pages.first()
        println("[TEST] Editing target page ID: ${targetPage.id}, initial rotation: ${targetPage.rotationDegrees}")
        
        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 90,
            topLeftX = 0.1f, topLeftY = 0.1f,
            topRightX = 0.9f, topRightY = 0.1f,
            bottomRightX = 0.9f, bottomRightY = 0.9f,
            bottomLeftX = 0.1f, bottomLeftY = 0.9f,
            enhancementPreset = "Black & White"
        )
        advanceUntilIdle()
        
        val updatedPages = viewModel.currentPages.value
        val updatedPage = updatedPages.first { it.id == targetPage.id }
        println("[TEST] Page updated. New rotation: ${updatedPage.rotationDegrees}")
        assertEquals(90, updatedPage.rotationDegrees)
        assertEquals("Black & White", updatedPage.enhancementPreset)
        assertTrue(updatedPage.manualEdits.contains("manual_edit"))
        assertTrue(viewModel.canUndo.value)
        
        viewModel.triggerUndo()
        advanceUntilIdle()
        
        val restoredPages = viewModel.currentPages.value
        val restoredPage = restoredPages.first { it.id == targetPage.id }
        println("[TEST] Undo triggered. Restored rotation: ${restoredPage.rotationDegrees}")
        assertEquals(targetPage.rotationDegrees, restoredPage.rotationDegrees)
        assertEquals(targetPage.enhancementPreset, restoredPage.enhancementPreset)
        println("[TEST] testSavePageEditsAndUndo passed successfully")
    }

    @Test
    fun testDeletePageAndUndo() = runTest(testDispatcher) {
        println("[TEST] Starting testDeletePageAndUndo")
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        val batches = viewModel.allBatches.value
        val sampleBatchId = batches.first().id
        viewModel.selectBatch(sampleBatchId)
        advanceUntilIdle()
        
        val initialPages = viewModel.currentPages.value
        val initialCount = initialPages.size
        val pageToDelete = initialPages.first()
        println("[TEST] Deleting page: ${pageToDelete.id}, initial count: $initialCount")
        
        viewModel.deletePage(pageToDelete)
        advanceUntilIdle()
        
        val pagesAfterDelete = viewModel.currentPages.value
        println("[TEST] After delete count: ${pagesAfterDelete.size}")
        assertEquals(initialCount - 1, pagesAfterDelete.size)
        
        for ((idx, p) in pagesAfterDelete.withIndex()) {
            assertEquals(idx + 1, p.pageNumber)
        }
        
        assertTrue(viewModel.canUndo.value)
        viewModel.triggerUndo()
        advanceUntilIdle()
        
        val restoredPages = viewModel.currentPages.value
        println("[TEST] After undo restore count: ${restoredPages.size}")
        assertEquals(initialCount, restoredPages.size)
        assertTrue(restoredPages.any { it.pageNumber == pageToDelete.pageNumber })
        println("[TEST] testDeletePageAndUndo passed successfully")
    }

    @Test
    fun testPageReordering() = runTest(testDispatcher) {
        println("[TEST] Starting testPageReordering")
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        val batches = viewModel.allBatches.value
        val sampleBatchId = batches.first().id
        viewModel.selectBatch(sampleBatchId)
        advanceUntilIdle()
        
        val initialPages = viewModel.currentPages.value
        println("[TEST] Initial pages for reorder: ${initialPages.size}")
        if (initialPages.size >= 2) {
            val temp = initialPages[0]
            val reordered = initialPages.toMutableList()
            reordered[0] = reordered[1]
            reordered[1] = temp
            
            viewModel.reorderPages(reordered)
            advanceUntilIdle()
            
            val resultPages = viewModel.currentPages.value
            println("[TEST] Reordered. First page original ID: ${temp.id}, new first page ID: ${resultPages[0].id}")
            assertEquals(1, resultPages[0].pageNumber)
            assertEquals(2, resultPages[1].pageNumber)
            assertEquals(initialPages[1].id, resultPages[0].id)
            assertEquals(temp.id, resultPages[1].id)
            
            viewModel.triggerUndo()
            advanceUntilIdle()
            
            val undonePages = viewModel.currentPages.value
            println("[TEST] Reorder Undo complete. First page number: ${undonePages[0].pageNumber}")
            assertEquals(1, undonePages[0].pageNumber)
            assertEquals(2, undonePages[1].pageNumber)
            assertEquals(initialPages[0].id, undonePages[0].id)
        }
        println("[TEST] testPageReordering passed successfully")
    }

    @Test
    fun testExportPdfAndZip() = runTest(testDispatcher) {
        println("[TEST] Starting testExportPdfAndZip")
        val viewModel = createViewModel()
        advanceUntilIdle()
        
        val batches = viewModel.allBatches.value
        val sampleBatchId = batches.first().id
        viewModel.selectBatch(sampleBatchId)
        advanceUntilIdle()
        
        // Export PDF
        var exportedPdfFile: File? = null
        viewModel.exportCurrentBatchPdf("ExportedTestDoc") { file ->
            exportedPdfFile = file
        }
        advanceUntilIdle()
        
        println("[TEST] PDF export state: ${viewModel.exportState.value}")
        assertTrue(viewModel.exportState.value is ExportStatus.Success)
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
        advanceUntilIdle()
        
        println("[TEST] ZIP export state: ${viewModel.exportState.value}")
        assertTrue(viewModel.exportState.value is ExportStatus.Success)
        assertNotNull(exportedZipFile)
        assertTrue(exportedZipFile!!.exists())
        assertTrue(exportedZipFile!!.name.endsWith(".zip"))
        println("[TEST] testExportPdfAndZip passed successfully")
    }
}
