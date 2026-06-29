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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BulkScannerUndoTests {

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
    fun testUndoWhenNothingToUndoDoesNothing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.canUndo.value)
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun testUndoTwiceAfterSingleEdit() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()
        val originalRotation = targetPage.rotationDegrees

        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 180,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Grayscale"
        )
        advanceUntilIdle()

        assertTrue(viewModel.canUndo.value)

        // First Undo
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)

        val checkPage1 = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(originalRotation, checkPage1.rotationDegrees)

        // Second Undo (Should be a no-op)
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun testUndoTwiceAfterSingleDelete() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val initialPages = viewModel.currentPages.value
        val pageToDelete = initialPages.first()

        viewModel.deletePage(pageToDelete)
        advanceUntilIdle()

        assertTrue(viewModel.canUndo.value)
        assertEquals(initialPages.size - 1, viewModel.currentPages.value.size)

        // First Undo (restores page)
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)
        assertEquals(initialPages.size, viewModel.currentPages.value.size)

        // Second Undo
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)
        assertEquals(initialPages.size, viewModel.currentPages.value.size)
    }

    @Test
    fun testUndoTwiceAfterSingleReorder() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val initialPages = viewModel.currentPages.value
        if (initialPages.size >= 2) {
            val reordered = initialPages.reversed()
            viewModel.reorderPages(reordered)
            advanceUntilIdle()

            assertTrue(viewModel.canUndo.value)

            // First Undo
            viewModel.triggerUndo()
            advanceUntilIdle()
            assertFalse(viewModel.canUndo.value)
            assertEquals(initialPages.first().id, viewModel.currentPages.value.first().id)

            // Second Undo
            viewModel.triggerUndo()
            advanceUntilIdle()
            assertFalse(viewModel.canUndo.value)
        }
    }

    @Test
    fun testMultiStepUndoEditDeleteReorder() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        // 1. Edit page
        val initialPages = viewModel.currentPages.value
        val firstPage = initialPages[0]
        val originalRotation = firstPage.rotationDegrees

        viewModel.savePageEdits(
            page = firstPage,
            rotationDegrees = 90,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        // 2. Delete page
        val secondPage = viewModel.currentPages.value[1]
        viewModel.deletePage(secondPage)
        advanceUntilIdle()

        // 3. Reorder pages (we have 2 remaining pages now in our sample midterm)
        val pagesAfterDelete = viewModel.currentPages.value
        val reversedPages = pagesAfterDelete.reversed()
        viewModel.reorderPages(reversedPages)
        advanceUntilIdle()

        assertTrue(viewModel.canUndo.value)

        // Undo 3: Reorder
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertTrue(viewModel.canUndo.value)
        assertEquals(pagesAfterDelete.first().id, viewModel.currentPages.value.first().id)

        // Undo 2: Delete
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertTrue(viewModel.canUndo.value)
        assertEquals(initialPages.size, viewModel.currentPages.value.size)

        // Undo 1: Edit
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)
        val finalFirstPage = viewModel.currentPages.value.first { it.id == firstPage.id }
        assertEquals(originalRotation, finalFirstPage.rotationDegrees)
    }

    @Test
    fun testCanUndoFalseAfterUndoHistoryConsumed() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val page = viewModel.currentPages.value.first()

        // Make two separate edits
        viewModel.savePageEdits(page, 90, 0f,0f, 1f,0f, 1f,1f, 0f,1f, "Original")
        advanceUntilIdle()
        viewModel.savePageEdits(page, 180, 0f,0f, 1f,0f, 1f,1f, 0f,1f, "Original")
        advanceUntilIdle()

        assertTrue(viewModel.canUndo.value)

        // Undo 1
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertTrue(viewModel.canUndo.value)

        // Undo 2
        viewModel.triggerUndo()
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun testExportDoesNotCreateUndoAction() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        assertFalse(viewModel.canUndo.value)

        viewModel.exportCurrentBatchPdf("DoNotUndo") {}
        advanceUntilIdle()

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun testUndoAfterSwitchingBatchIsSafe() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val batches = viewModel.allBatches.value
        val batchA = batches[0]
        val batchB = batches[1]

        viewModel.selectBatch(batchA.id)
        advanceUntilIdle()

        val pageA = viewModel.currentPages.value.first()
        val originalRotation = pageA.rotationDegrees

        // Edit page in Batch A
        viewModel.savePageEdits(pageA, 270, 0f,0f, 1f,0f, 1f,1f, 0f,1f, "Original")
        advanceUntilIdle()

        // Switch to Batch B
        viewModel.selectBatch(batchB.id)
        advanceUntilIdle()

        // Trigger undo of Batch A's edit while on Batch B
        assertTrue(viewModel.canUndo.value)
        viewModel.triggerUndo()
        advanceUntilIdle()

        // Check Batch A has reverted even though Batch B was active
        viewModel.selectBatch(batchA.id)
        advanceUntilIdle()
        val checkPage = viewModel.currentPages.value.first { it.id == pageA.id }
        assertEquals(originalRotation, checkPage.rotationDegrees)
    }
}
