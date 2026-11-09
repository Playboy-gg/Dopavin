package com.dopavin.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The accent ("tint") of buttons, icons, sliders and highlights. Pure Kotlin on ARGB ints so it is
 * JVM-testable. Setting values: 0 = the app's own colours, 1..[PRESETS].size = a preset, [DYNAMIC] =
 * follows the playing song's artwork.
 */
object AccentTint {
    const val DEFAULT = 0
    const val DYNAMIC = -1

    /** Preset base colours, in the order shown in Settings (setting value = index + 1). */
    val PRESETS: List<Int> = listOf(
        0xFFFF4D7D.toInt(), // rose
        0xFFFF9500.toInt(), // orange
        0xFFFFC21A.toInt(), // amber
        0xFF34C759.toInt(), // green
        0xFF1FC8B4.toInt(), // teal
        0xFF0A84FF.toInt(), // blue
        0xFF5E5CE6.toInt(), // indigo
        0xFFBF5AF2.toInt(), // purple
    )

    class Derived(val accent: Int, val start: Int, val end: Int, val onAccent: Int, val focusRing: Int)

    /** Setting value -> base colour, or null when the app's own colours apply (or no artwork yet for dynamic). */
    fun baseFor(setting: Int, artworkTint: Int?): Int? = when {
        setting == DYNAMIC -> artworkTint
        setting in 1..PRESETS.size -> PRESETS[setting - 1]
        else -> null
    }

    /** Accent, gradient ends and readable on-accent ink for [base] on a dark or light canvas. */
    fun derive(base: Int, dark: Boolean): Derived {
        val hsv = toHsv(base)
        val h = hsv[0]
        // Artwork colours can be grey or very dark: keep enough colour and brightness to read as a tint.
        val s = hsv[1].coerceIn(0.45f, 1f)
        var v = if (dark) hsv[2].coerceIn(0.85f, 1f) else hsv[2].coerceIn(0.55f, 0.80f)
        // On the light canvas the accent is also text and icon colour: darken until it reads (about 3:1).
        if (!dark) while (luminance(fromHsv(h, s, v)) > 0.30f && v > 0.30f) v -= 0.03f
        val accent = fromHsv(h, s, v)
        val start = fromHsv(wrap(h - 14f), s, if (dark) min(1f, v + 0.04f) else v)
        val end = fromHsv(wrap(h + 14f), s, v)
        // Whichever of near-black and white has the better contrast on the accent.
        val la = luminance(accent)
        val darkInk = 0xFF111111.toInt()
        val onAccent = if ((la + 0.05f) / (luminance(darkInk) + 0.05f) >= 1.05f / (la + 0.05f)) darkInk else 0xFFFFFFFF.toInt()
        val focus = fromHsv(h, if (dark) 0.35f else 0.7f, if (dark) 1f else 0.55f)
        return Derived(accent, start, end, onAccent, focus)
    }

    fun luminance(argb: Int): Float {
        fun lin(c: Int): Float {
            val x = c / 255f
            return if (x <= 0.03928f) x / 12.92f else Math.pow(((x + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * lin((argb shr 16) and 0xFF) + 0.7152f * lin((argb shr 8) and 0xFF) + 0.0722f * lin(argb and 0xFF)
    }

    private fun wrap(h: Float): Float = ((h % 360f) + 360f) % 360f

    fun toHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return floatArrayOf(wrap(h), if (mx == 0f) 0f else d / mx, mx)
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
