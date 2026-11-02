package com.dopavin.ui.screens
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.AppSettings
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppSlider
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.SelectChip
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.ToggleRow
import com.dopavin.ui.glass.LocalGlassBackdrop
import com.dopavin.ui.glass.backdrop.backdrops.layerBackdrop
import com.dopavin.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import com.dopavin.ui.glass.glassSurface
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
private fun AppSettings.withPreset(index: Int): AppSettings = when (index) {
    0 -> copy(
        glassBlur = 24, glassLensHeight = 6, glassRefraction = 25, glassTint = 30,
        glassSaturation = 120, glassHighlight = 40, glassShadow = 30, glassQuality = 60, glassDispersion = false,
    )
    2 -> copy(
        glassBlur = 8, glassLensHeight = 20, glassRefraction = 80, glassTint = 8,
        glassSaturation = 170, glassHighlight = 90, glassShadow = 50, glassQuality = 80, glassDispersion = false,
    )
    else -> copy(
        glassBlur = 16, glassLensHeight = 12, glassRefraction = 50, glassTint = 18,
        glassSaturation = 150, glassHighlight = 60, glassShadow = 40, glassQuality = 60, glassDispersion = false,
    )
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassSettingsPage(
    settings: AppSettings,
    actions: MusicActions,
    match: (String) -> Boolean,
) {
    val enableLabel = stringResource(R.string.settings_glass_enable)
    if (match(enableLabel)) {
        ToggleRow(
            title = enableLabel,
            checked = settings.glassEnabled,
            onCheckedChange = { v -> actions.updateGlass { it.copy(glassEnabled = v) } },
        )
    }
    if (!match("")) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Note(stringResource(R.string.settings_glass_unsupported))
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Note(stringResource(R.string.settings_glass_no_lens))
    }
    GlassPreview()
    AppText(
        stringResource(R.string.settings_glass_presets),
        AppTheme.type.bodyStrong,
        modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
    )
    FlowRow(
        Modifier.padding(horizontal = Dimens.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        SelectChip(stringResource(R.string.glass_preset_subtle), false, onClick = { actions.updateGlass { it.withPreset(0) } })
        SelectChip(stringResource(R.string.glass_preset_balanced), false, onClick = { actions.updateGlass { it.withPreset(1) } })
        SelectChip(stringResource(R.string.glass_preset_crystal), false, onClick = { actions.updateGlass { it.withPreset(2) } })
    }
    GlassSlider(R.string.settings_glass_blur, settings.glassBlur, 0..40, "dp") { v -> actions.updateGlass { it.copy(glassBlur = v) } }
    GlassSlider(R.string.settings_glass_lens, settings.glassLensHeight, 0..32, "dp") { v -> actions.updateGlass { it.copy(glassLensHeight = v) } }
    GlassSlider(R.string.settings_glass_refraction, settings.glassRefraction, 0..100, "%") { v -> actions.updateGlass { it.copy(glassRefraction = v) } }
    GlassSlider(R.string.settings_glass_tint, settings.glassTint, 0..60, "%") { v -> actions.updateGlass { it.copy(glassTint = v) } }
    GlassSlider(R.string.settings_glass_saturation, settings.glassSaturation, 100..200, "%") { v -> actions.updateGlass { it.copy(glassSaturation = v) } }
    GlassSlider(R.string.settings_glass_highlight, settings.glassHighlight, 0..100, "%") { v -> actions.updateGlass { it.copy(glassHighlight = v) } }
    GlassSlider(R.string.settings_glass_shadow, settings.glassShadow, 0..100, "%") { v -> actions.updateGlass { it.copy(glassShadow = v) } }
    GlassSlider(R.string.settings_glass_quality, settings.glassQuality, 40..100, "%") { v -> actions.updateGlass { it.copy(glassQuality = v) } }
    ToggleRow(
        title = stringResource(R.string.settings_glass_dispersion),
        checked = settings.glassDispersion,
        onCheckedChange = { v -> actions.updateGlass { it.copy(glassDispersion = v) } },
    )
    Box(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.md)) {
        TonalButton(
            text = stringResource(R.string.settings_glass_reset),
            onClick = { actions.updateGlass { it.withPreset(1).copy(glassEnabled = true) } },
        )
    }
    Note(stringResource(R.string.settings_glass_credit))
}
@Composable
private fun GlassSlider(label: Int, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    val text = stringResource(label)
    Column2(text, "$value $unit") {
        AppSlider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            description = text,
            valueText = "$value $unit",
        )
    }
}
@Composable
private fun Column2(title: String, valueText: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText(title, AppTheme.type.body, modifier = Modifier.weight(1f))
            AppText(valueText, AppTheme.type.bodySmall, color = AppTheme.colors.textSecondary)
        }
        content()
    }
}
@Composable
private fun GlassPreview() {
    val colors = AppTheme.colors
    val backdrop = rememberLayerBackdrop()
    val shape = RoundedCornerShape(Dimens.radiusLarge)
    Box(
        Modifier
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.md)
            .fillMaxWidth()
            .height(140.dp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.linearGradient(
                        listOf(colors.accentStart, colors.accentEnd, Color(0xFF3D5AFE), Color(0xFF00BFA5)),
                    ),
                ),
        )
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(64.dp)
                    .glassSurface(CircleShape, fallback = colors.surfaceRaised, floating = true),
                contentAlignment = Alignment.Center,
            ) {
                AppText(stringResource(R.string.settings_glass_preview), AppTheme.type.label)
            }
        }
    }
}
@Composable
private fun Note(text: String) {
    AppText(
        text,
        AppTheme.type.bodySmall,
        color = AppTheme.colors.textSecondary,
        maxLines = 6,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
    )
}
