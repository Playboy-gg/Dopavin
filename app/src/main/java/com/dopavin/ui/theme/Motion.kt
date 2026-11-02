package com.dopavin.ui.theme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
@Immutable
class MotionSpec(val reduce: Boolean) {
    val short: Int = if (reduce) 0 else 120
    val medium: Int = if (reduce) 0 else 240
    val long: Int = if (reduce) 0 else 400
    val ambient: Int = if (reduce) 0 else 700
}
val LocalMotion = staticCompositionLocalOf { MotionSpec(reduce = false) }
