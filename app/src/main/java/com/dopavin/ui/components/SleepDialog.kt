package com.dopavin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.dopavin.playback.SleepMode
import com.dopavin.playback.SleepState
import com.dopavin.ui.MusicActions
import com.dopavin.ui.glass.LocalGlassBackdrop
import com.dopavin.ui.glass.LocalGlassStyle
import com.dopavin.ui.glass.backdrop.drawBackdrop
import com.dopavin.ui.glass.backdrop.effects.blur
import com.dopavin.ui.glass.backdrop.effects.colorControls
import com.dopavin.ui.glass.backdrop.effects.lens
import com.dopavin.ui.glass.backdrop.highlight.Highlight
import com.dopavin.ui.theme.AppTheme

/**
 * Sleep timer as a liquid-glass dialog (look from the supplied Kyant0 dialog sample, fixed values -
 * not user-customisable). Drawn in the overlay layer so it blurs the player behind it. Without glass
 * it is a plain solid card.
 */
@Composable
fun SleepDialog(sleep: SleepState, actions: MusicActions, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val light = !colors.isDark
    val contentColor = if (light) Color.Black else Color.White
    val accentColor = colors.accent
    val containerColor = if (light) Color(0xFFFAFAFA).copy(0.6f) else Color(0xFF121212).copy(0.4f)
    val dimColor = if (light) Color(0xFF29293A).copy(0.23f) else Color(0xFF121212).copy(0.56f)
    val backdrop = LocalGlassBackdrop.current
    val glassOn = LocalGlassStyle.current.enabled && backdrop != null
    val shape = RoundedCornerShape(48.dp)

    BackHandler(onBack = onDismiss)

    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(dimColor)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(40.dp)
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
                stringResource(R.string.sleep_title),
                Modifier
                    .padding(28.dp, 24.dp, 28.dp, 4.dp)
                    .semantics { heading() },
                style = TextStyle(contentColor, 24.sp, FontWeight.Medium),
            )
            val status = when (sleep.mode) {
                SleepMode.Timer -> stringResource(R.string.sleep_status_timer, formatTime(sleep.remainingMs))
                SleepMode.EndOfTrack -> stringResource(R.string.sleep_status_end)
                SleepMode.Off -> stringResource(R.string.sleep_status_off)
            }
            BasicText(
                status,
                Modifier.padding(28.dp, 0.dp, 28.dp, 8.dp),
                style = TextStyle(contentColor.copy(0.68f), 15.sp),
            )
            Column(
                Modifier.padding(24.dp, 4.dp, 24.dp, 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    GlassDialogButton(
                        text = stringResource(R.string.sleep_minutes, minutes),
                        background = containerColor.copy(0.2f),
                        textColor = contentColor,
                        onClick = {
                            actions.startSleepTimer(minutes)
                            onDismiss()
                        },
                    )
                }
                GlassDialogButton(
                    text = stringResource(R.string.sleep_end_of_song),
                    background = containerColor.copy(0.2f),
                    textColor = contentColor,
                    onClick = {
                        actions.sleepAtEndOfTrack()
                        onDismiss()
                    },
                )
            }
            Row(
                Modifier
                    .padding(24.dp, 12.dp, 24.dp, 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassDialogButton(
                    text = stringResource(R.string.sel_cancel),
                    background = containerColor.copy(0.2f),
                    textColor = contentColor,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                if (sleep.mode != SleepMode.Off) {
                    GlassDialogButton(
                        text = stringResource(R.string.sleep_turn_off),
                        background = accentColor,
                        textColor = colors.onAccent,
                        onClick = {
                            actions.cancelSleep()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassDialogButton(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = TextStyle(textColor, 16.sp))
    }
}
