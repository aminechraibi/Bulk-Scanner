package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.data.ImageProcessor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageProcessorTests {

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir
    }

    private fun createMockJpeg(width: Int, height: Int): File {
        val file = File(cacheDir, "mock_${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLUE)
        
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        canvas.drawRect(width / 4f, height / 4f, width * 3f / 4f, height * 3f / 4f, paint)

        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
        out.close()
        bitmap.recycle()
        return file
    }

    @Test
    fun testCropCoordinateClamping() {
        val original = createMockJpeg(200, 200)
        val processed = File(cacheDir, "processed_clamped.jpg")

        // Coordinates far outside 0..1 bounds
        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 0,
            topLeftX = -0.5f, topLeftY = -0.5f,
            topRightX = 1.5f, topRightY = -0.1f,
            bottomRightX = 1.8f, bottomRightY = 1.9f,
            bottomLeftX = -0.1f, bottomLeftY = 1.5f,
            enhancementPreset = "Original"
        )

        assertTrue(result)
        assertTrue(processed.exists())
        assertTrue(processed.length() > 0)
    }

    @Test
    fun testRotationMetadata90() {
        val original = createMockJpeg(200, 100)
        val processed = File(cacheDir, "processed_rot90.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 90,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }

    @Test
    fun testRotationMetadata180() {
        val original = createMockJpeg(200, 100)
        val processed = File(cacheDir, "processed_rot180.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 180,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }

    @Test
    fun testRotationMetadata270() {
        val original = createMockJpeg(200, 100)
        val processed = File(cacheDir, "processed_rot270.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 270,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }

    @Test
    fun testTinyBitmapDoesNotCrash() {
        val original = createMockJpeg(2, 2)
        val processed = File(cacheDir, "processed_tiny.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 0,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }

    @Test
    fun testMissingImageFileDoesNotCrash() {
        val original = File(cacheDir, "completely_missing_file_xyz.jpg")
        val processed = File(cacheDir, "processed_missing.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 0,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )

        assertFalse(result)
        assertFalse(processed.exists())
    }

    @Test
    fun testOriginalPreset() {
        val original = createMockJpeg(100, 100)
        val processed = File(cacheDir, "processed_preset_orig.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 0,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Original"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }

    @Test
    fun testBlackAndWhitePreset() {
        val original = createMockJpeg(100, 100)
        val processed = File(cacheDir, "processed_preset_bw.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 0,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Black & White"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }

    @Test
    fun testGrayscalePreset() {
        val original = createMockJpeg(100, 100)
        val processed = File(cacheDir, "processed_preset_gray.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 0,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Grayscale"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }

    @Test
    fun testColorPreset() {
        val original = createMockJpeg(100, 100)
        val processed = File(cacheDir, "processed_preset_color.jpg")

        val result = ImageProcessor.processPage(
            originalFile = original,
            processedFile = processed,
            rotationDegrees = 0,
            topLeftX = 0f, topLeftY = 0f,
            topRightX = 1f, topRightY = 0f,
            bottomRightX = 1f, bottomRightY = 1f,
            bottomLeftX = 0f, bottomLeftY = 1f,
            enhancementPreset = "Color"
        )

        assertTrue(result)
        assertTrue(processed.exists())
    }
}
