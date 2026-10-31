package com.dopavin.ui.motion

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {
    @Test fun lerpEndpointsAndClamp() {
        assertEquals(10f, MotionMath.lerp(10f, 20f, 0f), 1e-6f)
        assertEquals(20f, MotionMath.lerp(10f, 20f, 1f), 1e-6f)
        assertEquals(15f, MotionMath.lerp(10f, 20f, 0.5f), 1e-6f)
        assertEquals(20f, MotionMath.lerp(10f, 20f, 3f), 1e-6f)
        assertEquals(10f, MotionMath.lerp(10f, 20f, -1f), 1e-6f)
    }

    @Test fun inverseLerpHandlesEqualBounds() {
        assertEquals(0f, MotionMath.inverseLerp(5f, 5f, 9f), 1e-6f)
        assertEquals(0.25f, MotionMath.inverseLerp(0f, 8f, 2f), 1e-6f)
    }

    @Test fun popDurationScalesWithRemainingDistance() {
        assertEquals(MotionTokens.PagePopMs, MotionMath.popDurationMs(1f))
        assertEquals(MotionTokens.PopMinMs, MotionMath.popDurationMs(0f))
        assertTrue(MotionMath.popDurationMs(0.5f) in MotionTokens.PopMinMs..MotionTokens.PagePopMs)
    }

    @Test fun heldPlayerNeverPassesTwelvePercent() {
        assertEquals(0f, MotionMath.heldPlayerOffset(0f), 1e-6f)
        assertEquals(0.12f, MotionMath.heldPlayerOffset(1f), 1e-6f)
        assertEquals(0.12f, MotionMath.heldPlayerOffset(5f), 1e-6f)
    }

    @Test fun playerScaleOnlyShrinksWhileHeld() {
        assertEquals(1f, MotionMath.playerScale(0f), 1e-6f)
        assertEquals(0.97f, MotionMath.playerScale(0.12f), 1e-6f)
        assertEquals(0.97f, MotionMath.playerScale(1f), 1e-6f)
    }

    @Test fun cornerRadiusIsBounded() {
        assertEquals(0f, MotionMath.playerCornerDp(0f), 1e-6f)
        assertEquals(MotionTokens.PlayerCornerDp, MotionMath.playerCornerDp(1f), 1e-6f)
    }

    @Test fun stretchIsSignedAndBounded() {
        assertEquals(0f, MotionMath.stretchFor(50f, 0f), 1e-6f)
        assertEquals(1f, MotionMath.stretchFor(1000f, 56f), 1e-6f)
        assertEquals(-1f, MotionMath.stretchFor(-1000f, 56f), 1e-6f)
        assertEquals(0.5f, MotionMath.stretchFor(56f, 56f), 1e-6f)
    }

    @Test fun flightFractionIsLinearAndClamped() {
        assertEquals(0f, MotionMath.flightFraction(0f), 1e-6f)
        assertEquals(0.12f, MotionMath.flightFraction(0.12f), 1e-6f)
        assertEquals(1f, MotionMath.flightFraction(1f), 1e-6f)
        assertEquals(1f, MotionMath.flightFraction(2f), 1e-6f)
    }

    private fun simulate(zeta: Float, steps: Int = 600): List<MotionMath.SpringState> {
        val k = 400f
        val c = zeta * 2f * kotlin.math.sqrt(k)
        var s = MotionMath.SpringState(0f, 0f)
        val out = ArrayList<MotionMath.SpringState>()
        repeat(steps) {
            s = MotionMath.springStep(s, 1f, k, c, 1f, 1f / 120f)
            out += s
        }
        return out
    }

    @Test fun springStaysFiniteAndConverges() {
        val path = simulate(0.5f)
        assertTrue(path.all { it.position.isFinite() && it.velocity.isFinite() })
        assertTrue(abs(path.last().position - 1f) < 0.01f)
    }

    @Test fun underdampedOvershootsOverdampedDoesNot() {
        assertTrue(simulate(0.4f).maxOf { it.position } > 1.05f)
        assertTrue(simulate(1.5f).maxOf { it.position } <= 1.0001f)
    }

    @Test fun dampingRatioFormula() {
        assertEquals(1f, MotionMath.dampingRatio(stiffness = 400f, damping = 40f), 1e-5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unboundedTimestepIsRejected() {
        MotionMath.springStep(MotionMath.SpringState(0f, 0f), 1f, 100f, 10f, 1f, 0.5f)
    }
}
