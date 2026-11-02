package com.dopavin.ui.components
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopavin.ui.glass.LocalGlassBackdrop
import com.dopavin.ui.glass.LocalGlassStyle
import com.dopavin.ui.glass.backdrop.drawBackdrop
import com.dopavin.ui.glass.backdrop.effects.blur
import com.dopavin.ui.glass.backdrop.effects.colorControls
import com.dopavin.ui.glass.backdrop.effects.lens
import com.dopavin.ui.glass.backdrop.highlight.Highlight
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
object MenuAnchor {
    private const val MAX_AGE_MS = 2_000L
    private var bounds: Rect? = null
    private var at = 0L
    fun record(coords: LayoutCoordinates?) {
        if (coords != null && coords.isAttached) {
            bounds = coords.boundsInWindow()
            at = SystemClock.uptimeMillis()
        }
    }
    fun fresh(): Rect? = bounds?.takeIf { SystemClock.uptimeMillis() - at < MAX_AGE_MS }
}
internal class CoordsHolder {
    var coords: LayoutCoordinates? = null
}
val LocalMenuInk = staticCompositionLocalOf { Color.Unspecified }
@Composable
fun AnchoredMenu(
    anchor: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val light = !colors.isDark
    val backdrop = LocalGlassBackdrop.current
    val glassOn = LocalGlassStyle.current.enabled && backdrop != null
    val ink = if (glassOn) (if (light) Color.Black else Color.White) else colors.textPrimary
    val container = if (light) Color(0xFFFAFAFA).copy(0.72f) else Color(0xFF121212).copy(0.58f)
    val shape = remember { ContinuousRoundedRectangle(28.dp) }
    val noRipple = remember { MutableInteractionSource() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(0f) }
    val openMs = AppTheme.motion.short
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(openMs)) }
    BackHandler(onBack = onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (if (colors.isDark) 0.34f else 0.16f) * progress.value))
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        )
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                CompositionLocalProvider(LocalMenuInk provides ink) {
                    Column(
                        Modifier
                            .graphicsLayer {
                                val p = progress.value
                                alpha = p
                                scaleX = 0.92f + 0.08f * p
                                scaleY = 0.92f + 0.08f * p
                            }
                            .then(
                                if (glassOn && backdrop != null) {
                                    Modifier.drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { shape },
                                        effects = {
                                            colorControls(brightness = if (light) 0.2f else 0f, saturation = 1.5f)
                                            blur(if (light) 16f.dp.toPx() else 10f.dp.toPx())
                                            lens(20f.dp.toPx(), 40f.dp.toPx(), depthEffect = true)
                                        },
                                        highlight = { Highlight.Plain },
                                        onDrawSurface = { drawRect(container) },
                                    )
                                } else {
                                    Modifier
                                        .shadow(16.dp, shape)
                                        .clip(shape)
                                        .background(colors.surfaceRaised)
                                        .border(1.dp, colors.divider, shape)
                                },
                            )
                            .clickable(interactionSource = noRipple, indication = null, onClick = {})
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        content = content,
                    )
                }
            },
        ) { measurables, constraints ->
            val margin = 12.dp.roundToPx()
            val gap = 6.dp.roundToPx()
            val w = min(width.roundToPx(), constraints.maxWidth - 2 * margin).coerceAtLeast(0)
            val a = anchor?.translate(-origin.x, -origin.y)
            val below = if (a != null) constraints.maxHeight - margin - (a.bottom.roundToInt() + gap) else 0
            val above = if (a != null) a.top.roundToInt() - gap - margin else 0
            val maxH = if (a != null) {
                max(below, above).coerceAtLeast(160.dp.roundToPx())
            } else {
                constraints.maxHeight - 2 * margin
            }.coerceAtMost(constraints.maxHeight - 2 * margin)
            val p = measurables.first().measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = maxH))
            val x: Int
            val y: Int
            if (a == null) {
                x = (constraints.maxWidth - p.width) / 2
                y = (constraints.maxHeight - p.height) / 2
            } else {
                val rightHalf = (a.left + a.right) / 2f > constraints.maxWidth / 2f
                x = (if (rightHalf) a.right.roundToInt() - p.width else a.left.roundToInt())
                    .coerceIn(margin, max(margin, constraints.maxWidth - margin - p.width))
                y = if (p.height <= below || below >= above) {
                    (a.bottom.roundToInt() + gap).coerceAtMost(constraints.maxHeight - margin - p.height)
                } else {
                    (a.top.roundToInt() - gap - p.height).coerceAtLeast(margin)
                }
            }
            layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
        }
    }
}
@Composable
fun AnchoredMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    destructive: Boolean = false,
) {
    val colors = AppTheme.colors
    val ink = LocalMenuInk.current.takeIf { it != Color.Unspecified } ?: colors.textPrimary
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val tint = if (destructive) colors.error else ink
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .heightIn(min = 48.dp)
            .clip(ContinuousCapsule)
            .background(if (pressed) ink.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Dimens.iconInline)) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (destructive) colors.error else ink.copy(alpha = 0.78f),
                    modifier = Modifier.size(Dimens.iconInline),
                )
            }
        }
        Spacer(Modifier.width(Dimens.md))
        AppText(label, AppTheme.type.body, color = tint, maxLines = 2)
    }
}
