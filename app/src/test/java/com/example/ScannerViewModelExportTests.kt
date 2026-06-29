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
class ScannerViewModelExportTests {

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
    fun testPdfExportCreatesNonEmptyFile() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        var pdfFile: File? = null
        viewModel.exportCurrentBatchPdf("algebra_midterm") { file ->
            pdfFile = file
        }
        advanceUntilIdle()

        assertNotNull(pdfFile)
        assertTrue(pdfFile!!.exists())
        assertTrue(pdfFile!!.length() > 0)
        assertTrue(viewModel.exportState.value is ExportStatus.Success)
    }

    @Test
    fun testZipExportCreatesNonEmptyFile() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        var zipFile: File? = null
        viewModel.exportCurrentBatchZip("algebra_zip") { file ->
            zipFile = file
        }
        advanceUntilIdle()

        assertNotNull(zipFile)
        assertTrue(zipFile!!.exists())
        assertTrue(zipFile!!.length() > 0)
        assertTrue(viewModel.exportState.value is ExportStatus.Success)
    }

    @Test
    fun testExportWithEmptyFilenameUsesFallbackName() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        var pdfFile: File? = null
        viewModel.exportCurrentBatchPdf("   ") { file ->
            pdfFile = file
        }
        advanceUntilIdle()

        assertNotNull(pdfFile)
        assertEquals("batch_export.pdf", pdfFile!!.name)
    }

    @Test
    fun testExportWithLongFilenameIsSafe() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val longName = "A".repeat(300)
        var pdfFile: File? = null
        viewModel.exportCurrentBatchPdf(longName) { file ->
            pdfFile = file
        }
        advanceUntilIdle()

        assertNotNull(pdfFile)
        assertTrue(pdfFile!!.name.length <= 130) // 120 chars + ".pdf"
    }

    @Test
    fun testExportWithArabicFrenchFilenameIsSafe() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val arabicFrenchName = "Examen_العربية"
        var pdfFile: File? = null
        viewModel.exportCurrentBatchPdf(arabicFrenchName) { file ->
            pdfFile = file
        }
        advanceUntilIdle()

        assertNotNull(pdfFile)
        assertTrue(pdfFile!!.name.startsWith("Examen_العربية"))
    }

    @Test
    fun testExportWithInvalidFilenameCharactersIsSanitized() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val invalidCharsName = "Exam<>:\"/\\|?*Name"
        var pdfFile: File? = null
        viewModel.exportCurrentBatchPdf(invalidCharsName) { file ->
            pdfFile = file
        }
        advanceUntilIdle()

        assertNotNull(pdfFile)
        assertEquals("Exam_________Name.pdf", pdfFile!!.name)
    }

    @Test
    fun testExportFailureUpdatesExportStateToError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Select a non-existent batch ID to force repository lookup error
        viewModel.selectBatch("non_existent_id")
        advanceUntilIdle()

        viewModel.exportCurrentBatchPdf("test") {}
        advanceUntilIdle()

        val state = viewModel.exportState.value
        assertTrue(state is ExportStatus.Error)
        assertEquals("Batch not found", (state as ExportStatus.Error).message)
    }
}
