package com.dopavin.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dopavin.ui.theme.AppTheme

/**
 * Display-only switch (the row around it owns the click and the Role.Switch semantics).
 * Glass track that glows accent when on; solid track when glass is off.
 */
@Composable
fun GlassSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val glassOn = LocalGlassStyle.current.enabled && LocalGlassBackdrop.current != null
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.surfaceHigh,
        animationSpec = tween(AppTheme.motion.short),
        label = "switchTrack",
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(AppTheme.motion.short),
        label = "switchThumb",
    )
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .glassSurface(CircleShape, fallback = trackColor, tint = trackColor)
            .then(if (checked && glassOn) Modifier.background(colors.accent.copy(alpha = 0.55f), CircleShape) else Modifier)
            .border(1.dp, if (checked) Color.Transparent else colors.controlOutline.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(28.dp)
                .shadow(3.dp, CircleShape)
                .background(if (checked) Color.White else colors.textSecondary, CircleShape),
        )
    }
}
