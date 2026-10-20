package com.cortana.hologram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Her hologram form (v0.2): the avatar portrait, background keyed out and
 * graded toward the hologram palette, stored as a Base64 WebP in
 * assets/hologram_body.b64 (keeps the repo text-only for the connector
 * push pipeline, and avoids a >64 KB string constant, which the Kotlin
 * compiler cannot hold). Decoded once, lazily, by HologramSurfaceView.
 */
object HologramBodyData {

    fun loadBitmap(context: Context): Bitmap? = try {
        val b64 = context.assets.open("hologram_body.b64").bufferedReader().use { it.readText() }
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
