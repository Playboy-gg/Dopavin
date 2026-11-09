package com.dopavin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.dopavin.ui.glass.glassSurface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Modal bottom sheet: dimmed scrim, rounded panel that slides up, drag the handle (or the title area
 * you put under [header]) down to close, tap the scrim or press back to close. Closing animates and
 * then calls [onDismiss].
 *
 * Only the handle strip handles drags, so lists inside [content] scroll normally.
 */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.78f,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val closeLabel = stringResource(R.string.cd_close_sheet)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * heightFraction
        val heightPx = with(density) { sheetHeight.toPx() }
        val offset = remember { Animatable(heightPx) }

        LaunchedEffect(heightPx) {
            offset.animateTo(0f, tween(motion.medium))
        }

        fun dismiss() {
            scope.launch {
                offset.animateTo(heightPx, tween(motion.medium))
                onDismiss()
            }
        }

        BackHandler { dismiss() }

        val dim = (1f - (offset.value / heightPx).coerceIn(0f, 1f)) * 0.55f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismiss() },
                )
                .semantics { contentDescription = closeLabel },
        )

        val shape = com.kyant.capsule.ContinuousRoundedRectangle(
            topStart = androidx.compose.foundation.shape.CornerSize(Dimens.radiusXLarge),
            topEnd = androidx.compose.foundation.shape.CornerSize(Dimens.radiusXLarge),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, offset.value.roundToInt().coerceAtLeast(0)) }
                .glassSurface(shape, fallback = colors.surfaceRaised, tint = colors.surfaceRaised, floating = true)
                // Swallow taps so they do not reach the scrim behind the panel.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(heightPx) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offset.value > heightPx * 0.3f) {
                                    dismiss()
                                } else {
                                    scope.launch { offset.animateTo(0f, tween(motion.short)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offset.animateTo(0f, tween(motion.short)) }
                            },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                scope.launch {
                                    offset.snapTo((offset.value + delta).coerceAtLeast(0f))
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
            }
            content { dismiss() }
        }
    }
}

