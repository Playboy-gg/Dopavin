package com.dopavin.ui.motion
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
object MotionSpecs {
    val standardEasing = FastOutSlowInEasing
    fun <T> slide(durationMs: Int): FiniteAnimationSpec<T> = tween(durationMs, easing = standardEasing)
    fun <T> dropSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 380f)
    fun <T> openSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)
    fun <T> landSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.35f, stiffness = 500f)
    fun <T> cancelSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> stretchSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 420f)
}
