package com.dopavin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks one representative, colourful tint from album art (no Palette library).
 * The tint colours Now Playing, collection headers and the mini player. Result 0 means "no usable colour".
 */
object ArtworkTint {

    const val NONE = 0

    private val cache = LruCache<String, Int>(512)

    /** Cached result (0 = known to have none), or null when not computed yet. */
    fun peek(uri: Uri): Int? = cache.get(uri.toString())

    suspend fun load(context: Context, uri: Uri): Int = withContext(Dispatchers.Default) {
        cache.get(uri.toString())?.let { return@withContext it }
        val bitmap = ArtworkLoader.loadBlocking(context, uri, ArtworkSize.Row)
        val tint = if (bitmap == null) NONE else compute(bitmap)
        cache.put(uri.toString(), tint)
        tint
    }

    private fun compute(source: Bitmap): Int {
        return try {
            val bitmap = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false) ?: return NONE
            } else {
                source
            }
            val bins = 12
            val weight = FloatArray(bins)
            val red = FloatArray(bins)
            val green = FloatArray(bins)
            val blue = FloatArray(bins)
            val hsv = FloatArray(3)
            val stepX = maxOf(1, bitmap.width / 24)
            val stepY = maxOf(1, bitmap.height / 24)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    Color.colorToHSV(pixel, hsv)
                    if (hsv[1] > 0.2f && hsv[2] > 0.2f) {
                        val bin = (hsv[0] / 30f).toInt().coerceIn(0, bins - 1)
                        val w = hsv[1] * hsv[2]
                        weight[bin] += w
                        red[bin] += Color.red(pixel) * w
                        green[bin] += Color.green(pixel) * w
                        blue[bin] += Color.blue(pixel) * w
                    }
                    x += stepX
                }
                y += stepY
            }
            var best = 0
            for (i in 1 until bins) if (weight[i] > weight[best]) best = i
            if (weight[best] < 0.5f) return NONE

            val r = (red[best] / weight[best]).toInt()
            val g = (green[best] / weight[best]).toInt()
            val b = (blue[best] / weight[best]).toInt()
            Color.colorToHSV(Color.rgb(r, g, b), hsv)
            hsv[1] = hsv[1].coerceIn(0.5f, 0.85f)
            hsv[2] = hsv[2].coerceIn(0.5f, 0.85f)
            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            NONE
        }
    }
}
