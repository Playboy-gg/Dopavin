package com.dopavin.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

/**
 * F-211: the widget's look, drawn into bitmaps because a home-screen widget cannot use Compose or live
 * backdrop blur. It mirrors the app: the artwork is blurred into an ambience that glows from the top edge
 * (like the full player), with a glass sheen and a thin light rim; the cover has rounded corners.
 */
internal object WidgetStyle {
    private const val W = 720
    private const val H = 200

    /** Brand gradient start, used when there is no artwork. */
    private const val BRAND = 0xFFFF8A4C.toInt()
    private const val CANVAS = 0xFF0B0B10.toInt()

    /** Rounded glass card: blurred artwork ambience, dark scrim for legible text, top glow, sheen, rim. */
    fun background(art: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = 56f
        val rect = RectF(0f, 0f, W.toFloat(), H.toFloat())
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawColor(CANVAS)

        val glow = if (art != null) dominant(art) else BRAND
        if (art != null) {
            // 10x10 and stretched back up with filtering = a cheap, soft blur of the whole cover.
            val tiny = Bitmap.createScaledBitmap(art, 10, 10, true)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val src = Rect(0, 0, tiny.width, (tiny.height * H.toFloat() / W * 2.2f).toInt().coerceIn(2, tiny.height))
            canvas.drawBitmap(tiny, src, rect, paint)
            if (tiny !== art) tiny.recycle()
        }
        // Dark scrim: text stays readable on any cover.
        canvas.drawRect(rect, Paint().apply { color = Color.argb(if (art != null) 150 else 0, 8, 8, 12) })
        // Ambience: colour glowing from the top edge, fading downwards (same idea as the player).
        canvas.drawRect(
            rect,
            Paint().apply {
                shader = RadialGradient(W / 2f, 0f, W * 0.55f, withAlpha(glow, 150), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            },
        )
        canvas.drawRect(
            rect,
            Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, H * 0.9f, withAlpha(glow, 90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            },
        )
        // Glass sheen: a faint white band across the upper half.
        canvas.drawRect(
            rect,
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, W * 0.6f, H.toFloat(),
                    intArrayOf(Color.argb(46, 255, 255, 255), Color.argb(8, 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        // Light rim, brighter at the top-left like the glass buttons in the app.
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(Color.argb(150, 255, 255, 255), Color.argb(30, 255, 255, 255), Color.argb(90, 255, 255, 255)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(Path().apply { addRoundRect(RectF(1.5f, 1.5f, W - 1.5f, H - 1.5f), radius, radius, Path.Direction.CW) }, rim)
        return out
    }

    /** The cover with rounded corners and a hairline rim (square [size] output). */
    fun cover(art: Bitmap, size: Int = 192): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val r = size * 0.2f
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val path = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        val side = minOf(art.width, art.height)
        val src = Rect((art.width - side) / 2, (art.height - side) / 2, (art.width + side) / 2, (art.height + side) / 2)
        canvas.drawBitmap(art, src, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
        canvas.drawPath(
            Path().apply { addRoundRect(RectF(1f, 1f, size - 1f, size - 1f), r, r, Path.Direction.CW) },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(70, 255, 255, 255)
            },
        )
        return out
    }

    /** Average colour of the cover, pushed towards a more vivid tone so the glow is visible. */
    private fun dominant(art: Bitmap): Int {
        val px = Bitmap.createScaledBitmap(art, 1, 1, true).let { b -> b.getPixel(0, 0).also { if (b !== art) b.recycle() } }
        val hsv = FloatArray(3)
        Color.colorToHSV(px, hsv)
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0.35f, 1f)
        hsv[2] = hsv[2].coerceIn(0.55f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
