package com.cortana.hologram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sin

/**
 * HOLOGRAM RENDERER (v0.2)
 *
 * Draws the companion's bust-form hologram: her portrait (background keyed
 * out, graded toward the palette) floating above a glowing projection
 * platform. She bobs and sways gently, flickers like a real projection,
 * and shows a faint red/cyan chromatic fringe at her edges. Scanlines and
 * drifting embers complete the field. Palette:
 *
 *   Electric Blue #00E5FF · Soft Indigo #4A148C · Magenta #FF007F
 *
 * The Filament/Vulkan volumetric renderer is still the long-term plan and
 * will render into this same SurfaceView; HolographicOverlayService does
 * not need to change when it lands.
 */
class HologramSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var drawThread: Thread? = null
    @Volatile private var running = false
    private var startTime = System.currentTimeMillis()

    private val bodyBitmap: Bitmap? by lazy { HologramBodyData.loadBitmap(context) }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val scanPaint = Paint().apply {
        color = Color.argb(26, 0, 229, 255)
        strokeWidth = 2f
    }
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val fringeRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        alpha = 44
    }
    private val fringeCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        alpha = 44
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
                    Thread.sleep(33) // ~30 fps
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
        // Nothing to resize; the frame is laid out from the canvas size each draw.
    }

    /** Cheap deterministic glitch: mostly steady, occasional sharp dip. */
    private fun flicker(t: Float): Float {
        val step = (t * 11f).toInt()
        val hash = sin(step * 12.9898) * 43758.5453
        val frac = (hash - hash.toInt()).toFloat()
        return if (frac > 0.94f) 0.68f else 0.92f + 0.08f * sin(t * 23f).toFloat()
    }

    private fun drawFrame(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        if (w == 0f || h == 0f) return

        val t = (System.currentTimeMillis() - startTime) / 1000f
        val cx = w / 2f
        val pulse = (sin(t * 1.6) * 0.5 + 0.5).toFloat() // 0..1 heartbeat

        // --- Backlight glow behind her
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, h * 0.36f, w * 0.62f,
                intArrayOf(
                    Color.argb(120, 74, 20, 140),
                    Color.argb(50, 0, 120, 180),
                    Color.argb(0, 0, 229, 255)
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, h * 0.36f, w * 0.62f, glowPaint)

        // --- Her form
        val bmp = bodyBitmap
        if (bmp != null) {
            val figW = w * 1.04f
            val figH = figW * bmp.height / bmp.width
            val bob = sin(t * 0.9) * h * 0.008f
            val left = cx - figW / 2f
            val top = h * 0.015f + bob.toFloat()
            val dst = RectF(left, top, left + figW, top + figH)

            canvas.save()
            // Gentle sway, pivoting near her base.
            canvas.rotate(sin(t * 0.6).toFloat() * 1.1f, cx, h * 0.72f)

            // Chromatic fringe passes.
            val fringe = w * 0.006f
            val dstL = RectF(dst).apply { offset(-fringe, 0f) }
            val dstR = RectF(dst).apply { offset(fringe, 0f) }
            canvas.drawBitmap(bmp, null, dstL, fringeRedPaint)
            canvas.drawBitmap(bmp, null, dstR, fringeCyanPaint)

            // Main pass with projection flicker.
            bodyPaint.alpha = (232 * flicker(t)).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bmp, null, dst, bodyPaint)
            canvas.restore()
        }

        // --- Projection platform under her
        val py = h * 0.865f
        val baseR = w * 0.30f
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, py, baseR * (0.9f + 0.1f * pulse),
                intArrayOf(
                    Color.argb(190, 120, 60, 255),
                    Color.argb(110, 74, 20, 140),
                    Color.argb(0, 0, 229, 255)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawOval(RectF(cx - baseR, py - baseR * 0.22f, cx + baseR, py + baseR * 0.22f), corePaint)

        for (i in 0..2) {
            val rr = baseR * (0.75f + i * 0.22f) + sin(t * 1.2 + i) * 3f
            ringPaint.color = when (i % 3) {
                0 -> Color.argb(150, 0, 229, 255)
                1 -> Color.argb(110, 255, 0, 127)
                else -> Color.argb(90, 140, 90, 255)
            }
            canvas.drawOval(
                RectF(cx - rr.toFloat(), py - rr.toFloat() * 0.22f, cx + rr.toFloat(), py + rr.toFloat() * 0.22f),
                ringPaint
            )
        }

        // --- Scanlines drifting downward
        var y = (t * 40f) % 26f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, scanPaint)
            y += 26f
        }

        // --- Drifting light embers
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
