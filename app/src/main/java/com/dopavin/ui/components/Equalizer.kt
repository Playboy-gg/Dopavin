package com.dopavin.ui.components
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopavin.ui.theme.AppTheme
@Composable
fun EqualizerBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 20.dp,
) {
    val animate = playing && !AppTheme.motion.reduce
    val a: State<Float>
    val b: State<Float>
    val c: State<Float>
    if (animate) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        a = transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
            label = "barA",
        )
        b = transition.animateFloat(
            initialValue = 1f, targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(430, easing = LinearEasing), RepeatMode.Reverse),
            label = "barB",
        )
        c = transition.animateFloat(
            initialValue = 0.4f, targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(610, easing = LinearEasing), RepeatMode.Reverse),
            label = "barC",
        )
    } else {
        a = remember { mutableFloatStateOf(0.4f) }
        b = remember { mutableFloatStateOf(0.7f) }
        c = remember { mutableFloatStateOf(0.5f) }
    }
    Canvas(modifier.size(size)) {
        val barWidth = this.size.width / 5f
        val gap = barWidth / 2f
        val total = barWidth * 3 + gap * 2
        val startX = (this.size.width - total) / 2f
        val heights = floatArrayOf(a.value, b.value, c.value)
        for (i in 0..2) {
            val h = this.size.height * heights[i]
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barWidth + gap), this.size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
