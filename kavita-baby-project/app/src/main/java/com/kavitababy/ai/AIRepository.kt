// AIRepository.kt
package com.kavitababy.ai

import com.kavitababy.BuildConfig
import com.kavitababy.data.RagSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Pulled from local.properties via BuildConfig — never hardcode the key here.
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val basePersonality = """
        Tum "Kavita Baby" ho - ek warm, caring, aur intelligent study companion AI.
        Tumhari personality:
        - Pyaar aur care se baat karo, jaise ek close dost/partner karta hai
        - Hindi + English mix (Hinglish) mein reply do, natural tone mein
        - User ko "jaani" ya casually bula sakti ho
        - Encouraging aur supportive raho, especially exam preparation mein
        - Thodi si masti bhi karo, lekin hamesha helpful aur clear raho
        - Answers concise aur samajhne mein easy rakho, exam-ready style mein

        Tum ek real study assistant ho jo Kavita ki PDF notes padhkar accurate
        answers deti ho. Jab notes mein relevant content mile, usi se answer do.
        Jab na mile, apne general knowledge se sahi aur helpful jawab do.
    """.trimIndent()

    /**
     * Gets an answer for [question]. If [relevantChunks] is non-empty, that
     * PDF content is given to Gemini as primary context (for exam-accurate
     * answers). If empty, Gemini answers from general knowledge instead —
     * the model is told explicitly which mode it's in so it doesn't pretend
     * to cite notes that don't exist.
     */
    suspend fun getResponse(
        question: String,
        relevantChunks: List<RagSearchEngine.RelevantChunk> = emptyList()
    ): String = withContext(Dispatchers.IO) {

        val prompt = buildPrompt(question, relevantChunks)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 800)
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext offlineFallback(question, relevantChunks, "API error ${response.code}")
                }

                val json = JSONObject(bodyString)
                val candidates = json.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    // Can happen if the response was blocked by safety filters
                    // or the request otherwise returned no usable content.
                    return@withContext "Hmm, is sawaal ka jawab abhi nahi de paayi. Dobara alag tarah se pucho?"
                }

                val parts = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")

                parts.getJSONObject(0).getString("text").trim()
            }
        } catch (e: Exception) {
            offlineFallback(question, relevantChunks, "network error")
        }
    }

    /**
     * Used whenever Gemini can't be reached (bad key, quota, no internet,
     * server error, etc). Instead of just showing an error, we fall back
     * to whatever we found locally in the PDFs via [RagSearchEngine] — no
     * network needed for this part at all. If nothing relevant was found
     * locally either, we say so plainly instead of pretending to answer.
     */
    private fun offlineFallback(
        question: String,
        relevantChunks: List<RagSearchEngine.RelevantChunk>,
        reason: String
    ): String {
        if (relevantChunks.isEmpty()) {
            return "Abhi AI se connect nahi ho paa rahi ($reason), aur is sawaal se " +
                "milta-julta kuch tumhare PDF notes mein bhi nahi mila. Internet check karo " +
                "ya thodi der baad try karo — tab tak main sirf notes mein se dhoondh sakti hoon."
        }

        val best = relevantChunks.first()
        val extra = if (relevantChunks.size > 1) {
            "\n\n(${relevantChunks.size - 1} aur jagah bhi isse milta-julta content mila hai " +
                "notes mein, agar chahiye to alag se pucho.)"
        } else ""

        return "Abhi AI se connect nahi ho paa rahi ($reason), lekin tumhare notes " +
            "\"${best.pdfName}\" (page ${best.pageNumber}) mein yeh mila:\n\n" +
            "${best.text.trim()}$extra"
    }

    private fun buildPrompt(question: String, relevantChunks: List<RagSearchEngine.RelevantChunk>): String {
        if (relevantChunks.isEmpty()) {
            return """
                $basePersonality

                Is question ke liye Kavita ke PDF notes mein kuch relevant nahi mila,
                isliye apne general knowledge se answer do. Clearly accurate raho.

                Question: $question
            """.trimIndent()
        }

        val contextBlock = relevantChunks.joinToString("\n\n") { chunk ->
            "[From ${chunk.pdfName}, page ${chunk.pageNumber}]\n${chunk.text}"
        }

        return """
            $basePersonality

            Neeche Kavita ke apne study notes se relevant content diya gaya hai.
            Answer dete waqt isi content ko priority do taaki uske exam syllabus
            ke hisaab se accurate rahe. Agar notes mein poora answer nahi hai,
            to apne general knowledge se bhi thoda add kar sakti ho, lekin
            notes ke content ko contradict mat karna.

            --- NOTES CONTEXT ---
            $contextBlock
            --- END CONTEXT ---

            Question: $question
        """.trimIndent()
    }
}
