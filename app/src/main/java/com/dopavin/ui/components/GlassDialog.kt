package com.dopavin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dopavin.R
import com.dopavin.ui.glass.LocalGlassBackdrop
import com.dopavin.ui.glass.LocalGlassStyle
import com.dopavin.ui.glass.backdrop.drawBackdrop
import com.dopavin.ui.glass.backdrop.effects.blur
import com.dopavin.ui.glass.backdrop.effects.colorControls
import com.dopavin.ui.glass.backdrop.effects.lens
import com.dopavin.ui.glass.backdrop.highlight.Highlight
import com.dopavin.ui.theme.AppTheme

/**
 * F-139. The one liquid-glass dialog of the app (look from the supplied Kyant0 dialog sample, fixed
 * values, not user-customisable). A call site just writes `GlassDialog(...)` where it used to write
 * `AlertDialog(...)`; the card is shown by [DialogLayer] in the app's overlay layer so it blurs the
 * screen behind it (an AlertDialog is a separate window and cannot). Without glass: a solid card.
 */
@Stable
class DialogHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalDialogHost = staticCompositionLocalOf<DialogHost?> { null }

/** Draws the dialog currently registered on [host]. Place it last, above sheets. */
@Composable
fun DialogLayer(host: DialogHost) {
    host.content?.invoke()
}

@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.sel_cancel),
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val host = LocalDialogHost.current
    val card: @Composable () -> Unit = {
        GlassDialogCard(title, onDismiss, confirmLabel, onConfirm, confirmEnabled, destructive, dismissLabel, body, content)
    }
    val latest by rememberUpdatedState(card)
    if (host == null) {
        latest()
        return
    }
    DisposableEffect(host) {
        val mine: @Composable () -> Unit = { latest() }
        host.content = mine
        onDispose { if (host.content === mine) host.content = null }
    }
}

@Composable
private fun GlassDialogCard(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String?,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    destructive: Boolean,
    dismissLabel: String,
    body: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val light = !colors.isDark
    val backdrop = LocalGlassBackdrop.current
    val glassOn = LocalGlassStyle.current.enabled && backdrop != null
    val glassInk = if (light) Color.Black else Color.White
    val ink = if (glassOn) glassInk else colors.textPrimary
    val containerColor = if (light) Color(0xFFFAFAFA).copy(0.6f) else Color(0xFF121212).copy(0.4f)
    val dimColor = if (light) Color(0xFF29293A).copy(0.23f) else Color(0xFF121212).copy(0.56f)
    val buttonFill = if (glassOn) containerColor.copy(0.2f) else colors.surfaceHigh
    val shape = RoundedCornerShape(36.dp)
    val noRipple = remember { MutableInteractionSource() }

    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(dimColor)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp)
                .then(
                    if (glassOn && backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                colorControls(brightness = if (light) 0.2f else 0f, saturation = 1.5f)
                                blur(if (light) 16f.dp.toPx() else 8f.dp.toPx())
                                lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                            },
                            highlight = { Highlight.Plain },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                    } else {
                        Modifier.clip(shape).background(colors.surfaceRaised)
                    },
                )
                .fillMaxWidth()
                // Taps inside the card must not reach the dismiss scrim.
                .clickable(interactionSource = noRipple, indication = null, onClick = {}),
        ) {
            BasicText(
                title,
                Modifier
                    .padding(28.dp, 24.dp, 28.dp, 4.dp)
                    .semantics { heading() },
                style = TextStyle(ink, 22.sp, FontWeight.Medium),
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp, 4.dp, 28.dp, 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (body != null) {
                    BasicText(body, style = TextStyle(ink.copy(0.74f), 15.sp, lineHeight = 21.sp))
                }
                content()
            }
            Row(
                Modifier
                    .padding(24.dp, 12.dp, 24.dp, 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogPillButton(
                    text = dismissLabel,
                    fill = buttonFill,
                    textColor = ink,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                if (confirmLabel != null) {
                    DialogPillButton(
                        text = confirmLabel,
                        fill = if (destructive) colors.error else colors.accent,
                        textColor = if (destructive) Color.White else colors.onAccent,
                        enabled = confirmEnabled,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A full-width pill button for dialogs: no ripple, no blur on the text. */
@Composable
fun DialogPillButton(
    text: String,
    fill: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) fill else fill.copy(alpha = 0.4f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = TextStyle(if (enabled) textColor else textColor.copy(0.6f), 16.sp, FontWeight.Medium))
    }
}

/** Text/ink colour for content placed inside a [GlassDialog]: black/white on glass, theme colour on the solid card. */
@Composable
fun glassInk(): Color {
    val colors = AppTheme.colors
    val glassOn = LocalGlassStyle.current.enabled && LocalGlassBackdrop.current != null
    return if (glassOn) (if (colors.isDark) Color.White else Color.Black) else colors.textPrimary
}
