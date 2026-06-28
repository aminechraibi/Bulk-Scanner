package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import com.example.data.ImageProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScannerRepository(
    private val context: Context,
    private val dao: ScannerDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val allBatches: Flow<List<BatchEntity>> = dao.getAllBatches()

    fun getPagesForBatch(batchId: String): Flow<List<PageEntity>> {
        return dao.getPagesForBatch(batchId)
    }

    suspend fun getBatchById(id: String): BatchEntity? {
        return dao.getBatchById(id)
    }

    suspend fun insertBatch(batch: BatchEntity) {
        dao.insertBatch(batch)
    }

    suspend fun updateBatch(batch: BatchEntity) {
        dao.updateBatch(batch)
    }

    suspend fun deleteBatch(batchId: String) = withContext(ioDispatcher) {
        val batch = dao.getBatchById(batchId)
        if (batch != null) {
            dao.deleteBatch(batch)
            dao.deletePagesForBatch(batchId)
            // Delete all files recursively
            val batchDir = File(context.filesDir, "batches/$batchId")
            if (batchDir.exists()) {
                batchDir.deleteRecursively()
            }
        }
    }

    suspend fun saveOriginalImage(batchId: String, pageNumber: Int, tempFile: File): File = withContext(ioDispatcher) {
        val originalDir = File(context.filesDir, "batches/$batchId/originals")
        originalDir.mkdirs()
        val originalFile = File(originalDir, "page_${String.format("%03d", pageNumber)}.jpg")
        tempFile.copyTo(originalFile, overwrite = true)
        originalFile
    }

    suspend fun processAndSavePage(
        batchId: String,
        pageNumber: Int,
        originalFile: File,
        topLeftX: Float = 0.05f, topLeftY: Float = 0.05f,
        topRightX: Float = 0.95f, topRightY: Float = 0.05f,
        bottomRightX: Float = 0.95f, bottomRightY: Float = 0.95f,
        bottomLeftX: Float = 0.05f, bottomLeftY: Float = 0.95f,
        rotationDegrees: Int = 0,
        enhancementPreset: String = "Document"
    ): File = withContext(ioDispatcher) {
        val processedDir = File(context.filesDir, "batches/$batchId/processed")
        processedDir.mkdirs()
        val processedFile = File(processedDir, "page_${String.format("%03d", pageNumber)}.jpg")
        
        ImageProcessor.processPage(
            originalFile = originalFile,
            processedFile = processedFile,
            rotationDegrees = rotationDegrees,
            topLeftX = topLeftX, topLeftY = topLeftY,
            topRightX = topRightX, topRightY = topRightY,
            bottomRightX = bottomRightX, bottomRightY = bottomRightY,
            bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY,
            enhancementPreset = enhancementPreset
        )
        processedFile
    }

    suspend fun addNewPageToBatch(
        batchId: String,
        tempOriginalFile: File,
        topLeftX: Float = 0.05f, topLeftY: Float = 0.05f,
        topRightX: Float = 0.95f, topRightY: Float = 0.05f,
        bottomRightX: Float = 0.95f, bottomRightY: Float = 0.95f,
        bottomLeftX: Float = 0.05f, bottomLeftY: Float = 0.95f,
        enhancementPreset: String = "Document",
        rotationDegrees: Int = 0
    ): PageEntity = withContext(ioDispatcher) {
        // Get existing pages to determine page number
        val pages = dao.getPagesForBatchList(batchId)
        val newPageNumber = if (pages.isEmpty()) 1 else pages.last().pageNumber + 1
        
        // Save original file
        val originalFile = saveOriginalImage(batchId, newPageNumber, tempOriginalFile)
        
        // Process page
        val processedFile = processAndSavePage(
            batchId = batchId,
            pageNumber = newPageNumber,
            originalFile = originalFile,
            topLeftX = topLeftX, topLeftY = topLeftY,
            topRightX = topRightX, topRightY = topRightY,
            bottomRightX = bottomRightX, bottomRightY = bottomRightY,
            bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY,
            enhancementPreset = enhancementPreset,
            rotationDegrees = rotationDegrees
        )

        val pageEntity = PageEntity(
            batchId = batchId,
            pageNumber = newPageNumber,
            originalPath = originalFile.absolutePath,
            processedPath = processedFile.absolutePath,
            status = "good",
            topLeftX = topLeftX, topLeftY = topLeftY,
            topRightX = topRightX, topRightY = topRightY,
            bottomRightX = bottomRightX, bottomRightY = bottomRightY,
            bottomLeftX = bottomLeftX, bottomLeftY = bottomLeftY,
            enhancementPreset = enhancementPreset,
            rotationDegrees = rotationDegrees
        )
        
        val id = dao.insertPage(pageEntity)
        val finalPage = pageEntity.copy(id = id)
        
        // Update batch count
        val batch = dao.getBatchById(batchId)
        if (batch != null) {
            dao.updateBatch(batch.copy(pageCount = newPageNumber))
        }
        
        finalPage
    }

    suspend fun updatePageSettings(page: PageEntity): PageEntity = withContext(ioDispatcher) {
        val originalFile = File(page.originalPath)
        val processedFile = File(page.processedPath)
        
        // Reprocess
        ImageProcessor.processPage(
            originalFile = originalFile,
            processedFile = processedFile,
            rotationDegrees = page.rotationDegrees,
            topLeftX = page.topLeftX, topLeftY = page.topLeftY,
            topRightX = page.topRightX, topRightY = page.topRightY,
            bottomRightX = page.bottomRightX, bottomRightY = page.bottomRightY,
            bottomLeftX = page.bottomLeftX, bottomLeftY = page.bottomLeftY,
            enhancementPreset = page.enhancementPreset
        )
        
        val updatedPage = page.copy(status = "good") // Clear warnings on manual save
        dao.updatePage(updatedPage)
        updatedPage
    }

    suspend fun deletePageFromBatch(page: PageEntity) = withContext(ioDispatcher) {
        dao.deletePage(page)
        
        // Delete files
        File(page.originalPath).delete()
        File(page.processedPath).delete()
        
        // Shift subsequent page numbers down
        val remainingPages = dao.getPagesForBatchList(page.batchId)
        var count = 1
        for (p in remainingPages) {
            val updatedPage = p.copy(pageNumber = count)
            dao.updatePage(updatedPage)
            count++
        }
        
        // Update batch page count
        val batch = dao.getBatchById(page.batchId)
        if (batch != null) {
            dao.updateBatch(batch.copy(pageCount = count - 1))
        }
    }

    suspend fun reorderPages(batchId: String, orderedPages: List<PageEntity>) = withContext(ioDispatcher) {
        // Simple update: loop through list and set their new page number sequentially
        for ((index, page) in orderedPages.withIndex()) {
            val updatedPage = page.copy(pageNumber = index + 1)
            dao.updatePage(updatedPage)
        }
    }

    suspend fun replacePageImage(page: PageEntity, newTempFile: File): PageEntity = withContext(ioDispatcher) {
        // Overwrite original file
        val originalFile = File(page.originalPath)
        newTempFile.copyTo(originalFile, overwrite = true)
        
        // Reprocess
        val processedFile = File(page.processedPath)
        ImageProcessor.processPage(
            originalFile = originalFile,
            processedFile = processedFile,
            rotationDegrees = page.rotationDegrees,
            topLeftX = page.topLeftX, topLeftY = page.topLeftY,
            topRightX = page.topRightX, topRightY = page.topRightY,
            bottomRightX = page.bottomRightX, bottomRightY = page.bottomRightY,
            bottomLeftX = page.bottomLeftX, bottomLeftY = page.bottomLeftY,
            enhancementPreset = page.enhancementPreset
        )
        
        val updatedPage = page.copy(status = "good")
        dao.updatePage(updatedPage)
        updatedPage
    }

    // PDF Export function
    suspend fun exportBatchAsPdf(batchId: String, pdfFileName: String): File = withContext(ioDispatcher) {
        val pages = dao.getPagesForBatchList(batchId)
        val exportsDir = File(context.filesDir, "batches/$batchId/exports")
        exportsDir.mkdirs()
        val pdfFile = File(exportsDir, "$pdfFileName.pdf")
        
        val pdfDocument = PdfDocument()
        for ((index, page) in pages.withIndex()) {
            val file = File(page.processedPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    val canvas = pdfPage.canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)
                    bitmap.recycle()
                }
            }
        }
        
        val outStream = FileOutputStream(pdfFile)
        pdfDocument.writeTo(outStream)
        pdfDocument.close()
        outStream.close()
        
        // Update batch status
        val batch = dao.getBatchById(batchId)
        if (batch != null) {
            dao.updateBatch(batch.copy(isExported = true, pdfPath = pdfFile.absolutePath))
        }
        
        pdfFile
    }

    // ZIP Export function (Compiles processed, originals, metadata.json and pdf as a ZIP)
    suspend fun exportBatchAsZip(batchId: String, zipFileName: String): File = withContext(ioDispatcher) {
        val batch = dao.getBatchById(batchId) ?: throw Exception("Batch not found")
        val pages = dao.getPagesForBatchList(batchId)
        
        val exportsDir = File(context.filesDir, "batches/$batchId/exports")
        exportsDir.mkdirs()
        val zipFile = File(exportsDir, "$zipFileName.zip")
        
        // Pre-generate PDF so it can be added to ZIP
        val pdfFile = exportBatchAsPdf(batchId, zipFileName)
        
        val zipOutputStream = ZipOutputStream(FileOutputStream(zipFile))
        
        // 1. Add Processed Images
        for (page in pages) {
            val file = File(page.processedPath)
            if (file.exists()) {
                zipOutputStream.putNextEntry(ZipEntry("processed/${file.name}"))
                val fileInputStream = FileInputStream(file)
                fileInputStream.copyTo(zipOutputStream)
                fileInputStream.close()
                zipOutputStream.closeEntry()
            }
        }
        
        // 2. Add Original Images
        for (page in pages) {
            val file = File(page.originalPath)
            if (file.exists()) {
                zipOutputStream.putNextEntry(ZipEntry("originals/${file.name}"))
                val fileInputStream = FileInputStream(file)
                fileInputStream.copyTo(zipOutputStream)
                fileInputStream.close()
                zipOutputStream.closeEntry()
            }
        }
        
        // 3. Add PDF
        if (pdfFile.exists()) {
            zipOutputStream.putNextEntry(ZipEntry("$zipFileName.pdf"))
            val fileInputStream = FileInputStream(pdfFile)
            fileInputStream.copyTo(zipOutputStream)
            fileInputStream.close()
            zipOutputStream.closeEntry()
        }
        
        // 4. Add metadata.json
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"batch_id\": \"${batch.id}\",\n")
        jsonBuilder.append("  \"name\": \"${batch.name}\",\n")
        jsonBuilder.append("  \"created_at\": \"${batch.createdAt}\",\n")
        jsonBuilder.append("  \"page_count\": ${batch.pageCount},\n")
        jsonBuilder.append("  \"exported\": true,\n")
        jsonBuilder.append("  \"pages\": [\n")
        for ((index, page) in pages.withIndex()) {
            jsonBuilder.append("    {\n")
            jsonBuilder.append("      \"page_number\": ${page.pageNumber},\n")
            jsonBuilder.append("      \"original_path\": \"originals/page_${String.format("%03d", page.pageNumber)}.jpg\",\n")
            jsonBuilder.append("      \"processed_path\": \"processed/page_${String.format("%03d", page.pageNumber)}.jpg\",\n")
            jsonBuilder.append("      \"status\": \"${page.status}\",\n")
            jsonBuilder.append("      \"warnings\": [${if (page.warningTypes.isEmpty()) "" else page.warningTypes.split(",").joinToString { "\"$it\"" }}],\n")
            jsonBuilder.append("      \"manual_edits\": [${if (page.manualEdits.isEmpty()) "" else page.manualEdits.split(",").joinToString { "\"$it\"" }}]\n")
            jsonBuilder.append("    }${if (index < pages.size - 1) "," else ""}\n")
        }
        jsonBuilder.append("  ]\n")
        jsonBuilder.append("}")
        
        zipOutputStream.putNextEntry(ZipEntry("metadata.json"))
        zipOutputStream.write(jsonBuilder.toString().toByteArray())
        zipOutputStream.closeEntry()
        
        zipOutputStream.close()
        
        // Update batch status
        dao.updateBatch(batch.copy(isExported = true, zipPath = zipFile.absolutePath, pdfPath = pdfFile.absolutePath))
        
        zipFile
    }
}
