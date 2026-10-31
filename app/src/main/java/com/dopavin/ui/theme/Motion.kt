package com.dopavin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion durations in ms. When the system animation scale is 0 ("reduce motion"),
 * every duration is 0 and decorative loops are switched off ([reduce]).
 */
@Immutable
class MotionSpec(val reduce: Boolean) {
    val short: Int = if (reduce) 0 else 120
    val medium: Int = if (reduce) 0 else 240
    val long: Int = if (reduce) 0 else 400
    /** Colour changes that follow the artwork (Now Playing, headers). */
    val ambient: Int = if (reduce) 0 else 700
}

val LocalMotion = staticCompositionLocalOf { MotionSpec(reduce = false) }
