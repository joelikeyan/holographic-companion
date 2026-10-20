package com.cortana.hologram

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import java.util.Locale

/**
 * HER EARS (v0.4)
 *
 * A transparent trampoline activity whose only job is to open Android's
 * system speech-recognition dialog. v0.3 listened with a SpeechRecognizer
 * buried in the overlay service, which on many phones quietly hears
 * nothing; the system dialog is the reliable path, and it also shows the
 * user a visible "listening" UI. The recognized text is handed back to
 * HolographicOverlayService, which speaks Brain's reply.
 */
class VoiceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Zoey…")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (e: Exception) {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH) {
            val text = if (resultCode == RESULT_OK) {
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            } else {
                null
            }
            val svc = Intent(this, HolographicOverlayService::class.java)
            if (!text.isNullOrBlank()) {
                svc.action = HolographicOverlayService.ACTION_HEARD
                svc.putExtra(HolographicOverlayService.EXTRA_HEARD_TEXT, text)
            } else {
                svc.action = HolographicOverlayService.ACTION_HEARD_CANCEL
            }
            try {
                startService(svc)
            } catch (e: Exception) {
                // Service not running; nothing to hand the words to.
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_SPEECH = 42
    }
}
