package com.dopavin.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag/press helper for the liquid tab indicator (written for Dopavin: the upstream catalog helper
 * of the same name was not part of the supplied sources). [value] follows [targetValue] with a
 * springy lag, [pressProgress] goes 0..1 while a finger is down, and [scaleX]/[scaleY] swell with it.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    /**
     * Where the draggable element's left edge currently is. The element moves under the finger, so
     * pointer positions (local to it) must be shifted by this to measure real finger movement.
     */
    private val originX: () -> Float = { 0f },
) {
    private val valueAnimation = Animatable(initialValue, Float.VectorConverter, visibilityThreshold)
    private val pressAnimation = Animatable(0f, Float.VectorConverter, 0.001f)

    var targetValue by mutableFloatStateOf(initialValue)
        private set

    val value: Float get() = valueAnimation.value
    val pressProgress: Float get() = pressAnimation.value
    val velocity: Float get() = valueAnimation.velocity
    val scaleX: Float get() = lerp(initialScale, pressedScale, pressProgress)
    val scaleY: Float get() = lerp(initialScale, pressedScale, pressProgress)

    /** Moves [targetValue] and lets [value] chase it. */
    fun updateValue(newValue: Float) {
        targetValue = newValue.fastCoerceIn(valueRange.start, valueRange.endInclusive)
        val goal = targetValue
        animationScope.launch { valueAnimation.animateTo(goal, spring(dampingRatio = 0.8f, stiffness = 900f)) }
    }

    fun animateToValue(newValue: Float) = updateValue(newValue)

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            animationScope.launch { pressAnimation.animateTo(1f, spring(stiffness = 600f)) }
            onDragStarted(down.position)
            var lastX = down.position.x + originX()
            drag(down.id) { change ->
                change.consume()
                val x = change.position.x + originX()
                val dx = x - lastX
                lastX = x
                onDrag(size, Offset(dx, 0f))
            }
            onDragStopped()
            animationScope.launch { pressAnimation.animateTo(0f, spring(stiffness = 600f)) }
        }
    }
}
