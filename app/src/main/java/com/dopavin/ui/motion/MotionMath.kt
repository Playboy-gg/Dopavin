package com.dopavin.ui.motion
import kotlin.math.sqrt
object MotionMath {
    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
    fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * clamp01(fraction)
    fun inverseLerp(a: Float, b: Float, v: Float): Float = if (a == b) 0f else clamp01((v - a) / (b - a))
    fun popDurationMs(remaining: Float): Int =
        lerp(MotionTokens.PopMinMs.toFloat(), MotionTokens.PagePopMs.toFloat(), remaining).toInt()
    fun heldPlayerOffset(progress: Float): Float = MotionTokens.PlayerHeldFraction * clamp01(progress)
    fun playerScale(offset: Float): Float =
        1f - MotionTokens.PlayerHeldShrink * inverseLerp(0f, MotionTokens.PlayerHeldFraction, offset)
    fun playerCornerDp(offset: Float): Float = MotionTokens.PlayerCornerDp * clamp01(offset * 8f)
    fun flightFraction(sheet: Float): Float = clamp01(sheet)
    fun stretchFor(offsetPx: Float, thresholdPx: Float): Float =
        if (thresholdPx <= 0f) 0f else (offsetPx / (thresholdPx * 2f)).coerceIn(-1f, 1f)
    fun dampingRatio(stiffness: Float, damping: Float, mass: Float = 1f): Float =
        damping / (2f * sqrt(stiffness * mass))
    data class SpringState(val position: Float, val velocity: Float)
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
