/*
 * Adapted from Kyant0/AndroidLiquidGlass (catalog LiquidBottomTabs), Apache License 2.0,
 * Copyright 2025 Kyant0. Text tabs instead of icon tabs, Dopavin theme colours and package names.
 */
package com.dopavin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.dopavin.ui.glass.backdrop.Backdrop
import com.dopavin.ui.glass.backdrop.backdrops.layerBackdrop
import com.dopavin.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import com.dopavin.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import com.dopavin.ui.glass.backdrop.drawBackdrop
import com.dopavin.ui.glass.backdrop.effects.blur
import com.dopavin.ui.glass.backdrop.effects.lens
import com.dopavin.ui.glass.backdrop.effects.vibrancy
import com.dopavin.ui.glass.backdrop.highlight.Highlight
import com.dopavin.ui.glass.backdrop.shadow.InnerShadow
import com.dopavin.ui.glass.backdrop.shadow.Shadow
import com.dopavin.ui.glass.interaction.DampedDragAnimation
import com.dopavin.ui.glass.interaction.InteractiveHighlight
import com.dopavin.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Liquid-glass segmented tabs (Downloads: Songs / Artists / Liked). A glass capsule holds the tab
 * names; a glass puck marks the selected one, can be pressed and dragged between tabs (it magnifies
 * and refracts the tab name under it) and snaps to the nearest tab on release. Tapping a tab selects it.
 * [backdrop] is what the glass samples (the page behind it).
 */
@Composable
fun LiquidTabs(
    labels: List<String>,
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    /** Tab the puck starts on: the tab shown before this view was created, so a switch slides instead of jumping. */
    initialIndex: Int = selectedTabIndex(),
) {
    val tabsCount = labels.size
    if (tabsCount == 0) return
    val colors = AppTheme.colors
    val accentColor = colors.accent
    val dark = colors.isDark
    val containerColor = if (dark) Color(0xFF121212).copy(0.4f) else Color(0xFFFAFAFA).copy(0.4f)
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth
        val tabWidth = with(density) { (maxWidthPx.toFloat() - 8f.dp.toPx()) / tabsCount }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / maxWidthPx).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = initialIndex.coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }.collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            // Slide to where the selection already is (a switch made before this view existed).
            if (dampedDragAnimation.targetValue.fastRoundToInt() != currentIndex) {
                dampedDragAnimation.animateToValue(currentIndex.toFloat())
            }
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    // Select first, then slide: the page below must not wait for the puck.
                    onTabSelected(index)
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        // 1. The glass panel with the visible tab names.
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(PANEL_HEIGHT)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                TabLabel(
                    text = label,
                    selected = index == currentIndex,
                    scale = { 1f },
                    onClick = { currentIndex = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 2. An invisible accent-tinted copy of the names: only the puck shows it (through its lens).
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                    },
                    // No panel fill and no rim here: they would show up as a white haze inside the puck.
                    highlight = null,
                    shadow = null,
                )
                .then(interactiveHighlight.modifier)
                .height(PUCK_HEIGHT)
                .fillMaxWidth()
                .padding(horizontal = 4f.dp)
                .drawWithContent {
                    // Compose 1.7 has no graphicsLayer(colorFilter): tint the whole layer instead.
                    val tintPaint = Paint().apply { colorFilter = ColorFilter.tint(accentColor) }
                    drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), tintPaint) }
                    drawContent()
                    drawIntoCanvas { it.restore() }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEach { label ->
                TabLabel(
                    text = label,
                    selected = true,
                    scale = { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
                    onClick = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 3. The puck.
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        // Resting puck: a bright, slightly accent-tinted glass chip so the chosen tab stands out.
                        drawRect(
                            if (dark) Color.White.copy(0.16f) else Color.White.copy(0.55f),
                            alpha = 1f - progress,
                        )
                        drawRect(accentColor.copy(alpha = 0.14f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(PUCK_HEIGHT)
                .fillMaxWidth(1f / tabsCount),
        )
    }
}

private val PANEL_HEIGHT = 56.dp
private val PUCK_HEIGHT = 48.dp

@Composable
private fun TabLabel(
    text: String,
    selected: Boolean,
    scale: () -> Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tap = if (onClick == null) Modifier else Modifier
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .semantics {
            role = Role.Tab
            this.selected = selected
        }
    Box(
        modifier
            .then(tap)
            .fillMaxHeight()
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, AppTheme.type.label, color = AppTheme.colors.textPrimary, maxLines = 1)
    }
}
