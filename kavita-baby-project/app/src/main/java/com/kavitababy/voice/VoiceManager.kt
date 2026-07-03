// VoiceManager.kt
package com.kavitababy.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceManager(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var ttsReady = false

    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("hi", "IN")
                textToSpeech?.setSpeechRate(0.95f)
                ttsReady = true
            }
        }
    }

    /** Speaks [text]. Calls [onDone] when speech finishes or fails, so callers can reset UI state. */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) {
            onDone?.invoke()
            return
        }

        if (onDone != null) {
            textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onDone()
                }
            })
        }

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kavita_speak")
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    /**
     * Starts listening for speech. [onResult] is called with the recognized text,
     * or an empty string if nothing was understood — check for blank rather than
     * assuming a result is always present.
     * [onError] is called if recognition fails outright (no permission, no network, etc).
     */
    fun startListening(onResult: (String) -> Unit, onError: (() -> Unit)? = null) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError?.invoke()
            return
        }

        // Avoid leaking a previous recognizer instance if startListening
        // is called again before the last one was cleaned up.
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // Covers cases like ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT, ERROR_NETWORK, etc.
                // Without this, the caller's UI would stay stuck in a "listening" state forever.
                onError?.invoke()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                onResult(matches?.firstOrNull() ?: "")
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
