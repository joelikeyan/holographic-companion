package com.cortana.hologram

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sin

/**
 * PLACEHOLDER RENDERER (v0.1)
 *
 * A lightweight 2D Canvas animation that stands in for the planned
 * Google Filament / Vulkan volumetric renderer. It draws the companion's
 * energy signature — a pulsing violet-blue core, concentric telemetry
 * rings, scanlines and drifting embers — in the spec's palette:
 *
 *   Electric Blue #00E5FF · Soft Indigo #4A148C · Magenta #FF007F
 *
 * The Filament engine will render into this same SurfaceView later;
 * HolographicOverlayService does not need to change when it lands.
 */
class HologramSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var drawThread: Thread? = null
    @Volatile private var running = false
    private var startTime = System.currentTimeMillis()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val scanPaint = Paint().apply {
        color = Color.argb(28, 0, 229, 255)
        strokeWidth = 2f
    }
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        startTime = System.currentTimeMillis()
        drawThread = Thread {
            while (running) {
                val canvas: Canvas? = try {
                    holder.lockCanvas()
                } catch (e: Exception) {
                    null
                }
                if (canvas != null) {
                    try {
                        drawFrame(canvas)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
                try {
                    Thread.sleep(33) // ~30 fps placeholder loop
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also { it.start() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            drawThread?.join(300)
        } catch (e: InterruptedException) {
            // shutting down anyway
        }
        drawThread = null
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Nothing to resize in the placeholder renderer.
    }

    private fun drawFrame(canvas: Canvas) {
        // Fully transparent background so underlying apps show through.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        if (w == 0f || h == 0f) return

        val t = (System.currentTimeMillis() - startTime) / 1000f
        val cx = w / 2f
        val cy = h / 2f
        val baseR = minOf(w, h) * 0.30f

        // Breathing pulse (heartbeat of the projection field).
        val pulse = (sin(t * 1.6) * 0.5 + 0.5).toFloat() // 0..1

        // Glowing core: indigo center bleeding into electric blue.
        val coreR = baseR * (0.55f + 0.08f * pulse)
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, coreR,
                intArrayOf(
                    Color.argb(220, 120, 60, 255),  // violet-blue core
                    Color.argb(140, 74, 20, 140),   // soft indigo
                    Color.argb(0, 0, 229, 255)      // fade to transparent blue
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, coreR, corePaint)

        // Telemetry rings.
        for (i in 0..2) {
            val rr = baseR * (0.75f + i * 0.22f) + sin(t * 1.2 + i) * 4f
            ringPaint.color = when (i % 3) {
                0 -> Color.argb(150, 0, 229, 255)   // electric blue
                1 -> Color.argb(110, 255, 0, 127)   // magenta
                else -> Color.argb(90, 140, 90, 255) // indigo-violet
            }
            canvas.drawCircle(cx, cy, rr.toFloat(), ringPaint)
        }

        // Scanlines drifting downward.
        var y = (t * 40f) % 26f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, scanPaint)
            y += 26f
        }

        // Drifting light embers.
        for (i in 0 until 14) {
            val speed = 12f + (i * 7 % 23)
            val ex = ((i * 97.3f) % w + sin(t * 0.7 + i) * 12f).toFloat()
            val ey = h - ((t * speed + i * 61f) % (h + 40f)) + 20f
            emberPaint.color = if (i % 3 == 0) {
                Color.argb(170, 255, 0, 127)
            } else {
                Color.argb(170, 0, 229, 255)
            }
            canvas.drawCircle(ex, ey, 3.2f, emberPaint)
        }
    }
}
