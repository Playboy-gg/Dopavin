package com.dopavin.ui.components
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import com.dopavin.ui.glass.glassSurface
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = AppTheme.colors.focusRing
    this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(Dimens.focusRingWidth, ring, shape) else Modifier)
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.clickableScale(
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = AppTheme.motion.reduce
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val holder = remember { CoordsHolder() }
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .onGloballyPositioned { holder.coords = it }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick?.let { long ->
                {
                    MenuAnchor.record(holder.coords)
                    long()
                }
            },
            onClick = {
                MenuAnchor.record(holder.coords)
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}
@Composable
fun AppText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.textPrimary,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.textPrimary,
    textAlign: TextAlign? = null,
) {
    val reduce = AppTheme.motion.reduce
    var overflowing by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableStateOf(false) }
    val fade = MARQUEE_FADE
    Text(
        text = text,
        modifier = if (reduce) {
            modifier
        } else {
            modifier
                .then(if (overflowing) Modifier.fadingEdges(fade) else Modifier)
                .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_500, velocity = 40.dp)
        },
        color = color,
        style = style,
        maxLines = 1,
        softWrap = !reduce,
        overflow = if (reduce) TextOverflow.Ellipsis else TextOverflow.Clip,
        textAlign = textAlign,
        onTextLayout = { overflowing = it.hasVisualOverflow },
    )
}
private val MARQUEE_FADE = 14.dp
private fun Modifier.fadingEdges(width: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 2f)
        if (w <= 0f) return@drawWithContent
        val f = w / size.width
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to Color.Transparent,
                f to Color.Black,
                1f - f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.textPrimary,
    enabled: Boolean = true,
    container: Color = Color.Transparent,
    size: Dp = Dimens.touchTarget,
    iconSize: Dp = Dimens.iconControl,
    glass: Boolean = false,
) {
    val shownTint = if (enabled) tint else tint.copy(alpha = 0.38f)
    Box(
        modifier = modifier
            .size(size)
            .clickableScale(enabled = enabled, pressedScale = 0.88f, onClick = onClick)
            .then(
                if (glass) {
                    Modifier.glassSurface(CircleShape, fallback = container)
                } else {
                    Modifier.clip(CircleShape).background(container)
                },
            )
            .focusRing(CircleShape)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = shownTint,
        )
    }
}
@Composable
fun GlassIconPill(
    modifier: Modifier = Modifier,
    fallback: Color = Color.Transparent,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.glassSurface(CircleShape, fallback = fallback),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
@Composable
fun ToggleIconAction(
    @DrawableRes icon: Int,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inactiveTint: Color = AppTheme.colors.textSecondary,
    glass: Boolean = false,
) {
    val colors = AppTheme.colors
    val tint by animateColorAsState(
        targetValue = if (active) colors.accent else inactiveTint,
        animationSpec = tween(AppTheme.motion.short),
        label = "toggleTint",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        IconAction(
            icon = icon,
            description = description,
            onClick = onClick,
            tint = tint,
            iconSize = 26.dp,
            glass = glass,
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    description: String? = null,
) {
    val colors = AppTheme.colors
    val shape = CircleShape
    Row(
        modifier = modifier
            .heightIn(min = Dimens.touchTarget)
            .clickableScale(onClick = onClick)
            .clip(shape)
            .background(colors.gradient)
            .focusRing(shape)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = Dimens.xl, vertical = Dimens.md),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(Dimens.iconControl),
            )
            Spacer(Modifier.size(Dimens.sm))
        }
        AppText(text, AppTheme.type.label, color = colors.onAccent)
    }
}
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    description: String? = null,
    accent: Boolean = false,
) {
    val colors = AppTheme.colors
    val shape = CircleShape
    Row(
        modifier = modifier
            .heightIn(min = Dimens.touchTarget)
            .clickableScale(onClick = onClick)
            .glassSurface(
                shape,
                fallback = if (accent) colors.accent else colors.surfaceHigh,
                tint = if (accent) colors.accent else colors.surface,
                tintAlpha = if (accent) 0.55f else null,
            )
            .focusRing(shape)
            .semantics { if (description != null) contentDescription = description }
            .padding(horizontal = Dimens.xl, vertical = Dimens.md),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (accent) colors.onAccent else colors.textPrimary,
                modifier = Modifier.size(Dimens.iconControl),
            )
            Spacer(Modifier.size(Dimens.sm))
        }
        AppText(text, AppTheme.type.label, color = if (accent) colors.onAccent else colors.textPrimary)
    }
}
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = CircleShape
    val bg by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.surfaceRaised,
        animationSpec = tween(AppTheme.motion.short),
        label = "chipBg",
    )
    Box(
        modifier = modifier
            .heightIn(min = Dimens.chipHeight)
            .clickableScale(role = Role.Tab, onClick = onClick)
            .then(
                if (selected) {
                    Modifier.clip(shape).background(bg)
                } else {
                    Modifier.glassSurface(shape, fallback = bg)
                },
            )
            .focusRing(shape)
            .semantics { this.selected = selected }
            .padding(horizontal = Dimens.lg),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = text,
            style = AppTheme.type.label,
            color = if (selected) colors.onAccent else colors.textPrimary,
        )
    }
}
@Composable
fun PlayPauseButton(
    playing: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.playButton,
) {
    val colors = AppTheme.colors
    val label = stringResource(if (playing) R.string.cd_pause else R.string.cd_play)
    Box(
        modifier = modifier
            .size(size)
            .clickableScale(pressedScale = 0.92f, onClick = onClick)
            .clip(CircleShape)
            .background(colors.gradient)
            .focusRing(CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            androidx.compose.material3.CircularProgressIndicator(
                color = colors.onAccent,
                trackColor = Color.Transparent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(size * 0.45f),
            )
        } else {
            Crossfade(
                targetState = playing,
                animationSpec = tween(AppTheme.motion.short),
                label = "playPause",
            ) { isPlaying ->
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = AppTheme.colors.textPrimary.copy(alpha = 0.2f),
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val enabled = durationMs > 0L
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction = when {
        dragging -> dragFraction
        enabled -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownMs = if (dragging) (dragFraction * durationMs).toLong() else positionMs
    val unknown = stringResource(R.string.time_unknown)
    val elapsed = formatTime(shownMs)
    val remaining = if (enabled) "-" + formatTime((durationMs - shownMs).coerceAtLeast(0L)) else unknown
    val total = if (enabled) formatTime(durationMs) else unknown
    val seekLabel = stringResource(R.string.seek_label)
    val seekState = stringResource(R.string.seek_state, elapsed, total)
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) Dimens.seekTrackActive else Dimens.seekTrack,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "seekTrack",
    )
    val thumbColor = colors.textPrimary
    val gradient = colors.gradientHorizontal
    val gradientStart = colors.accentStart
    val gradientEnd = colors.accentEnd
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dimens.seekTouchHeight)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(enabled, durationMs) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((f * durationMs).toLong())
                    }
                }
                .pointerInput(enabled, durationMs) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            onSeek((dragFraction * durationMs).toLong())
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
                .semantics {
                    contentDescription = seekLabel
                    stateDescription = seekState
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        if (!enabled) {
                            false
                        } else {
                            onSeek((target.coerceIn(0f, 1f) * durationMs).toLong())
                            true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(Dimens.seekTouchHeight)) {
                val thumbRadius = 0f
                val h = trackHeight.toPx()
                val left = thumbRadius
                val usable = (this.size.width - thumbRadius * 2f).coerceAtLeast(1f)
                val top = (this.size.height - h) / 2f
                val radius = CornerRadius(h / 2f)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, top),
                    size = Size(usable, h),
                    cornerRadius = radius,
                )
                val active = usable * fraction
                if (active > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(gradientStart, gradientEnd),
                            startX = left,
                            endX = left + usable,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(active, h),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.xs),
        ) {
            AppText(elapsed, type.numeric, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            AppText(remaining, type.numeric, color = colors.textSecondary)
        }
    }
}
