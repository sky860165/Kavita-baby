// data/PdfChunkDao.kt
package com.kavitababy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PdfChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<PdfChunk>)

    @Query("SELECT COUNT(*) FROM pdf_chunks WHERE pdfPath = :pdfPath")
    suspend fun countForPdf(pdfPath: String): Int

    @Query("DELETE FROM pdf_chunks WHERE pdfPath = :pdfPath")
    suspend fun deleteForPdf(pdfPath: String)

    @Query("SELECT DISTINCT pdfPath FROM pdf_chunks")
    suspend fun getAllIndexedPaths(): List<String>

    @Query("SELECT * FROM pdf_chunks")
    suspend fun getAllChunks(): List<PdfChunk>

    // Cheap first-pass filter: only pull chunks that contain at least one
    // query word, so RagSearchEngine doesn't have to score every chunk
    // in the database on every question.
    @Query(
        """
        SELECT * FROM pdf_chunks 
        WHERE searchableText LIKE '%' || :keyword || '%'
        LIMIT 200
        """
    )
    suspend fun searchByKeyword(keyword: String): List<PdfChunk>
}
