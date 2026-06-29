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
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BulkScannerFlowStateTests {

    private lateinit var app: Application
    private lateinit var database: ScannerDatabase
    private lateinit var repository: ScannerRepository
    private var viewModel: ScannerViewModel? = null
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        
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
    fun testInitialAllBatchesEmission() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val batches = viewModel.allBatches.value
        assertEquals(2, batches.size) // pre-populated with 2 sample batches
        assertEquals("Algebra Midterm Papers", batches[0].name)
        assertEquals("Receipts - June 2026", batches[1].name)
    }

    @Test
    fun testInitialCurrentBatchBeforeSelection() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.currentBatchId.value)
        assertNull(viewModel.currentBatch.value)
    }

    @Test
    fun testInitialCurrentPagesBeforeSelection() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.currentPages.value.isEmpty())
    }

    @Test
    fun testInitialDisplayedPagesBeforeSelection() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.displayedPages.value.isEmpty())
    }

    @Test
    fun testToggleFilterBeforeSelectingBatch() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.showOnlyProblems.value)

        viewModel.toggleProblemsFilter()
        advanceUntilIdle()

        assertTrue(viewModel.showOnlyProblems.value)
        assertTrue(viewModel.displayedPages.value.isEmpty()) // Empty since no batch selected
    }

    @Test
    fun testRapidFilterToggleFinalState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.showOnlyProblems.value)

        // Toggle 5 times rapidly
        viewModel.toggleProblemsFilter()
        viewModel.toggleProblemsFilter()
        viewModel.toggleProblemsFilter()
        viewModel.toggleProblemsFilter()
        viewModel.toggleProblemsFilter()
        advanceUntilIdle()

        assertTrue(viewModel.showOnlyProblems.value)
    }

    @Test
    fun testMultipleCollectorsOnCurrentPagesAndDisplayedPages() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val collectedPages1 = mutableListOf<List<PageEntity>>()
        val collectedPages2 = mutableListOf<List<PageEntity>>()

        val job1 = launch {
            viewModel.currentPages.collect {
                collectedPages1.add(it)
            }
        }
        val job2 = launch {
            viewModel.displayedPages.collect {
                collectedPages2.add(it)
            }
        }

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        assertFalse(collectedPages1.isEmpty())
        assertFalse(collectedPages2.isEmpty())
        
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun testRapidDeleteReorderToggleFinalState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val initialPages = viewModel.currentPages.value
        assertTrue(initialPages.size >= 2)

        // Rapid delete and reorder
        viewModel.deletePage(initialPages[0])
        viewModel.reorderPages(viewModel.currentPages.value.reversed())
        advanceUntilIdle()

        val currentPages = viewModel.currentPages.value
        assertEquals(initialPages.size - 1, currentPages.size)
    }

    @Test
    fun testExportStateClearReturnsIdle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        // Trigger an export
        viewModel.exportCurrentBatchPdf("test_export") {}
        advanceUntilIdle()

        assertTrue(viewModel.exportState.value is ExportStatus.Success)

        // Clear export state
        viewModel.clearExportState()
        advanceUntilIdle()

        assertEquals(ExportStatus.Idle, viewModel.exportState.value)
    }
}
