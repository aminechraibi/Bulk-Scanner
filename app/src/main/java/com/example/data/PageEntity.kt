package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String,
    val pageNumber: Int,
    val originalPath: String,
    val processedPath: String,
    val status: String = "good", // good, warning, processing, failed
    val warningTypes: String = "", // comma-separated warnings
    val manualEdits: String = "", // comma-separated edit markers
    
    // Corner coordinates (0.0f to 1.0f)
    val topLeftX: Float = 0.05f,
    val topLeftY: Float = 0.05f,
    val topRightX: Float = 0.95f,
    val topRightY: Float = 0.05f,
    val bottomRightX: Float = 0.95f,
    val bottomRightY: Float = 0.95f,
    val bottomLeftX: Float = 0.05f,
    val bottomLeftY: Float = 0.95f,
    
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val enhancementPreset: String = "Document" // Original, Document, Handwriting, Color, Black & White, Low Light
)
