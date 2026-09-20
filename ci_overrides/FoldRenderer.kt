package com.sycompany.duomorph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** GPU-friendly visual layer shared by the launcher and the global accessibility overlay. */
object FoldRenderer {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun strength(progress: Float): Float =
        sin(PI * progress.coerceIn(0f, 1f).toDouble()).toFloat().coerceIn(0f, 1f)

    fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun drawFoldedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        progress: Float,
        inner: Boolean,
        hingeX: Float,
        globalAlpha: Int = 255
    ) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        if (w <= 0f || h <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return

        val s = strength(progress)
        val eased = ease(progress)
        val blurRadius = min(w, h) * 0.022f * s
        bitmapPaint.alpha = globalAlpha.coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= 31 && blurRadius > 0.8f) {
            bitmapPaint.setRenderEffect(
                RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius * 0.72f,
                    Shader.TileMode.CLAMP
                )
            )
        } else if (Build.VERSION.SDK_INT >= 31) {
            bitmapPaint.setRenderEffect(null)
        }

        val skew = (1f - eased) * (if (inner) -0.020f else 0.020f)
        val scaleX = 0.948f + 0.052f * eased
        val scaleY = 1f - 0.012f * s

        canvas.save()
        canvas.translate(hingeX, h * 0.5f)
        canvas.scale(scaleX, scaleY)
        canvas.skew(skew, 0f)
        canvas.translate(-hingeX, -h * 0.5f)
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, w, h), bitmapPaint)
        canvas.restore()

        if (Build.VERSION.SDK_INT >= 31) bitmapPaint.setRenderEffect(null)

        val radius = w * (0.075f + 0.12f * s)
        val left = max(0f, hingeX - radius)
        val right = min(w, hingeX + radius)
        val stripCount = 18
        val stripW = max(1f, (right - left) / stripCount)
        val srcScaleX = bitmap.width / w
        bitmapPaint.alpha = (45 + 85 * s).toInt().coerceIn(0, 150)
        for (i in 0 until stripCount) {
            val dx = left + i * stripW
            val center = dx + stripW * 0.5f
            val normalized = ((center - hingeX) / max(1f, radius)).coerceIn(-1f, 1f)
            val displacement = sin(normalized * PI.toFloat()) * w * 0.012f * s
            val src = Rect(
                (dx * srcScaleX).toInt().coerceIn(0, bitmap.width - 1),
                0,
                ((dx + stripW + 1f) * srcScaleX).toInt().coerceIn(1, bitmap.width),
                bitmap.height
            )
            val dst = RectF(dx + displacement, 0f, dx + stripW + displacement + 1f, h)
            if (src.right > src.left) canvas.drawBitmap(bitmap, src, dst, bitmapPaint)
        }
        bitmapPaint.alpha = 255
    }

    fun drawGlass(canvas: Canvas, progress: Float, inner: Boolean, hingeX: Float) {
        val s = strength(progress)
        if (s < 0.003f) return
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        fxPaint.shader = null
        fxPaint.color = Color.argb((108f * s).toInt(), 1, 6, 18)
        canvas.drawRect(0f, 0f, w, h, fxPaint)

        val radius = w * (0.055f + 0.19f * s)
        fxPaint.shader = LinearGradient(
            hingeX - radius,
            0f,
            hingeX + radius,
            0f,
            intArrayOf(
                Color.argb(0, 120, 180, 255),
                Color.argb((70f * s).toInt(), 70, 120, 210),
                Color.argb((165f * s).toInt(), 225, 240, 255),
                Color.argb((80f * s).toInt(), 255, 255, 255),
                Color.argb((68f * s).toInt(), 30, 60, 115),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.25f, 0.44f, 0.515f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(max(0f, hingeX - radius), 0f, min(w, hingeX + radius), h, fxPaint)
        fxPaint.shader = null

        fxPaint.color = Color.argb((210f * s).toInt(), 245, 250, 255)
        val lineW = max(1.5f, w * 0.0013f)
        canvas.drawRect(hingeX - lineW, 0f, hingeX + lineW, h, fxPaint)

        val shadowWidth = w * (0.025f + 0.055f * s)
        val from = if (inner) hingeX - shadowWidth else hingeX
        val to = if (inner) hingeX else hingeX + shadowWidth
        fxPaint.shader = LinearGradient(
            from, 0f, to, 0f,
            if (inner) intArrayOf(Color.TRANSPARENT, Color.argb((100f * s).toInt(), 0, 0, 0))
            else intArrayOf(Color.argb((100f * s).toInt(), 0, 0, 0), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(min(from, to), 0f, max(from, to), h, fxPaint)
        fxPaint.shader = null
    }
}
