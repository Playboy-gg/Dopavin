package com.dopavin.ui.motion

/**
 * Every motion number (plain Kotlin, JVM-testable) of the gesture and transition work in one place (docs/12_MOTION_RULES.md).
 * Apple-inspired, not Apple's private tuning: values are tuned by feel on the device.
 */
object MotionTokens {
    // Durations (ms)
    const val FastMs = 160
    const val StandardMs = 280
    const val EmphasizedMs = 420
    /** Full page slide (button back, or a gesture finished from zero). */
    const val PagePopMs = 350
    /** Shortest finish of a page slide that was dragged almost all the way. */
    const val PopMinMs = 140
    const val PlayerExitMs = 300
    const val GestureCancelMs = 200
    const val SwipeOutMs = 120
    const val SwipeInMs = 200

    // Distances (fractions of the screen)
    const val ParallaxFraction = 0.30f
    const val ParallaxDim = 0.28f
    /** How far a held Now Playing may travel, as a fraction of the screen height. */
    const val PlayerHeldFraction = 0.12f
    const val PlayerHeldShrink = 0.03f
    const val PlayerCornerDp = 36f

    /** Swiping Now Playing down closes it when released beyond this many dp (as before the shared sheet). */
    const val SheetDismissDp = 120f

    /** Dragging the mini player up: opens when released above this fraction (0 = open) or flung faster than this. */
    const val SheetOpenAt = 0.5f
    const val SheetFlingPxPerSec = 900f

    /** Initial velocity (scale units/s) that makes the landing cover overshoot by roughly 5-6 %. */
    const val CoverLandKick = 2.2f

    // Mini player
    const val MiniDropDp = 24f
    const val MiniStretch = 0.045f
    const val MiniStretchThin = 0.015f
}
