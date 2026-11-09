package com.dopavin.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Compose animation specs built from [MotionTokens]. */
object MotionSpecs {
    val standardEasing = FastOutSlowInEasing

    fun <T> slide(durationMs: Int): FiniteAnimationSpec<T> = tween(durationMs, easing = standardEasing)

    /** Mini player settling after Now Playing leaves: lively, a little overshoot. */
    fun <T> dropSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 380f)

    /** Now Playing rising: no overshoot (a sheet that bounces past the top looks broken). */
    fun <T> openSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    /** The cover settling in the mini player: bouncy, rings back within a few frames. */
    fun <T> landSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.35f, stiffness = 500f)

    /** Rubber-band return of a cancelled drag (player sheet). */
    fun <T> cancelSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Mini player stretch returning to rest. */
    fun <T> stretchSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.5f, stiffness = 420f)
}
