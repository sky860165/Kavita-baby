// data/PdfScannerService.kt
package com.kavitababy.data

import android.content.Context
import android.provider.MediaStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Finds every PDF on the device via MediaStore, pulls text out of it with
 * PdfBox, and stores it as searchable chunks in Room.
 *
 * Re-running scanAllPdfs() is safe: PDFs already indexed (same path) are
 * skipped, so calling this both on app start and from a manual "Scan PDFs"
 * button won't duplicate work or data.
 */
class PdfScannerService(private val context: Context) {

    private val dao = KavitaDatabase.getInstance(context).pdfChunkDao()

    // Roughly one paragraph per chunk. Small enough that the RAG search
    // returns focused context, big enough to keep sentence meaning intact.
    private val chunkSizeChars = 800

    data class ScanResult(val pdfsFound: Int, val pdfsIndexed: Int, val chunksCreated: Int)

    suspend fun scanAllPdfs(forceRescan: Boolean = false): ScanResult {
        PDFBoxResourceLoader.init(context.applicationContext)

        val pdfFiles = findAllPdfPaths()
        val alreadyIndexed = if (forceRescan) emptySet() else dao.getAllIndexedPaths().toSet()

        var indexedCount = 0
        var totalChunks = 0

        for (path in pdfFiles) {
            if (!forceRescan && path in alreadyIndexed) continue

            val file = File(path)
            if (!file.exists() || !file.canRead()) continue

            try {
                val chunks = extractChunks(file)
                if (chunks.isNotEmpty()) {
                    if (forceRescan) dao.deleteForPdf(path)
                    dao.insertAll(chunks)
                    indexedCount++
                    totalChunks += chunks.size
                }
            } catch (e: Exception) {
                // Corrupt / password-protected / unreadable PDF — skip it,
                // don't let one bad file stop the whole scan.
                continue
            }
        }

        return ScanResult(pdfsFound = pdfFiles.size, pdfsIndexed = indexedCount, chunksCreated = totalChunks)
    }

    private fun findAllPdfPaths(): List<String> {
        val paths = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")

        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            val dataIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            while (it.moveToNext()) {
                if (dataIndex >= 0) {
                    it.getString(dataIndex)?.let { path -> paths.add(path) }
                }
            }
        }
        return paths
    }

    private fun extractChunks(file: File): List<PdfChunk> {
        val chunks = mutableListOf<PdfChunk>()
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()
            val pageCount = document.numberOfPages

            for (pageNum in 1..pageCount) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val pageText = stripper.getText(document).trim()
                if (pageText.isBlank()) continue

                chunks.addAll(splitIntoChunks(pageText, file.name, file.absolutePath, pageNum))
            }
        }
        return chunks
    }

    private fun splitIntoChunks(
        pageText: String,
        pdfName: String,
        pdfPath: String,
        pageNumber: Int
    ): List<PdfChunk> {
        val chunks = mutableListOf<PdfChunk>()
        val paragraphs = pageText.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }

        val buffer = StringBuilder()
        for (para in paragraphs) {
            if (buffer.length + para.length > chunkSizeChars && buffer.isNotEmpty()) {
                chunks.add(buildChunk(buffer.toString(), pdfName, pdfPath, pageNumber))
                buffer.clear()
            }
            buffer.append(para).append("\n\n")
        }
        if (buffer.isNotEmpty()) {
            chunks.add(buildChunk(buffer.toString(), pdfName, pdfPath, pageNumber))
        }
        return chunks
    }

    private fun buildChunk(text: String, pdfName: String, pdfPath: String, pageNumber: Int): PdfChunk {
        return PdfChunk(
            pdfName = pdfName,
            pdfPath = pdfPath,
            pageNumber = pageNumber,
            text = text.trim(),
            searchableText = text.lowercase().replace(Regex("\\s+"), " ").trim()
        )
    }
}
