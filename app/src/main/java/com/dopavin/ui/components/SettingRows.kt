package com.dopavin.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/** Tappable settings row: title, optional subtitle, optional chevron. */
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chevron: Boolean = true,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .focusRing(RoundedCornerShape(0.dp))
            .clickableScale(pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, AppTheme.type.bodyStrong, maxLines = 2)
            if (subtitle != null) {
                AppText(subtitle, AppTheme.type.bodySmall, color = colors.textSecondary, maxLines = 3)
            }
        }
        if (chevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(Dimens.iconControl),
            )
        }
    }
}

/** Row with a switch. The whole row toggles; the switch itself is not a second target. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // the Switch itself animates; no row-wide grey overlay
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(Dimens.iconControl),
            )
            Spacer(Modifier.width(Dimens.md))
        }
        Column(Modifier.weight(1f)) {
            AppText(title, AppTheme.type.bodyStrong, maxLines = 2)
            if (subtitle != null) {
                AppText(subtitle, AppTheme.type.bodySmall, color = colors.textSecondary, maxLines = 3)
            }
        }
        Spacer(Modifier.width(Dimens.md))
        com.dopavin.ui.glass.GlassSwitch(checked = checked)
    }
}

/** Themed Material slider with a spoken label and value. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    description: String,
    valueText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.surfaceHigh,
            disabledThumbColor = colors.controlOutline,
            disabledActiveTrackColor = colors.controlOutline,
            disabledInactiveTrackColor = colors.surfaceHigh,
        ),
        // No drag handle: just a track that fills up to the value.
        thumb = {},
        track = { state ->
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
            val fraction = ((state.value - valueRange.start) / span).coerceIn(0f, 1f)
            val active = if (enabled) colors.accent else colors.controlOutline
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceHigh),
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(active),
                )
            }
        },
        modifier = modifier.semantics {
            contentDescription = description
            stateDescription = valueText
        },
    )
}
