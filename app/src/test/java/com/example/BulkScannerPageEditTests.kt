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
class BulkScannerPageEditTests {

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
    fun testSaveCropCoordinatesBelowZero() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        // Negative coordinates
        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 0,
            topLeftX = -0.5f, topLeftY = -0.5f,
            topRightX = 1.0f, topRightY = 0.0f,
            bottomRightX = 1.0f, bottomRightY = 1.0f,
            bottomLeftX = 0.0f, bottomLeftY = 1.0f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(-0.5f, updatedPage.topLeftX)
        assertEquals(-0.5f, updatedPage.topLeftY)
        assertTrue(updatedPage.manualEdits.contains("manual_edit"))
    }

    @Test
    fun testSaveCropCoordinatesAboveOne() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        // Coordinates > 1.0
        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 0,
            topLeftX = 0.0f, topLeftY = 0.0f,
            topRightX = 1.8f, topRightY = -0.1f,
            bottomRightX = 1.5f, bottomRightY = 1.5f,
            bottomLeftX = 0.0f, bottomLeftY = 1.0f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(1.8f, updatedPage.topRightX)
        assertEquals(1.5f, updatedPage.bottomRightY)
    }

    @Test
    fun testSaveReversedCropCorners() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        // Left and Right corners completely swapped (reversed)
        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 0,
            topLeftX = 1.0f, topLeftY = 0.0f,
            topRightX = 0.0f, topRightY = 0.0f,
            bottomRightX = 0.0f, bottomRightY = 1.0f,
            bottomLeftX = 1.0f, bottomLeftY = 1.0f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(1.0f, updatedPage.topLeftX)
        assertEquals(0.0f, updatedPage.topRightX)
    }

    @Test
    fun testSaveIdenticalCropCorners() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        // All corners set to same exact coordinate (should fall back safely in image processor)
        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 0,
            topLeftX = 0.5f, topLeftY = 0.5f,
            topRightX = 0.5f, topRightY = 0.5f,
            bottomRightX = 0.5f, bottomRightY = 0.5f,
            bottomLeftX = 0.5f, bottomLeftY = 0.5f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(0.5f, updatedPage.topLeftX)
        assertEquals(0.5f, updatedPage.bottomRightY)
    }

    @Test
    fun testSaveRotationNegative90() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = -90,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(-90, updatedPage.rotationDegrees)
    }

    @Test
    fun testSaveRotation450() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 450,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(450, updatedPage.rotationDegrees)
    }

    @Test
    fun testSaveRotation720() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 720,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals(720, updatedPage.rotationDegrees)
    }

    @Test
    fun testUnknownEnhancementPreset() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        // Save with non-existent preset name
        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 0,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "SuperNeonCyberpunk"
        )
        advanceUntilIdle()

        val updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals("SuperNeonCyberpunk", updatedPage.enhancementPreset)
    }

    @Test
    fun testRepeatedEditsDoNotCorruptManualEdits() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sampleBatch = viewModel.allBatches.value.first()
        viewModel.selectBatch(sampleBatch.id)
        advanceUntilIdle()

        val targetPage = viewModel.currentPages.value.first()

        // Edit 1
        viewModel.savePageEdits(
            page = targetPage,
            rotationDegrees = 90,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        var updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals("manual_edit", updatedPage.manualEdits)

        // Edit 2
        viewModel.savePageEdits(
            page = updatedPage,
            rotationDegrees = 180,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )
        advanceUntilIdle()

        updatedPage = viewModel.currentPages.value.first { it.id == targetPage.id }
        assertEquals("manual_edit", updatedPage.manualEdits) // String shouldn't become "manual_edit,manual_edit"
    }
}
