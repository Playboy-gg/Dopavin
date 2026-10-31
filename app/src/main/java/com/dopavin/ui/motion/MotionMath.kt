package com.dopavin.ui.motion

import kotlin.math.sqrt

/**
 * Pure motion maths (no Compose, no Android) so it can be unit-tested on the JVM.
 * Units: fractions are 0..1, time is milliseconds unless the name says Seconds, distances are px.
 */
object MotionMath {
    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    /** a + (b - a) * p, with p clamped to 0..1. */
    fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * clamp01(fraction)

    /** Where [v] sits between [a] and [b] as a 0..1 fraction (0 when a == b). */
    fun inverseLerp(a: Float, b: Float, v: Float): Float = if (a == b) 0f else clamp01((v - a) / (b - a))

    /**
     * How long finishing a page back takes: a full slide takes [MotionTokens.PagePopMs]; from partway it takes
     * proportionally less, but never less than [MotionTokens.PopMinMs] (so a nearly finished drag does not snap).
     */
    fun popDurationMs(remaining: Float): Int =
        lerp(MotionTokens.PopMinMs.toFloat(), MotionTokens.PagePopMs.toFloat(), remaining).toInt()

    /** Held Now Playing: the system back progress (0..1) mapped to a small fraction of the screen height. */
    fun heldPlayerOffset(progress: Float): Float = MotionTokens.PlayerHeldFraction * clamp01(progress)

    /** Faint shrink of the sheet while held; once released (offset above the held range) the shrink stays put. */
    fun playerScale(offset: Float): Float =
        1f - MotionTokens.PlayerHeldShrink * inverseLerp(0f, MotionTokens.PlayerHeldFraction, offset)

    /** Corner radius (dp) of the leaving sheet: grows quickly with the first few percent of travel. */
    fun playerCornerDp(offset: Float): Float = MotionTokens.PlayerCornerDp * clamp01(offset * 8f)

    /**
     * Cover flight between Now Playing's cover (0) and the mini player's cover (1). Linear in the sheet value:
     * the cover covers only the distance to the mini player while the sheet covers the whole screen, so it always
     * moves slower than the player, in the same direction as the finger.
     */
    fun flightFraction(sheet: Float): Float = clamp01(sheet)

    /** Mini player stretch toward the finger, -1..1, from the drag distance. */
    fun stretchFor(offsetPx: Float, thresholdPx: Float): Float =
        if (thresholdPx <= 0f) 0f else (offsetPx / (thresholdPx * 2f)).coerceIn(-1f, 1f)

    /** Damping ratio zeta = c / (2 sqrt(k m)). < 1 overshoots, 1 is critical, > 1 never overshoots. */
    fun dampingRatio(stiffness: Float, damping: Float, mass: Float = 1f): Float =
        damping / (2f * sqrt(stiffness * mass))

    data class SpringState(val position: Float, val velocity: Float)

    /**
     * One semi-implicit Euler step of m x'' + c x' + k (x - target) = 0. Only for custom motion that Compose's
     * own springs cannot express; [dtSeconds] must be small (split larger gaps into several steps).
     */
    fun springStep(
        state: SpringState,
        target: Float,
        stiffness: Float,
        damping: Float,
        mass: Float,
        dtSeconds: Float,
    ): SpringState {
        require(mass > 0f) { "mass must be positive" }
        require(dtSeconds in 0f..0.05f) { "use a bounded timestep" }
        val acceleration = (-stiffness * (state.position - target) - damping * state.velocity) / mass
        val v = state.velocity + acceleration * dtSeconds
        return SpringState(state.position + v * dtSeconds, v)
    }
}
