package com.dopavin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.AccentTint
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.DarkColors
import com.dopavin.ui.theme.Dimens

private val NAMES = intArrayOf(
    R.string.tint_rose, R.string.tint_orange, R.string.tint_amber, R.string.tint_green,
    R.string.tint_teal, R.string.tint_blue, R.string.tint_indigo, R.string.tint_purple,
)

/** Accent tint choice: the app's own colours, eight presets, and "Dynamic" (follows the playing song). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TintPicker(selected: Int, onSelect: (Int) -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.padding(vertical = Dimens.sm)) {
        AppText(
            stringResource(R.string.settings_tint),
            AppTheme.type.bodyStrong,
            modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
        )
        AppText(
            stringResource(R.string.settings_tint_body),
            AppTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = Dimens.screenEdge).padding(bottom = Dimens.sm),
        )
        FlowRow(
            Modifier.padding(horizontal = Dimens.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            Swatch(
                fill = null,
                label = stringResource(R.string.tint_default),
                selected = selected == AccentTint.DEFAULT,
                onClick = { onSelect(AccentTint.DEFAULT) },
            )
            AccentTint.PRESETS.forEachIndexed { index, base ->
                Swatch(
                    fill = Color(base),
                    label = stringResource(NAMES[index]),
                    selected = selected == index + 1,
                    onClick = { onSelect(index + 1) },
                )
            }
        }
        Box(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.md)) {
            SelectChip(
                text = stringResource(R.string.tint_dynamic),
                selected = selected == AccentTint.DYNAMIC,
                onClick = { onSelect(AccentTint.DYNAMIC) },
            )
        }
    }
}

@Composable
private fun Swatch(fill: Color?, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    // The app's own colours are shown as their gradient, so "default" is recognisable.
    val brush: Brush = fill?.let { SolidColor(it) }
        ?: Brush.linearGradient(listOf(DarkColors.accentStart, DarkColors.accentEnd))
    Box(
        Modifier
            .size(Dimens.touchTarget)
            .clickableScale(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.textPrimary else colors.controlOutline),
                CircleShape,
            )
            .padding(5.dp)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
