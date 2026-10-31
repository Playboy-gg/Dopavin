package com.dopavin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.dopavin.ui.glass.glassSurface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.Song
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * C-01. Floating card above the bottom navigation, tinted with the artwork colour.
 * Tap the body to open Now Playing; swipe sideways to skip; play/pause and next are separate 48dp-class
 * targets. The progress line at the bottom is decorative.
 */
@Composable
fun MiniPlayer(
    playback: PlaybackUi,
    song: Song?,
    position: () -> Long,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    /** Inside the nav bar: the bar supplies the glass / shape through [modifier]; no own card chrome. */
    bare: Boolean = false,
    /** Collapsed form (nav bar scrolled away): artwork + play/pause only. */
    compact: Boolean = false,
    /** Vertical offset in px while it drops in after Now Playing closes (0 = rest, negative = above). Draw phase. */
    drop: () -> Float = { 0f },
    /** Dragging the card up opens Now Playing: finger movement in px (negative = up), then release velocity px/s. */
    onSheetDrag: (Float) -> Unit = {},
    onSheetRelease: (Float) -> Unit = {},
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val motion = AppTheme.motion

    // Position is polled here, so only this composable recomposes twice a second.
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playback.mediaId, playback.isPlaying) {
        positionMs = position()
        if (playback.isPlaying) {
            while (isActive) {
                delay(500L)
                positionMs = position()
            }
        }
    }

    val tint = rememberArtworkTint(song?.artworkUri)
    val base = colors.surfaceHigh
    val background by animateColorAsState(
        targetValue = if (tint != null) lerp(base, tint, if (colors.isDark) 0.32f else 0.28f) else base,
        animationSpec = tween(motion.ambient),
        label = "miniTint",
    )

    val duration = if (playback.durationMs > 0L) playback.durationMs else song?.durationMs ?: 0L
    val progress = if (duration > 0L) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val description = stringResource(
        if (playback.isPlaying) R.string.mini_playing else R.string.mini_paused,
        playback.title,
        playback.artist,
    )
    val nextLabel = stringResource(R.string.cd_next)
    val previousLabel = stringResource(R.string.cd_previous)
    val shape = com.kyant.capsule.ContinuousCapsule()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val travelPx = with(density) { 160.dp.toPx() }
    val thresholdPx = with(density) { 56.dp.toPx() }
    // Rubber-band stretch toward the finger while swiping to change song: -1..1 (sign = direction).
    val latestSheetDrag by androidx.compose.runtime.rememberUpdatedState(onSheetDrag)
    val latestSheetRelease by androidx.compose.runtime.rememberUpdatedState(onSheetRelease)
    val sheetDrag = androidx.compose.foundation.gestures.rememberDraggableState { latestSheetDrag(it) }
    val sheetDragModifier = Modifier.draggable(
        state = sheetDrag,
        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
        onDragStopped = { latestSheetRelease(it) },
    )
    val flight = LocalArtworkFlight.current
    val stretch = remember { Animatable(0f) }
    val stretchSpring = com.dopavin.ui.motion.MotionSpecs.stretchSpring<Float>()

    if (compact) {
        Row(
            modifier
                .then(sheetDragModifier)
                .graphicsLayer {
                    val d = drop()
                    translationY = d
                    alpha = (1f + d / (com.dopavin.ui.motion.MotionTokens.MiniDropDp * this.density)).coerceIn(0f, 1f)
                }
                .clickableScale(pressedScale = 0.97f, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.sm),
        ) {
            ArtworkImage(
                uri = song?.artworkUri,
                sizePx = ArtworkSize.Row,
                shape = RoundedCornerShape(Dimens.radiusMedium),
                modifier = Modifier.size(36.dp).flightMini(flight),
                seed = song?.album ?: "",
            )
            PlayPauseButton(
                playing = playback.isPlaying,
                buffering = playback.isBuffering,
                onClick = onTogglePlay,
                size = 40.dp,
            )
        }
        return
    }

    Box(
        Modifier
            .then(sheetDragModifier)
            .graphicsLayer {
                // Expands toward the finger: the edge opposite the drag stays put, the card grows ~4.5% sideways
                // and gets a hair thinner, like a stretched liquid. Reduce Motion: no stretch.
                val d = drop()
                translationY = d
                alpha = (1f + d / (com.dopavin.ui.motion.MotionTokens.MiniDropDp * this.density)).coerceIn(0f, 1f)
                val k = if (motion.reduce) 0f else stretch.value
                scaleX = 1f + com.dopavin.ui.motion.MotionTokens.MiniStretch * kotlin.math.abs(k)
                scaleY = 1f - com.dopavin.ui.motion.MotionTokens.MiniStretchThin * kotlin.math.abs(k)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (k < 0f) 1f else 0f, 0.5f)
            }
            .then(if (bare) modifier.clip(shape) else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.lg, vertical = Dimens.xs)
                .glassSurface(shape, fallback = background, tint = background, floating = true)
        }),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.miniPlayerHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Gestures stay on the fixed outer layer; only the content below is moved.
                    .pointerInput(playback.hasNext, playback.hasPrevious) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val v = offsetX.value
                                scope.launch { stretch.animateTo(0f, stretchSpring) }
                                scope.launch {
                                    when {
                                        v < -thresholdPx && playback.hasNext -> {
                                            offsetX.animateTo(-travelPx, tween(com.dopavin.ui.motion.MotionTokens.SwipeOutMs))
                                            onNext()
                                            offsetX.snapTo(travelPx)
                                            offsetX.animateTo(0f, tween(com.dopavin.ui.motion.MotionTokens.SwipeInMs))
                                        }
                                        v > thresholdPx && playback.hasPrevious -> {
                                            offsetX.animateTo(travelPx, tween(com.dopavin.ui.motion.MotionTokens.SwipeOutMs))
                                            onPrevious()
                                            offsetX.snapTo(-travelPx)
                                            offsetX.animateTo(0f, tween(com.dopavin.ui.motion.MotionTokens.SwipeInMs))
                                        }
                                        else -> offsetX.animateTo(0f, spring())
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f, spring()) }
                                scope.launch { stretch.animateTo(0f, stretchSpring) }
                            },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                val blocked = (delta < 0 && !playback.hasNext) || (delta > 0 && !playback.hasPrevious)
                                val step = if (blocked) delta * 0.3f else delta
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + step).coerceIn(-travelPx, travelPx))
                                    stretch.snapTo(com.dopavin.ui.motion.MotionMath.stretchFor(offsetX.value, thresholdPx))
                                }
                            },
                        )
                    }
                    .graphicsLayer {
                        translationX = offsetX.value
                        alpha = 1f - (kotlin.math.abs(offsetX.value) / travelPx).coerceIn(0f, 1f) * 0.8f
                    }
                    .focusRing(RoundedCornerShape(Dimens.radiusLarge))
                    .clickableScale(pressedScale = 0.99f, onClick = onOpen)
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        customActions = listOf(
                            CustomAccessibilityAction(nextLabel) { onNext(); true },
                            CustomAccessibilityAction(previousLabel) { onPrevious(); true },
                        )
                    }
                    .padding(start = Dimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    uri = song?.artworkUri,
                    sizePx = ArtworkSize.Row,
                    shape = RoundedCornerShape(Dimens.radiusMedium),
                    modifier = Modifier.size(Dimens.artworkMini).flightMini(flight),
                    seed = song?.album ?: "",
                )
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    MarqueeText(text = playback.title, style = type.bodyStrong)
                    AppText(
                        text = playback.artist,
                        style = type.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            PlayPauseButton(
                playing = playback.isPlaying,
                buffering = playback.isBuffering,
                onClick = onTogglePlay,
                size = Dimens.miniPlay,
            )
            IconAction(
                icon = R.drawable.ic_skip_next,
                description = nextLabel,
                onClick = onNext,
                enabled = playback.hasNext,
            )
            Spacer(Modifier.width(Dimens.xs))
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress)
                .height(3.dp)
                .background(colors.gradientHorizontal),
        )
    }
}
