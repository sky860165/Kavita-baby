// data/PdfChunk.kt
package com.kavitababy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_chunks")
data class PdfChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfName: String,
    val pdfPath: String,
    val pageNumber: Int,
    val text: String,
    // Lowercased, whitespace-normalized copy of `text` used for fast keyword search.
    val searchableText: String
)
