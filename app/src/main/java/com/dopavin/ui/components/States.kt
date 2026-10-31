package com.dopavin.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * Centred message with an optional action. Used for empty library, empty search,
 * errors, and the permission screen. Scrolls if it does not fit (large text, landscape).
 *
 * Safe inside a scrolling parent: a scrollable child must never be measured with an unbounded
 * height (Compose throws "Vertically scrollable component was measured with an infinity maximum
 * height constraints"), so the inner scroll is only attached when the incoming height is bounded.
 * Without a bound the message simply takes its natural height (with a sensible minimum).
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    @DrawableRes icon: Int = R.drawable.ic_music_note,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bounded = constraints.hasBoundedHeight
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .then(
                    if (bounded) {
                        Modifier.verticalScroll(scroll)
                    } else {
                        Modifier.heightIn(min = 280.dp)
                    },
                )
                .padding(Dimens.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.gradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(Dimens.xl))
            AppText(
                text = title,
                style = type.headline,
                modifier = Modifier.semantics { heading() },
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(Dimens.sm))
                AppText(
                    text = body,
                    style = type.body,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = colors.textSecondary,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Dimens.xl))
                GradientButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Skeleton rows that pulse while the library loads (static with reduce-motion). */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val loading = stringResource(R.string.loading_library)
    val pulse: State<Float> = if (AppTheme.motion.reduce) {
        remember { mutableFloatStateOf(0.6f) }
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg)
            .semantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        repeat(8) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.rowMinHeight)
                    .alpha(pulse.value),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(Dimens.artworkRow)
                        .clip(RoundedCornerShape(Dimens.radiusSmall))
                        .background(colors.surfaceHigh),
                )
                Column(Modifier.padding(start = Dimens.md)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceHigh),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.38f)
                            .height(12.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceRaised),
                    )
                }
            }
        }
    }
}


/** A quiet error line with a "Try again" button, so a failed online screen is never a dead end. */
@Composable
fun RetryHint(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.md),
    ) {
        AppText(text = text, style = AppTheme.type.bodySmall, color = AppTheme.colors.textSecondary)
        TonalButton(text = stringResource(R.string.error_retry), onClick = onRetry)
    }
}
