package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val isExported: Boolean = false,
    val pdfPath: String? = null,
    val zipPath: String? = null
)
