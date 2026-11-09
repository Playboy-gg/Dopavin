package com.dopavin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dopavin.ui.glass.LocalGlassBackdrop
import com.dopavin.ui.glass.LocalGlassStyle
import com.dopavin.ui.glass.glassSurface
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay

/** One notice: text and an optional single action (e.g. Undo). */
class ToastData(val text: String, val actionLabel: String?, val onAction: (() -> Unit)?)

/**
 * The app's transient notice (replaces the Material snackbar). One at a time; a new one replaces the
 * old one. It always goes away by itself ([show] returns when it has been shown for its time), also
 * when it carries an action.
 */
class ToastState {
    var current by mutableStateOf<ToastData?>(null)
        private set

    /** Shows [data] for [durationMs], then hides it. Cancelling (a newer notice) hides it at once. */
    suspend fun show(data: ToastData, durationMs: Long) {
        current = data
        try {
            delay(durationMs)
        } finally {
            if (current === data) current = null
        }
    }

    fun dismiss() {
        current = null
    }
}

/** How long a notice stays: longer when it offers an action. */
fun toastDurationMs(hasAction: Boolean): Long = if (hasAction) 5_000L else 3_000L

/**
 * iOS-style banner: a rounded floating card that springs up from the bottom, with the message and an
 * accent-coloured action on its right. With Liquid Glass on it is a glass lens; otherwise a solid
 * themed card with a hairline edge. Swipe it down (or tap the action) to dismiss.
 */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val data = state.current
    var last by remember { mutableStateOf<ToastData?>(null) }
    if (data != null) last = data
    AnimatedVisibility(
        visible = data != null,
        modifier = modifier,
        // Reduce motion: the notice only fades, it does not spring in.
        enter = if (AppTheme.motion.reduce) fadeIn(tween(120)) else {
            slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(160))
        },
        exit = if (AppTheme.motion.reduce) fadeOut(tween(120)) else slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
    ) {
        last?.let { ToastCard(it, onDismiss = state::dismiss) }
    }
}

@Composable
private fun ToastCard(data: ToastData, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val glassOn = LocalGlassStyle.current.enabled && LocalGlassBackdrop.current != null
    val ink = if (glassOn) (if (colors.isDark) Color.White else Color.Black) else colors.textPrimary
    val shape = remember { ContinuousRoundedRectangle(22.dp) }
    var drag by remember { mutableFloatStateOf(0f) }
    val dismissPx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = drag.coerceAtLeast(0f) }
                .glassSurface(
                    shape = shape,
                    fallback = colors.surfaceRaised,
                    tint = if (colors.isDark) Color(0xFF121212) else Color(0xFFFAFAFA),
                    floating = true,
                )
                .then(if (glassOn) Modifier else Modifier.border(1.dp, colors.divider, shape))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        },
                        onDragEnd = {
                            if (drag > dismissPx) onDismiss()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    )
                }
                .semantics { liveRegion = LiveRegionMode.Polite }
                .heightIn(min = 52.dp)
                .padding(start = Dimens.lg, end = if (data.actionLabel != null) Dimens.xs else Dimens.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = data.text,
                style = AppTheme.type.bodySmall,
                color = ink,
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Dimens.sm),
            )
            if (data.actionLabel != null) {
                Box(
                    Modifier
                        .heightIn(min = Dimens.touchTarget)
                        .clickableScale(pressedScale = 0.95f) {
                            data.onAction?.invoke()
                            onDismiss()
                        }
                        .padding(horizontal = Dimens.md),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = data.actionLabel,
                        style = AppTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                    )
                }
            }
        }
    }
}
