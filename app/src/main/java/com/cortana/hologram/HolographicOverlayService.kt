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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.hypot

class HolographicOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var surfaceView: SurfaceView
    private var isExpanded = true

    // Her sizes, in px — pinch edits these and she remembers them (v0.4).
    private var expandedWidth = 800
    private var expandedHeight = 1200
    private var haloSize = 180

    // --- Voice (v0.3, reworked v0.4): TTS speaks; the system speech dialog
    // (VoiceActivity) listens; replies come from Brain.kt.
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var voiceBusy = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupOverlayWindow()
        initVoice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HEARD -> {
                voiceBusy = false
                val text = intent.getStringExtra(EXTRA_HEARD_TEXT)
                if (!text.isNullOrBlank()) {
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
            ACTION_HEARD_ERROR -> {
                // She missed it — say so out loud instead of going silent (v0.5).
                voiceBusy = false
                speak(Brain.didntCatch(), null)
            }
            ACTION_HEARD_CANCEL -> voiceBusy = false
        }
        return START_STICKY
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
            private var pinching = false
            private var pinchStartDist = 0f
            private var pinchStartW = 0
            private var reanchorPending = false

            private fun spacing(event: MotionEvent): Float {
                if (event.pointerCount < 2) return 0f
                return hypot(
                    (event.getX(0) - event.getX(1)).toDouble(),
                    (event.getY(0) - event.getY(1)).toDouble()
                ).toFloat()
            }

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        downTime = System.currentTimeMillis()
                        pinching = false
                        reanchorPending = false
                        return true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount == 2) {
                            pinching = true
                            pinchStartDist = spacing(event)
                            pinchStartW = if (layoutParams.width > 0) {
                                layoutParams.width
                            } else {
                                v?.width ?: if (isExpanded) expandedWidth else haloSize
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (pinching && event.pointerCount == 2) {
                            val d = spacing(event)
                            if (pinchStartDist > 0f && d > 0f) {
                                val scale = d / pinchStartDist
                                // Grow/shrink around her CENTER (v0.5): shift the
                                // window by half the size change on each step.
                                if (isExpanded) {
                                    val prevW = expandedWidth
                                    val prevH = expandedHeight
                                    val newW = (pinchStartW * scale).toInt().coerceIn(360, 1500)
                                    expandedWidth = newW
                                    expandedHeight = (newW * 1.5f).toInt()
                                    layoutParams.width = expandedWidth
                                    layoutParams.height = expandedHeight
                                    layoutParams.x -= (expandedWidth - prevW) / 2
                                    layoutParams.y -= (expandedHeight - prevH) / 2
                                } else {
                                    val prevS = haloSize
                                    val newS = (pinchStartW * scale).toInt().coerceIn(120, 420)
                                    haloSize = newS
                                    layoutParams.width = newS
                                    layoutParams.height = newS
                                    layoutParams.x -= (newS - prevS) / 2
                                    layoutParams.y -= (newS - prevS) / 2
                                }
                                windowManager.updateViewLayout(overlayView, layoutParams)
                            }
                            return true
                        }
                        if (reanchorPending) {
                            // First move after a pinch ends: re-anchor so she doesn't jump.
                            initialX = layoutParams.x
                            initialY = layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            reanchorPending = false
                            return true
                        }
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (pinching) reanchorPending = true
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPinching = pinching
                        pinching = false
                        if (wasPinching) return true
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            val heldMs = System.currentTimeMillis() - downTime
                            if (heldMs > 600) {
                                // Long-press: shrink to the halo.
                                if (isExpanded) toggleCompanionMode(layoutParams)
                            } else if (isExpanded) {
                                // Tap her: talk cycle.
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
        // Anchor to her CENTER (v0.5): shrinking/growing keeps her middle
        // where it was instead of jumping to the old top-left corner.
        val oldW = if (params.width > 0) params.width else overlayView.width
        val oldH = if (params.height > 0) params.height else overlayView.height
        val newW: Int
        val newH: Int
        if (isExpanded) {
            newW = haloSize
            newH = haloSize
            surfaceView.visibility = View.GONE
            overlayView.findViewById<View>(R.id.compact_halo_node).visibility = View.VISIBLE
            isExpanded = false
        } else {
            newW = expandedWidth
            newH = expandedHeight
            overlayView.findViewById<View>(R.id.compact_halo_node).visibility = View.GONE
            surfaceView.visibility = View.VISIBLE
            isExpanded = true
        }
        params.width = newW
        params.height = newH
        params.x += (oldW - newW) / 2
        params.y += (oldH - newH) / 2
        windowManager.updateViewLayout(overlayView, params)
    }

    // ------------------------------------------------------------------
    // Voice wiring
    // ------------------------------------------------------------------

    private fun initVoice() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.12f)
                ttsReady = true
                pickFemaleVoice(0)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "listen_prompt") {
                    mainHandler.post { launchVoiceDialog() }
                }
            }
        })
    }

    /**
     * The default engine voice on many phones is male. Explicitly choose a
     * female voice when the engine offers one (Google TTS exposes names
     * like "en-us-x-sfg#female_1"); otherwise lift the pitch as a fallback.
     * The voice list can arrive asynchronously, so retry a few times.
     */
    private fun pickFemaleVoice(attempt: Int) {
        val engine = tts ?: return
        try {
            val voices = engine.voices
            if (voices.isNullOrEmpty()) {
                if (attempt < 3) {
                    mainHandler.postDelayed({ pickFemaleVoice(attempt + 1) }, 400)
                } else {
                    engine.setPitch(1.3f)
                }
                return
            }
            val female = voices.firstOrNull { v ->
                v.name.contains("female", ignoreCase = true) ||
                    (v.features?.any { it.contains("female", ignoreCase = true) } == true)
            } ?: voices.firstOrNull { v ->
                v.name.contains("x-sfg", ignoreCase = true) ||
                    v.name.contains("x-tpf", ignoreCase = true)
            }
            if (female != null) {
                engine.voice = female
                engine.setPitch(1.05f)
            } else {
                engine.setPitch(1.3f)
            }
        } catch (e: Exception) {
            engine.setPitch(1.3f)
        }
    }

    private fun onTalkTap() {
        if (voiceBusy) {
            tts?.stop()
            voiceBusy = false
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            speak("I need microphone permission to hear you. Open my app once and allow the microphone, then tap me again.", null)
            return
        }
        if (ttsReady) {
            speak(Brain.listenPrompt(), "listen_prompt")
        } else {
            launchVoiceDialog()
        }
    }

    private fun launchVoiceDialog() {
        try {
            val intent = Intent(this, VoiceActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            voiceBusy = true
        } catch (e: Exception) {
            voiceBusy = false
        }
    }

    private fun speak(text: String, utteranceId: String?) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId ?: "line")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    companion object {
        const val ACTION_HEARD = "com.cortana.hologram.ACTION_HEARD"
        const val ACTION_HEARD_CANCEL = "com.cortana.hologram.ACTION_HEARD_CANCEL"
        const val ACTION_HEARD_ERROR = "com.cortana.hologram.ACTION_HEARD_ERROR"
        const val EXTRA_HEARD_TEXT = "heard_text"
    }
}
