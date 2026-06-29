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
class BulkScannerBoundaryTests {

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
    fun testEmptyBatchHasNoPagesAndNoCrash() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createNewBatch("Empty Batch 1", "Document")
        advanceUntilIdle()

        assertNotNull(viewModel.currentBatchId.value)
        assertEquals("Empty Batch 1", viewModel.currentBatch.value?.name)
        assertTrue(viewModel.currentPages.value.isEmpty())
        assertTrue(viewModel.displayedPages.value.isEmpty())
    }

    @Test
    fun testReorderEmptyBatchDoesNothing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createNewBatch("Empty Batch 2", "Document")
        advanceUntilIdle()

        // Reordering with empty list should do nothing and not crash
        viewModel.reorderPages(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.currentPages.value.isEmpty())
    }

    @Test
    fun testReorderSinglePageBatchDoesNothing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createNewBatch("Single Page Batch", "Document")
        advanceUntilIdle()

        val sampleFile = SampleDocumentGenerator.generateSampleDocFile(app, "math_exam")
        var addedPage: PageEntity? = null
        viewModel.addPageToCurrentBatch(sampleFile, "Document") { page ->
            addedPage = page
        }
        advanceUntilIdle()

        assertNotNull(addedPage)
        val pagesBefore = viewModel.currentPages.value
        assertEquals(1, pagesBefore.size)

        // Reorder single page
        viewModel.reorderPages(pagesBefore)
        advanceUntilIdle()

        val pagesAfter = viewModel.currentPages.value
        assertEquals(1, pagesAfter.size)
        assertEquals(pagesBefore[0].id, pagesAfter[0].id)
    }

    @Test
    fun testDeleteFromEmptyBatchDoesNothing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createNewBatch("Empty Batch 3", "Document")
        advanceUntilIdle()

        // Attempting to delete a non-existent page shouldn't crash
        val dummyPage = PageEntity(
            id = 99999L,
            batchId = viewModel.currentBatchId.value ?: "dummy",
            pageNumber = 1,
            originalPath = "dummy_path",
            processedPath = "dummy_processed_path"
        )

        viewModel.deletePage(dummyPage)
        advanceUntilIdle()

        assertTrue(viewModel.currentPages.value.isEmpty())
    }

    @Test
    fun testSelectInvalidBatchIdKeepsSafeState() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectBatch("non_existent_batch_id")
        advanceUntilIdle()

        assertEquals("non_existent_batch_id", viewModel.currentBatchId.value)
        assertNull(viewModel.currentBatch.value)
        assertTrue(viewModel.currentPages.value.isEmpty())
    }

    @Test
    fun testCreateDuplicateBatchNames() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createNewBatch("Duplicate Batch", "Document")
        advanceUntilIdle()
        val firstBatchId = viewModel.currentBatchId.value

        viewModel.createNewBatch("Duplicate Batch", "Document")
        advanceUntilIdle()
        val secondBatchId = viewModel.currentBatchId.value

        assertNotEquals(firstBatchId, secondBatchId)
        assertEquals("Duplicate Batch", viewModel.currentBatch.value?.name)
    }

    @Test
    fun testCreateBatchWithVeryLongName() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val longName = "A".repeat(500)
        viewModel.createNewBatch(longName, "Document")
        advanceUntilIdle()

        assertEquals(longName, viewModel.currentBatch.value?.name)
    }

    @Test
    fun testCreateBatchWithArabicFrenchName() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val localizedName = "Dossier Médical - الملف الطبي"
        viewModel.createNewBatch(localizedName, "Document")
        advanceUntilIdle()

        assertEquals(localizedName, viewModel.currentBatch.value?.name)
    }

    @Test
    fun testCreateBatchWithInvalidFilenameCharacters() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val nameWithSpecialChars = "Batch<>:\"/\\|?*!#$"
        viewModel.createNewBatch(nameWithSpecialChars, "Document")
        advanceUntilIdle()

        assertEquals(nameWithSpecialChars, viewModel.currentBatch.value?.name)
    }
}
