package com.cortana.hologram

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class HolographicOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var surfaceView: SurfaceView
    private var isExpanded = true

    // --- Voice (v0.3): Android's built-in TTS + SpeechRecognizer, Brain.kt replies.
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupOverlayWindow()
        initVoice()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "cortana_hologram_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Cortana AI Active",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Cortana AI Companion")
            .setContentText("Volumetric Neural Mesh Active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_hologram_layout, null)
        surfaceView = overlayView.findViewById(R.id.hologram_surface)

        surfaceView.setZOrderOnTop(true)
        surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)

        overlayView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var downTime = 0L

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        downTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            val heldMs = System.currentTimeMillis() - downTime
                            if (heldMs > 600) {
                                // Long-press: shrink to the halo (or stay quiet if already small).
                                if (isExpanded) toggleCompanionMode(layoutParams)
                            } else if (isExpanded) {
                                // Tap her: talk cycle — she speaks, then listens.
                                onTalkTap()
                            } else {
                                // Tap the halo: wake her up.
                                toggleCompanionMode(layoutParams)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(overlayView, layoutParams)
    }

    private fun toggleCompanionMode(params: WindowManager.LayoutParams) {
        if (isExpanded) {
            params.width = 180
            params.height = 180
            surfaceView.visibility = View.GONE
            overlayView.findViewById<View>(R.id.compact_halo_node).visibility = View.VISIBLE
            isExpanded = false
        } else {
            params.width = 800
            params.height = 1200
            overlayView.findViewById<View>(R.id.compact_halo_node).visibility = View.GONE
            surfaceView.visibility = View.VISIBLE
            isExpanded = true
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    // ------------------------------------------------------------------
    // Voice wiring
    // ------------------------------------------------------------------

    private fun initVoice() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(1.12f)
                tts?.setSpeechRate(1.0f)
                ttsReady = true
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "listen_prompt") {
                    mainHandler.post { startListening() }
                }
            }
        })

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onResults(results: Bundle?) {
                        listening = false
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (text.isNullOrBlank()) {
                            speak(Brain.didntCatch(), null)
                        } else {
                            val reply = Brain.respond(text)
                            val sleep = Brain.shouldSleep(text)
                            speak(reply, null)
                            if (sleep) {
                                mainHandler.postDelayed({
                                    if (isExpanded && ::overlayView.isInitialized) {
                                        @Suppress("UNCHECKED_CAST")
                                        toggleCompanionMode(overlayView.layoutParams as WindowManager.LayoutParams)
                                    }
                                }, 2600)
                            }
                        }
                    }

                    override fun onError(error: Int) {
                        listening = false
                        if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                            error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) {
                            speak(Brain.didntCatch(), null)
                        }
                    }
                })
            }
        }
    }

    private fun onTalkTap() {
        if (listening) {
            recognizer?.stopListening()
            listening = false
            tts?.stop()
            return
        }
        if (!ttsReady) {
            Toast.makeText(this, "Voice is warming up — tap again in a second.", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            speak("I need microphone permission to hear you. Open my app once and allow the microphone, then tap me again.", null)
            return
        }
        speak(Brain.listenPrompt(), "listen_prompt")
    }

    private fun startListening() {
        if (listening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
            listening = true
        } catch (e: Exception) {
            listening = false
        }
    }

    private fun speak(text: String, utteranceId: String?) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId ?: "line")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // already gone
        }
        tts?.stop()
        tts?.shutdown()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
