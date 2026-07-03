// FloatingWidgetService.kt
package com.kavitababy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.kavitababy.R
import com.kavitababy.ai.AIRepository
import com.kavitababy.data.RagSearchEngine
import com.kavitababy.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var pulseView: View
    private lateinit var avatarImage: ImageView

    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var isDragging = false

    private lateinit var voiceManager: VoiceManager
    private lateinit var ragEngine: RagSearchEngine
    private lateinit var aiRepository: AIRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private enum class WidgetState { IDLE, LISTENING, THINKING, SPEAKING }
    private var currentState = WidgetState.IDLE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupFloatingWidget()

        voiceManager = VoiceManager(this)
        ragEngine = RagSearchEngine(applicationContext)
        aiRepository = AIRepository()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "kavita_baby_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kavita Baby AI",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kavita Baby AI")
            .setContentText("Padhai mein saath hu, tap karke pucho")
            .setSmallIcon(R.drawable.ic_kavita)
            .build()

        startForeground(1, notification)
    }

    private fun setupFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)
        avatarImage = floatingView.findViewById(R.id.avatarImage)
        pulseView = floatingView.findViewById(R.id.pulseEffect)
        avatarImage.setImageResource(R.drawable.kavita_face)
        setWidgetState(WidgetState.IDLE)

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onBubbleTapped()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, layoutParams)
    }

    // ---- Tap handling ----

    private fun onBubbleTapped() {
        when (currentState) {
            WidgetState.IDLE -> startListening()
            WidgetState.LISTENING -> {
                voiceManager.stopListening()
                setWidgetState(WidgetState.IDLE)
            }
            WidgetState.SPEAKING -> {
                voiceManager.stopSpeaking()
                setWidgetState(WidgetState.IDLE)
            }
            WidgetState.THINKING -> {
                // Request already in flight — ignore taps until it resolves.
            }
        }
    }

    // ---- Speech input ----

    private fun startListening() {
        setWidgetState(WidgetState.LISTENING)

        voiceManager.startListening(
            onResult = { text ->
                if (text.isBlank()) {
                    setWidgetState(WidgetState.IDLE)
                } else {
                    handleQuestion(text)
                }
            },
            onError = {
                setWidgetState(WidgetState.IDLE)
                speak("Sorry, sun nahi paayi. Dobara try karo.")
            }
        )
    }

    // ---- Question handling (RAG + Gemini) ----

    private fun handleQuestion(question: String) {
        setWidgetState(WidgetState.THINKING)
        serviceScope.launch {
            try {
                val relevantChunks = withContext(Dispatchers.IO) {
                    ragEngine.findRelevantChunks(question)
                }
                val answer = aiRepository.getResponse(question, relevantChunks)
                speak(answer)
            } catch (e: Exception) {
                speak("Sorry baby, abhi answer nahi mil paaya. Dobara try karo.")
            }
        }
    }

    // ---- Speech output ----

    private fun speak(text: String) {
        setWidgetState(WidgetState.SPEAKING)
        voiceManager.speak(text) {
            setWidgetState(WidgetState.IDLE)
        }
    }

    // ---- Visual state ----

    private fun setWidgetState(state: WidgetState) {
        currentState = state
        val shouldPulse = state != WidgetState.IDLE
        pulseView.visibility = if (shouldPulse) View.VISIBLE else View.INVISIBLE

        val drawable = pulseView.background
        if (drawable is Animatable) {
            if (shouldPulse) drawable.start() else drawable.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[Job]?.cancel()
        voiceManager.destroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
