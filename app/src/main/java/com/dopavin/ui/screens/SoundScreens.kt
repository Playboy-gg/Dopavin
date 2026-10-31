package com.dopavin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.playback.EffectsState
import com.dopavin.playback.SleepMode
import com.dopavin.playback.SleepState
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppSlider
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.BottomSheet
import com.dopavin.ui.components.ScreenHeader
import com.dopavin.ui.components.SectionHeader
import com.dopavin.ui.components.SelectChip
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.ToggleRow
import com.dopavin.ui.components.formatTime
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import java.util.Locale

private fun dbText(mb: Int): String {
    val db = mb / 100f
    return String.format(Locale.ROOT, "%+.1f dB", db)
}

private fun freqText(hz: Int): String =
    if (hz >= 1000) String.format(Locale.ROOT, "%.1f kHz", hz / 1000f).replace(".0 ", " ") else "$hz Hz"

/** The equaliser, bass boost and loudness controls. Scrolls inside whatever hosts it. */
@Composable
fun EqualizerPanel(
    effects: EffectsState,
    actions: MusicActions,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth()) {
        ToggleRow(
            title = stringResource(R.string.eq_title),
            subtitle = stringResource(if (effects.available) R.string.eq_subtitle else R.string.eq_unavailable),
            checked = effects.enabled,
            onCheckedChange = actions::setEqEnabled,
            icon = R.drawable.ic_equalizer,
        )
        if (!effects.available) return@Column

        if (effects.presets.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                modifier = Modifier.padding(vertical = Dimens.sm),
            ) {
                itemsIndexed(effects.presets) { index, name ->
                    SelectChip(
                        text = name,
                        selected = effects.preset == index,
                        onClick = { actions.setEqPreset(index) },
                    )
                }
            }
        }

        effects.levelsMb.forEachIndexed { band, level ->
            val freq = effects.bandFreqsHz.getOrNull(band) ?: 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenEdge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = freqText(freq),
                    style = AppTheme.type.numeric,
                    color = colors.textSecondary,
                    modifier = Modifier.width(64.dp),
                )
                AppSlider(
                    value = level.toFloat(),
                    onValueChange = { actions.setEqBand(band, it.toInt()) },
                    valueRange = effects.minLevelMb.toFloat()..effects.maxLevelMb.toFloat(),
                    description = stringResource(R.string.eq_band_description, freqText(freq)),
                    valueText = dbText(level),
                    enabled = effects.enabled,
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    text = dbText(level),
                    style = AppTheme.type.numeric,
                    color = colors.textSecondary,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }

        SectionHeader(
            title = stringResource(R.string.sound_boosts),
            modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.xs),
        )
        if (effects.bassSupported) {
            SliderRow(
                label = stringResource(R.string.sound_bass),
                value = effects.bassStrength.toFloat(),
                range = 0f..1000f,
                valueText = "${effects.bassStrength / 10}%",
                onChange = { actions.setBass(it.toInt()) },
            )
        }
        SliderRow(
            label = stringResource(R.string.sound_loudness),
            value = effects.loudnessMb.toFloat(),
            range = 0f..1000f,
            valueText = dbText(effects.loudnessMb),
            onChange = { actions.setLoudness(it.toInt()) },
        )
        Row(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg)) {
            TonalButton(
                text = stringResource(R.string.sound_reset),
                onClick = actions::resetSound,
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            style = AppTheme.type.label,
            modifier = Modifier.width(88.dp),
        )
        AppSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            description = label,
            valueText = valueText,
            modifier = Modifier.weight(1f),
        )
        AppText(
            text = valueText,
            style = AppTheme.type.numeric,
            color = colors.textSecondary,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Full-screen equaliser, reached from Settings. */
@Composable
fun EqualizerScreen(effects: EffectsState, actions: MusicActions) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(onBack = actions::back, title = stringResource(R.string.eq_title))
            SpeedPicker(effects.speed, actions)
            EqualizerPanel(effects, actions)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** Playback speed presets. Pitch follows speed (a simple, predictable behaviour). */
@Composable
    fun SpeedPicker(speed: Float, actions: MusicActions) {
    Column(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm)) {
        AppText(stringResource(R.string.speed_title), AppTheme.type.bodyStrong)
        Spacer(Modifier.height(Dimens.sm))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                SelectChip(
                    text = stringResource(R.string.speed_value, value.toString().removeSuffix(".0")),
                    selected = kotlin.math.abs(speed - value) < 0.01f,
                    onClick = { actions.setSpeed(value) },
                )
            }
        }
    }
}
