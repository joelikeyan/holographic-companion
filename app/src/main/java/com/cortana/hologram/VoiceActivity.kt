package com.cortana.hologram

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * HER EARS (v0.5)
 *
 * v0.3 listened from the background service and heard nothing; v0.4 used
 * the system speech dialog, which works but throws a big Google window on
 * screen. This version splits the difference: a transparent activity (so
 * the recognizer runs in the foreground, where microphone rules let it
 * work) running its OWN SpeechRecognizer — no dialog, no popup, nothing
 * on screen but her. Every outcome is reported back to the service:
 * heard text, an error (she says she missed it), or a cancel. One silent
 * retry is attempted before giving up.
 */
class VoiceActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private var attempts = 0
    private var finished = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        report(HolographicOverlayService.ACTION_HEARD_ERROR, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            report(HolographicOverlayService.ACTION_HEARD_ERROR, null)
            return
        }
        handler.postDelayed(timeoutRunnable, 12_000)
        startListening()
    }

    private fun startListening() {
        attempts++
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // already gone
        }
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
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        report(HolographicOverlayService.ACTION_HEARD_ERROR, null)
                    } else {
                        report(HolographicOverlayService.ACTION_HEARD, text)
                    }
                }

                override fun onError(error: Int) {
                    if (attempts < 2 && !finished) {
                        handler.postDelayed({
                            if (!finished) startListening()
                        }, 200)
                    } else {
                        report(HolographicOverlayService.ACTION_HEARD_ERROR, null)
                    }
                }
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            report(HolographicOverlayService.ACTION_HEARD_ERROR, null)
        }
    }

    private fun report(action: String, text: String?) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeoutRunnable)
        val svc = Intent(this, HolographicOverlayService::class.java).apply {
            this.action = action
            if (text != null) {
                putExtra(HolographicOverlayService.EXTRA_HEARD_TEXT, text)
            }
        }
        try {
            startService(svc)
        } catch (e: Exception) {
            // Service not running; nothing to hand the words to.
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // already gone
        }
        super.onDestroy()
    }
}
