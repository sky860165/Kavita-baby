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
                    return@withContext "Sorry jaani, abhi connect nahi ho paaya (${response.code}). Thodi der baad try karo."
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
            "Sorry jaani, network mein dikkat lag rahi hai. Check karo connection aur dobara try karo."
        }
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
