// data/RagSearchEngine.kt
package com.kavitababy.data

import android.content.Context

/**
 * Lightweight, on-device "search" over indexed PDF chunks — no embeddings,
 * no network call needed just to find relevant text. Good enough for exam
 * notes where questions tend to share vocabulary with the source material.
 *
 * Flow:
 *  1. Break the question into meaningful keywords (drop common stop words).
 *  2. Pull candidate chunks from Room that contain at least one keyword.
 *  3. Score each candidate by how many keywords it contains (+ bonus for
 *     exact phrase matches) and return the top few.
 */
class RagSearchEngine(context: Context) {

    private val dao = KavitaDatabase.getInstance(context).pdfChunkDao()

    private val stopWords = setOf(
        "the", "a", "an", "is", "are", "was", "were", "what", "when", "where",
        "how", "why", "who", "which", "of", "in", "on", "at", "to", "for",
        "and", "or", "kya", "hai", "ka", "ki", "ke", "kaise", "kyun", "mein",
        "se", "ko", "aur", "ye", "wo", "hain", "tha", "thi"
    )

    data class RelevantChunk(val pdfName: String, val pageNumber: Int, val text: String, val score: Int)

    /**
     * Returns the top [maxChunks] most relevant chunks for [question],
     * or an empty list if nothing in the indexed PDFs seems related —
     * callers should treat an empty list as "no PDF context available"
     * and fall back to general knowledge.
     */
    suspend fun findRelevantChunks(question: String, maxChunks: Int = 4): List<RelevantChunk> {
        val keywords = extractKeywords(question)
        if (keywords.isEmpty()) return emptyList()

        val candidates = mutableMapOf<Long, PdfChunk>()
        for (keyword in keywords) {
            dao.searchByKeyword(keyword).forEach { chunk ->
                candidates[chunk.id] = chunk
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val scored = candidates.values.map { chunk ->
            val score = scoreChunk(chunk.searchableText, keywords, question)
            RelevantChunk(chunk.pdfName, chunk.pageNumber, chunk.text, score)
        }

        // A minimum score keeps genuinely unrelated chunks (which only
        // matched on a common word) from being sent to Gemini as if they
        // were relevant context.
        return scored
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(maxChunks)
    }

    private fun extractKeywords(question: String): List<String> {
        return question
            .lowercase()
            .replace(Regex("[^a-z0-9\\u0900-\\u097F\\s]"), " ") // keep latin, digits, Devanagari
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
    }

    private fun scoreChunk(searchableText: String, keywords: List<String>, originalQuestion: String): Int {
        var score = 0
        for (keyword in keywords) {
            if (searchableText.contains(keyword)) score += 1
        }
        // Reward chunks that contain the question's words close together /
        // in sequence, not just scattered individual keyword hits.
        val normalizedQuestion = originalQuestion.lowercase().trim()
        if (normalizedQuestion.length > 5 && searchableText.contains(normalizedQuestion)) {
            score += 5
        }
        return score
    }
}
