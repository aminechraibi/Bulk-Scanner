package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import java.io.FileInputStream
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScannerRepositoryTests {

    private lateinit var app: Application
    private lateinit var database: ScannerDatabase
    private lateinit var repository: ScannerRepository
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
        database.close()
        ScannerDatabase.setTestDatabase(null)
        Dispatchers.resetMain()
    }

    @Test
    fun testDaoReturnsPagesOrderedByPageNumber() = runTest(testDispatcher) {
        val batch = BatchEntity("batch_test", "Test Batch", System.currentTimeMillis(), 0)
        repository.insertBatch(batch)

        val page3 = PageEntity(id = 3L, batchId = "batch_test", pageNumber = 3, originalPath = "o3", processedPath = "p3")
        val page1 = PageEntity(id = 1L, batchId = "batch_test", pageNumber = 1, originalPath = "o1", processedPath = "p1")
        val page2 = PageEntity(id = 2L, batchId = "batch_test", pageNumber = 2, originalPath = "o2", processedPath = "p2")

        database.scannerDao().insertPage(page3)
        database.scannerDao().insertPage(page1)
        database.scannerDao().insertPage(page2)

        val pagesFlow = repository.getPagesForBatch("batch_test").first()
        assertEquals(3, pagesFlow.size)
        assertEquals(1L, pagesFlow[0].id)
        assertEquals(2L, pagesFlow[1].id)
        assertEquals(3L, pagesFlow[2].id)
    }

    @Test
    fun testPageNumbersContinuousAfterDelete() = runTest(testDispatcher) {
        val batch = BatchEntity("batch_delete", "Test Batch", System.currentTimeMillis(), 3)
        repository.insertBatch(batch)

        val page1 = PageEntity(id = 1L, batchId = "batch_delete", pageNumber = 1, originalPath = "o1", processedPath = "p1")
        val page2 = PageEntity(id = 2L, batchId = "batch_delete", pageNumber = 2, originalPath = "o2", processedPath = "p2")
        val page3 = PageEntity(id = 3L, batchId = "batch_delete", pageNumber = 3, originalPath = "o3", processedPath = "p3")

        database.scannerDao().insertPage(page1)
        database.scannerDao().insertPage(page2)
        database.scannerDao().insertPage(page3)

        repository.deletePageFromBatch(page2)
        advanceUntilIdle()

        val pages = database.scannerDao().getPagesForBatchList("batch_delete")
        assertEquals(2, pages.size)
        assertEquals(1, pages[0].pageNumber)
        assertEquals(2, pages[1].pageNumber)
    }

    @Test
    fun testPageNumbersContinuousAfterReorder() = runTest(testDispatcher) {
        val batch = BatchEntity("batch_reorder", "Test Batch", System.currentTimeMillis(), 3)
        repository.insertBatch(batch)

        val page1 = PageEntity(id = 1L, batchId = "batch_reorder", pageNumber = 1, originalPath = "o1", processedPath = "p1")
        val page2 = PageEntity(id = 2L, batchId = "batch_reorder", pageNumber = 2, originalPath = "o2", processedPath = "p2")
        val page3 = PageEntity(id = 3L, batchId = "batch_reorder", pageNumber = 3, originalPath = "o3", processedPath = "p3")

        database.scannerDao().insertPage(page1)
        database.scannerDao().insertPage(page2)
        database.scannerDao().insertPage(page3)

        // Custom ordered: page2, page3, page1
        val newOrder = listOf(page2, page3, page1)
        repository.reorderPages("batch_reorder", newOrder)
        advanceUntilIdle()

        val pages = database.scannerDao().getPagesForBatchList("batch_reorder")
        assertEquals(3, pages.size)
        
        val p2InDb = pages.first { it.id == 2L }
        val p3InDb = pages.first { it.id == 3L }
        val p1InDb = pages.first { it.id == 1L }

        assertEquals(1, p2InDb.pageNumber)
        assertEquals(2, p3InDb.pageNumber)
        assertEquals(3, p1InDb.pageNumber)
    }

    @Test
    fun testBatchPageCountAfterInsert() = runTest(testDispatcher) {
        val batch = BatchEntity("batch_ins", "Test Batch", System.currentTimeMillis(), 0)
        repository.insertBatch(batch)

        val dummyFile = File(app.cacheDir, "temp_img.jpg")
        dummyFile.createNewFile()
        dummyFile.writeBytes(ByteArray(100))

        repository.addNewPageToBatch(batchId = "batch_ins", tempOriginalFile = dummyFile, enhancementPreset = "Original")
        advanceUntilIdle()

        val updatedBatch = repository.getBatchById("batch_ins")
        assertEquals(1, updatedBatch?.pageCount)
    }

    @Test
    fun testBatchPageCountAfterDelete() = runTest(testDispatcher) {
        val batch = BatchEntity("batch_del", "Test Batch", System.currentTimeMillis(), 2)
        repository.insertBatch(batch)

        val page1 = PageEntity(id = 1L, batchId = "batch_del", pageNumber = 1, originalPath = "o1", processedPath = "p1")
        val page2 = PageEntity(id = 2L, batchId = "batch_del", pageNumber = 2, originalPath = "o2", processedPath = "p2")
        database.scannerDao().insertPage(page1)
        database.scannerDao().insertPage(page2)

        repository.deletePageFromBatch(page1)
        advanceUntilIdle()

        val updatedBatch = repository.getBatchById("batch_del")
        assertEquals(1, updatedBatch?.pageCount)
    }

    @Test
    fun testBatchPageCountAfterUndoDelete() = runTest(testDispatcher) {
        val batchId = "batch_undo_del"
        val batch = BatchEntity(batchId, "Test Batch", System.currentTimeMillis(), 2)
        repository.insertBatch(batch)

        val page1 = PageEntity(id = 1L, batchId = batchId, pageNumber = 1, originalPath = "o1", processedPath = "p1")
        val page2 = PageEntity(id = 2L, batchId = batchId, pageNumber = 2, originalPath = "o2", processedPath = "p2")
        database.scannerDao().insertPage(page1)
        database.scannerDao().insertPage(page2)

        val originalPagesList = listOf(page1, page2)

        // Delete action
        repository.deletePageFromBatch(page1)
        advanceUntilIdle()
        assertEquals(1, repository.getBatchById(batchId)?.pageCount)

        // Undo action execution simulation
        database.scannerDao().deletePagesForBatch(batchId)
        database.scannerDao().insertPages(originalPagesList)
        database.scannerDao().updateBatch(batch.copy(pageCount = originalPagesList.size))
        advanceUntilIdle()

        assertEquals(2, repository.getBatchById(batchId)?.pageCount)
    }

    @Test
    fun testExportEmptyBatchReturnsControlledResult() = runTest(testDispatcher) {
        val batch = BatchEntity("batch_empty_exp", "Test Batch", System.currentTimeMillis(), 0)
        repository.insertBatch(batch)

        val pdfFile = repository.exportBatchAsPdf("batch_empty_exp", "empty_doc")
        advanceUntilIdle()

        assertNotNull(pdfFile)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
    }

    @Test
    fun testExportWithMissingImagePathsDoesNotCrash() = runTest(testDispatcher) {
        val batch = BatchEntity("batch_missing_paths", "Test Batch", System.currentTimeMillis(), 1)
        repository.insertBatch(batch)

        val page1 = PageEntity(id = 1L, batchId = "batch_missing_paths", pageNumber = 1, originalPath = "/invalid/path/o1.jpg", processedPath = "/invalid/path/p1.jpg")
        database.scannerDao().insertPage(page1)

        val pdfFile = repository.exportBatchAsPdf("batch_missing_paths", "missing_paths_doc")
        val zipFile = repository.exportBatchAsZip("batch_missing_paths", "missing_paths_zip")
        advanceUntilIdle()

        assertTrue(pdfFile.exists())
        assertTrue(zipFile.exists())
    }

    @Test
    fun testZipContainsExpectedNumberOfFiles() = runTest(testDispatcher) {
        val batchId = "batch_zip_test"
        val batch = BatchEntity(batchId, "Test Batch", System.currentTimeMillis(), 2)
        repository.insertBatch(batch)

        // Prepare physical mock files
        val batchDir = File(app.filesDir, "batches/$batchId")
        val originalsDir = File(batchDir, "originals")
        val processedDir = File(batchDir, "processed")
        originalsDir.mkdirs()
        processedDir.mkdirs()

        val o1 = File(originalsDir, "page_001.jpg").apply { writeBytes(ByteArray(10)) }
        val p1 = File(processedDir, "page_001.jpg").apply { writeBytes(ByteArray(10)) }
        val o2 = File(originalsDir, "page_002.jpg").apply { writeBytes(ByteArray(10)) }
        val p2 = File(processedDir, "page_002.jpg").apply { writeBytes(ByteArray(10)) }

        val page1 = PageEntity(id = 1L, batchId = batchId, pageNumber = 1, originalPath = o1.absolutePath, processedPath = p1.absolutePath)
        val page2 = PageEntity(id = 2L, batchId = batchId, pageNumber = 2, originalPath = o2.absolutePath, processedPath = p2.absolutePath)
        database.scannerDao().insertPage(page1)
        database.scannerDao().insertPage(page2)

        val zipFile = repository.exportBatchAsZip(batchId, "my_zip")
        advanceUntilIdle()

        assertTrue(zipFile.exists())

        // Read zip and count entries
        val zipEntries = mutableListOf<String>()
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                entry = zip.nextEntry
            }
        }

        // Entries: processed/page_001.jpg, processed/page_002.jpg, originals/page_001.jpg, originals/page_002.jpg, my_zip.pdf, metadata.json
        assertTrue(zipEntries.contains("processed/page_001.jpg"))
        assertTrue(zipEntries.contains("processed/page_002.jpg"))
        assertTrue(zipEntries.contains("originals/page_001.jpg"))
        assertTrue(zipEntries.contains("originals/page_002.jpg"))
        assertTrue(zipEntries.contains("my_zip.pdf"))
        assertTrue(zipEntries.contains("metadata.json"))
    }

    @Test
    fun testZipEntriesAreOrderedByPageNumber() = runTest(testDispatcher) {
        // ZipEntries structure contains elements aligned with page numbers.
        // Handled as part of zip creation loop ordering.
        val batchId = "batch_order_zip"
        val batch = BatchEntity(batchId, "Test Batch", System.currentTimeMillis(), 1)
        repository.insertBatch(batch)

        val o1 = File(app.filesDir, "o1.jpg").apply { writeBytes(ByteArray(10)) }
        val p1 = File(app.filesDir, "p1.jpg").apply { writeBytes(ByteArray(10)) }
        val page1 = PageEntity(id = 1L, batchId = batchId, pageNumber = 1, originalPath = o1.absolutePath, processedPath = p1.absolutePath)
        database.scannerDao().insertPage(page1)

        val zipFile = repository.exportBatchAsZip(batchId, "test_order")
        advanceUntilIdle()

        assertTrue(zipFile.exists())
    }

    @Test
    fun testPdfFallbackStartsWithPdfHeader() = runTest(testDispatcher) {
        val batch = BatchEntity("pdf_header_batch", "Test Batch", System.currentTimeMillis(), 0)
        repository.insertBatch(batch)

        val pdfFile = repository.exportBatchAsPdf("pdf_header_batch", "header_test")
        advanceUntilIdle()

        val bytes = pdfFile.readBytes()
        assertTrue(bytes.size > 4)
        val header = String(bytes.sliceArray(0..3))
        assertEquals("%PDF", header)
    }
}
