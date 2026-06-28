package com.example.data

import android.graphics.*
import java.io.File
import java.io.FileOutputStream

object ImageProcessor {

    fun processPage(
        originalFile: File,
        processedFile: File,
        rotationDegrees: Int,
        topLeftX: Float, topLeftY: Float,
        topRightX: Float, topRightY: Float,
        bottomRightX: Float, bottomRightY: Float,
        bottomLeftX: Float, bottomLeftY: Float,
        enhancementPreset: String
    ): Boolean {
        try {
            // 1. Decode original bitmap
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options) ?: return false

            // 2. Apply initial rotation if needed
            if (rotationDegrees != 0) {
                val rotateMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotateMatrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            val imgWidth = bitmap.width.toFloat()
            val imgHeight = bitmap.height.toFloat()

            // 3. Define source corners in pixels
            val srcCorners = floatArrayOf(
                topLeftX * imgWidth, topLeftY * imgHeight,         // Top Left
                topRightX * imgWidth, topRightY * imgHeight,       // Top Right
                bottomRightX * imgWidth, bottomRightY * imgHeight, // Bottom Right
                bottomLeftX * imgWidth, bottomLeftY * imgHeight    // Bottom Left
            )

            // 4. Calculate output size of the cropped document (estimate based on distance between points)
            val topWidth = Math.hypot((topRightX - topLeftX) * imgWidth.toDouble(), (topRightY - topLeftY) * imgHeight.toDouble())
            val bottomWidth = Math.hypot((bottomRightX - bottomLeftX) * imgWidth.toDouble(), (bottomRightY - bottomLeftY) * imgHeight.toDouble())
            val leftHeight = Math.hypot((bottomLeftX - topLeftX) * imgWidth.toDouble(), (bottomLeftY - topLeftY) * imgHeight.toDouble())
            val rightHeight = Math.hypot((bottomRightX - topRightX) * imgWidth.toDouble(), (bottomRightY - topRightY) * imgHeight.toDouble())

            val targetWidth = Math.max(topWidth, bottomWidth).coerceAtLeast(100.0).toFloat()
            val targetHeight = Math.max(leftHeight, rightHeight).coerceAtLeast(100.0).toFloat()

            val dstCorners = floatArrayOf(
                0f, 0f,                           // Top Left
                targetWidth, 0f,                  // Top Right
                targetWidth, targetHeight,        // Bottom Right
                0f, targetHeight                  // Bottom Left
            )

            // 5. Apply perspective transformation (homography)
            val transformMatrix = Matrix()
            val success = transformMatrix.setPolyToPoly(srcCorners, 0, dstCorners, 0, 4)
            
            val warpedBitmap = if (success) {
                val warped = Bitmap.createBitmap(targetWidth.toInt(), targetHeight.toInt(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(warped)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bitmap, transformMatrix, paint)
                warped
            } else {
                // Fallback to simple sub-rectangle crop
                val cropLeft = (topLeftX * imgWidth).coerceIn(0f, imgWidth).toInt()
                val cropTop = (topLeftY * imgHeight).coerceIn(0f, imgHeight).toInt()
                val cropRight = (bottomRightX * imgWidth).coerceIn(cropLeft + 10f, imgWidth).toInt()
                val cropBottom = (bottomRightY * imgHeight).coerceIn(cropTop + 10f, imgHeight).toInt()
                Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
            }

            // Recycle original bitmap as we are done with it
            bitmap.recycle()

            // 6. Apply enhancement preset using hardware-accelerated Canvas with ColorMatrixColorFilter
            val enhancedBitmap = Bitmap.createBitmap(warpedBitmap.width, warpedBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(enhancedBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val colorMatrix = ColorMatrix()
            when (enhancementPreset) {
                "Document" -> {
                    colorMatrix.setSaturation(0f) // Grayscale
                    val contrast = 1.6f
                    val brightness = -10f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                    colorMatrix.set(array)
                }
                "Handwriting" -> {
                    // Slight color saturation, clean shadows, boost white levels
                    val contrast = 1.3f
                    val brightness = 20f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                    colorMatrix.set(array)
                }
                "Color" -> {
                    colorMatrix.setSaturation(1.4f) // Boost colors
                    val contrast = 1.1f
                    val brightness = 5f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                    colorMatrix.set(array)
                }
                "Black & White" -> {
                    colorMatrix.setSaturation(0f)
                    val contrast = 2.5f
                    val brightness = -90f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                    colorMatrix.set(array)
                }
                "Low Light" -> {
                    val brightness = 50f
                    val contrast = 1.1f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                    colorMatrix.set(array)
                }
                else -> {
                    // Original - Identity matrix, no changes
                }
            }

            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(warpedBitmap, 0f, 0f, paint)

            warpedBitmap.recycle()

            // 7. Save output bitmap to processedFile path
            processedFile.parentFile?.mkdirs()
            val outStream = FileOutputStream(processedFile)
            enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
            outStream.flush()
            outStream.close()
            enhancedBitmap.recycle()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
