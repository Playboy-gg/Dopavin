package com.dopavin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.components.GlassSearchField
import com.dopavin.data.AppSettings
import com.dopavin.data.AudioQuality
import com.dopavin.data.ThemeMode
import com.dopavin.playback.EffectsState
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.FloatingBackButton
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.SelectChip
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.ToggleRow
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/** The settings categories (F-114). Each opens its own page from the hub; search flattens them. */
private enum class SettingsCategory(val icon: Int, val title: Int, val summary: Int) {
    Playback(R.drawable.ic_headphones, R.string.cat_playback, R.string.settings_sum_playback),
    Sound(R.drawable.ic_audio_lines, R.string.cat_audio, R.string.settings_sum_sound),
    Downloads(R.drawable.ic_download, R.string.cat_downloads, R.string.settings_sum_downloads),
    Streaming(R.drawable.ic_cloud, R.string.cat_streaming, R.string.settings_sum_streaming),
    Appearance(R.drawable.ic_palette, R.string.cat_appearance, R.string.settings_sum_appearance),
    Glass(R.drawable.ic_glass, R.string.cat_glass, R.string.settings_sum_glass),
    Privacy(R.drawable.ic_shield_check, R.string.cat_privacy, R.string.settings_sum_privacy),
    Updates(R.drawable.ic_download, R.string.cat_updates, R.string.settings_sum_updates),
    About(R.drawable.ic_info, R.string.cat_about, R.string.settings_sum_about),
}

/**
 * S-08 (F-114 redesign). A hub of seven category cards; tapping one opens that category as its own
 * page (back returns to the hub). The search field flattens every category into one filtered list.
 * Destructive actions (delete all downloads, restore defaults, clear personalisation) are confirmed.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    effects: EffectsState,
    actions: MusicActions,
    /** Set when this is a category page pushed from the hub (Route.SettingsPage); null for the hub itself. */
    categoryName: String? = null,
) {
    val colors = AppTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val category = categoryName?.let { name -> SettingsCategory.values().firstOrNull { it.name == name } }
    val searching = query.isNotBlank()

    // Back (button, device, predictive) is the app's normal page back: a category is a real route above the hub.
    FloatingBackButton(actions::back)
    // Title and search scroll away with the page; only the glass back button stays put.
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Dimens.touchTarget + Dimens.sm))
        AppText(
            text = stringResource(category?.title ?: R.string.settings_title),
            style = AppTheme.type.display,
            maxLines = 2,
            modifier = Modifier
                .padding(horizontal = Dimens.screenEdge)
                .padding(top = Dimens.sm, bottom = Dimens.md)
                .semantics { heading() },
        )
        if (category == null) {
            GlassSearchField(
                query = query,
                onQueryChange = { query = it },
                hint = stringResource(R.string.settings_search_hint),
                modifier = Modifier.padding(start = Dimens.screenEdge, end = Dimens.screenEdge, bottom = Dimens.sm),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            when {
                category != null -> CategoryPage(category, settings, effects, actions, match = { true }, onNavigate = { actions.openSettingsCategory(it.name) })
                searching -> {
                    val q = query.trim()
                    var shown = 0
                    SettingsCategory.values().forEach { cat ->
                        val hits = rowsMatching(cat, q)
                        if (hits) {
                            shown++
                            SectionLabel(stringResource(cat.title))
                            CategoryPage(
                                cat, settings, effects, actions,
                                match = { label -> label.contains(q, ignoreCase = true) },
                                onNavigate = { actions.openSettingsCategory(it.name) },
                            )
                        }
                    }
                    if (shown == 0) {
                        Column(
                            Modifier.fillMaxWidth().padding(Dimens.screenEdge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppText(
                                stringResource(R.string.settings_search_none),
                                AppTheme.type.body,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(Dimens.md))
                            com.dopavin.ui.components.TonalButton(
                                text = stringResource(R.string.settings_search_clear),
                                onClick = { query = "" },
                            )
                        }
                    }
                }
                else -> Hub(onOpen = { actions.openSettingsCategory(it.name) })
            }
            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(Dimens.xl),
            )
        }
    }
}

// ---- Hub ----

@Composable
private fun Hub(onOpen: (SettingsCategory) -> Unit) {
    val colors = AppTheme.colors
    val newVersion = com.dopavin.ui.components.LocalUpdate.current.status == com.dopavin.data.UpdateStatus.Available
    val groups = listOf(
        R.string.settings_grp_listening to listOf(
            SettingsCategory.Playback, SettingsCategory.Sound, SettingsCategory.Downloads, SettingsCategory.Streaming,
        ),
        R.string.settings_grp_experience to listOf(SettingsCategory.Appearance, SettingsCategory.Glass),
        R.string.settings_grp_app to listOf(SettingsCategory.Privacy, SettingsCategory.Updates, SettingsCategory.About),
    )
    Column(Modifier.padding(bottom = Dimens.xs)) {
        groups.forEach { (label, cats) ->
            SectionLabel(stringResource(label))
            cats.forEach { cat ->
                val title = stringResource(cat.title)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickableScale(pressedScale = 0.985f) { onOpen(cat) }
                        .padding(horizontal = Dimens.screenEdge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(cat.icon),
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(Dimens.iconControl),
                    )
                    Spacer(Modifier.width(Dimens.lg))
                    AppText(title, AppTheme.type.bodyStrong, maxLines = 2, modifier = Modifier.weight(1f))
                    if (cat == SettingsCategory.Updates && newVersion) {
                        // No pop-up: the hub itself says a new version is waiting.
                        AppText(
                            stringResource(R.string.update_badge),
                            AppTheme.type.caption,
                            color = colors.onAccent,
                            modifier = Modifier
                                .padding(end = Dimens.sm)
                                .clip(RoundedCornerShape(50))
                                .background(colors.accent)
                                .padding(horizontal = Dimens.sm, vertical = 2.dp),
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(Dimens.iconControl),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    AppText(
        text,
        AppTheme.type.caption,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier
            .padding(start = Dimens.screenEdge, top = Dimens.xl, bottom = Dimens.xs)
            .semantics { heading() },
    )
}

/** True when any row label of [cat] contains [q]; used to hide empty categories while searching. */
@Composable
private fun rowsMatching(cat: SettingsCategory, q: String): Boolean {
    val labels = rowLabels(cat)
    return labels.any { it.contains(q, ignoreCase = true) }
}

@Composable
private fun rowLabels(cat: SettingsCategory): List<String> = when (cat) {
    SettingsCategory.Playback -> listOf(
        R.string.settings_skip_silence, R.string.settings_pause_unplug, R.string.settings_keep_screen_on,
        R.string.settings_keep_pitch, R.string.settings_sleep_fade, R.string.settings_autoplay,
        R.string.settings_resume_connect, R.string.settings_autoplay_fresh,
    )
    SettingsCategory.Sound -> listOf(R.string.eq_title)
    SettingsCategory.Downloads -> listOf(
        R.string.settings_download_quality, R.string.settings_download_wifi,
        R.string.settings_auto_download_liked, R.string.settings_download_limit, R.string.storage_title,
        R.string.settings_delete_downloads,
    )
    SettingsCategory.Streaming -> listOf(
        R.string.settings_online_search, R.string.settings_stream_quality, R.string.settings_lyrics_online,
        R.string.lyrics_provider_title, R.string.settings_stream_quality_mobile, R.string.settings_lyric_strip,
    )
    SettingsCategory.Appearance -> listOf(
        R.string.settings_group_appearance, R.string.settings_pure_black, R.string.settings_ambience, R.string.theme_system,
        R.string.theme_dark, R.string.theme_light, R.string.settings_tint, R.string.tint_dynamic, R.string.settings_haptics,
    )
    SettingsCategory.Glass -> listOf(
        R.string.settings_glass_enable, R.string.settings_glass_blur, R.string.settings_glass_lens,
        R.string.settings_glass_refraction, R.string.settings_glass_tint, R.string.settings_glass_saturation,
        R.string.settings_glass_highlight, R.string.settings_glass_shadow, R.string.settings_glass_quality,
        R.string.settings_glass_dispersion,
    )
    SettingsCategory.Privacy -> listOf(
        R.string.settings_reset_personalization, R.string.settings_backup_export, R.string.settings_backup_import,
    )
    SettingsCategory.About -> listOf(
        R.string.settings_restore, R.string.about_licences, R.string.about_view_source,
    )
    SettingsCategory.Updates -> listOf(
        R.string.update_check_now, R.string.update_auto, R.string.update_download, R.string.update_install,
        R.string.update_whats_new,
    )
}.map { stringResource(it) }

// ---- Category pages ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPage(
    category: SettingsCategory,
    settings: AppSettings,
    effects: EffectsState,
    actions: MusicActions,
    match: (String) -> Boolean,
    onNavigate: (SettingsCategory) -> Unit,
) {
    val colors = AppTheme.colors
    when (category) {
        SettingsCategory.Playback -> {
            Toggle(R.string.settings_skip_silence, R.string.settings_skip_silence_body, settings.skipSilence, actions::setSkipSilence, match)
            Toggle(R.string.settings_pause_unplug, R.string.settings_pause_unplug_body, settings.pauseOnUnplug, actions::setPauseOnUnplug, match)
            Toggle(R.string.settings_keep_screen_on, R.string.settings_keep_screen_on_body, settings.keepScreenOn, actions::setKeepScreenOn, match)
            Toggle(R.string.settings_keep_pitch, R.string.settings_keep_pitch_body, settings.keepPitch, actions::setKeepPitch, match)
            Toggle(R.string.settings_sleep_fade, R.string.settings_sleep_fade_body, settings.sleepFade, actions::setSleepFade, match)
            Toggle(R.string.settings_autoplay, R.string.settings_autoplay_body, settings.autoplay, actions::setAutoplay, match)
            Toggle(R.string.settings_autoplay_fresh, R.string.settings_autoplay_fresh_body, settings.autoplayFresh, actions::setAutoplayFresh, match)
            Toggle(R.string.settings_resume_connect, R.string.settings_resume_connect_body, settings.resumeOnConnect, actions::setResumeOnConnect, match)
        }
        SettingsCategory.Sound -> {
            val label = stringResource(R.string.eq_title)
            if (match(label)) {
                SettingsRow(
                    title = label,
                    subtitle = stringResource(if (effects.enabled) R.string.settings_eq_on else R.string.settings_eq_off),
                    onClick = actions::openEqualizer,
                )
            }
        }
        SettingsCategory.Downloads -> {
            QualityPicker(
                title = R.string.settings_download_quality,
                body = R.string.settings_download_quality_body,
                selected = settings.downloadQuality,
                onSelect = actions::setDownloadQuality,
                match = match,
            )
            Toggle(R.string.settings_download_wifi, R.string.settings_download_wifi_body, settings.downloadWifiOnly, actions::setDownloadWifiOnly, match)
            Toggle(R.string.settings_auto_download_liked, R.string.settings_auto_download_liked_body, settings.autoDownloadLiked, actions::setAutoDownloadLiked, match)
            val limitLabel = stringResource(R.string.settings_download_limit)
            if (match(limitLabel)) {
                AppText(
                    limitLabel,
                    AppTheme.type.bodyStrong,
                    modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
                )
                FlowRow(
                    Modifier.padding(horizontal = Dimens.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                ) {
                    listOf(0, 500, 1000, 2000, 5000).forEach { mb ->
                        val text = when (mb) {
                            0 -> stringResource(R.string.limit_none)
                            1000, 2000, 5000 -> stringResource(R.string.limit_gb, mb / 1000)
                            else -> stringResource(R.string.limit_mb, mb)
                        }
                        SelectChip(text, settings.downloadLimitMb == mb, onClick = { actions.setDownloadLimit(mb) })
                    }
                }
            }
            val storageLabel = stringResource(R.string.storage_title)
            if (match(storageLabel)) {
                SettingsRow(
                    title = storageLabel,
                    onClick = actions::openStorage,
                )
            }
        }
        SettingsCategory.Streaming -> {
            Toggle(R.string.settings_online_search, R.string.settings_online_search_body, settings.onlinePlayback, actions::setOnlinePlayback, match)
            QualityPicker(
                title = R.string.settings_stream_quality,
                body = R.string.settings_stream_quality_body,
                selected = settings.streamQuality,
                onSelect = actions::setStreamQuality,
                match = match,
            )
            QualityPicker(
                title = R.string.settings_stream_quality_mobile,
                body = R.string.settings_stream_quality_mobile_body,
                selected = settings.streamQualityMobile,
                onSelect = actions::setStreamQualityMobile,
                match = match,
            )
            Toggle(R.string.settings_lyrics_online, R.string.settings_lyrics_online_body, settings.lyricsOnline, actions::setLyricsOnline, match)
            if (match(stringResource(R.string.lyrics_provider_title))) {
                LyricsProviderPicker(settings.lyricsProvider, actions::setLyricsProvider)
            }
            Toggle(R.string.settings_lyric_strip, R.string.settings_lyric_strip_body, settings.lyricStrip, actions::setLyricStrip, match)
        }
        SettingsCategory.Appearance -> {
            val themeSystem = stringResource(R.string.theme_system)
            val themeDark = stringResource(R.string.theme_dark)
            val themeLight = stringResource(R.string.theme_light)
            val title = stringResource(R.string.settings_group_appearance)
            if (match(title) || match(themeSystem) || match(themeDark) || match(themeLight)) {
                AppText(
                    stringResource(R.string.settings_theme),
                    AppTheme.type.bodyStrong,
                    modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
                )
                FlowRow(
                    Modifier.padding(horizontal = Dimens.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                ) {
                    SelectChip(themeSystem, settings.theme == ThemeMode.System, onClick = { actions.setTheme(ThemeMode.System) })
                    SelectChip(themeDark, settings.theme == ThemeMode.Dark, onClick = { actions.setTheme(ThemeMode.Dark) })
                    SelectChip(themeLight, settings.theme == ThemeMode.Light, onClick = { actions.setTheme(ThemeMode.Light) })
                }
            }
            val tintTitle = stringResource(R.string.settings_tint)
            if (match(tintTitle) || match(stringResource(R.string.tint_dynamic))) {
                com.dopavin.ui.components.TintPicker(
                    selected = settings.accentTint,
                    onSelect = actions::setAccentTint,
                )
            }
            Toggle(R.string.settings_pure_black, R.string.settings_pure_black_body, settings.pureBlack, actions::setPureBlack, match)
            Toggle(R.string.settings_ambience, R.string.settings_ambience_body, settings.ambience, actions::setAmbience, match)
            Toggle(R.string.settings_haptics, R.string.settings_haptics_body, settings.haptics, actions::setHaptics, match)
        }
        SettingsCategory.Glass -> GlassSettingsPage(settings, actions, match)
        SettingsCategory.Privacy -> {
            var confirm by remember { mutableStateOf(false) }
            val exportLabel = stringResource(R.string.settings_backup_export)
            if (match(exportLabel)) {
                SettingsRow(
                    title = exportLabel,
                    onClick = { actions.requestBackup(true) },
                )
            }
            val importLabel = stringResource(R.string.settings_backup_import)
            if (match(importLabel)) {
                SettingsRow(
                    title = importLabel,
                    onClick = { actions.requestBackup(false) },
                )
            }
            val resetLabel = stringResource(R.string.settings_reset_personalization)
            if (match(resetLabel)) {
                SettingsRow(
                    title = resetLabel,
                    onClick = { confirm = true },
                    chevron = false,
                )
            }
            if (confirm) {
                ConfirmDialog(
                    title = resetLabel,
                    body = stringResource(R.string.settings_reset_personalization_body),
                    onConfirm = {
                        confirm = false
                        actions.clearPersonalization()
                    },
                    onDismiss = { confirm = false },
                )
            }
        }
        SettingsCategory.Updates -> UpdatesPage(actions, settings.autoUpdateCheck, match)
        SettingsCategory.About -> {
            AboutPage(actions, onOpenPrivacy = { onNavigate(SettingsCategory.Privacy) })
            // Debug builds keep every crash they had; release builds never show this row.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (com.dopavin.CrashReporter.isDebuggable(ctx)) {
                SettingsRow(
                    title = stringResource(R.string.crash_log_row),
                    onClick = actions::openCrashLog,
                )
            }
        }
    }
    // Delete-all lives with the downloads, in its own destructive row.
    if (category == SettingsCategory.Downloads) {
        var confirm by remember { mutableStateOf(false) }
        val label = stringResource(R.string.settings_delete_downloads)
        if (match(label)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clickableScale(pressedScale = 0.99f) { confirm = true }
                    .padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AppText(label, AppTheme.type.bodyStrong, color = colors.error)
                    AppText(
                        stringResource(R.string.settings_delete_downloads_body),
                        AppTheme.type.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 3,
                    )
                }
            }
        }
        if (confirm) {
            ConfirmDialog(
                title = label,
                body = stringResource(R.string.settings_delete_downloads_body),
                onConfirm = {
                    confirm = false
                    actions.deleteAllDownloads()
                },
                onDismiss = { confirm = false },
            )
        }
    }
}

@Composable
private fun Toggle(
    title: Int,
    body: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    match: (String) -> Boolean,
) {
    val label = stringResource(title)
    if (match(label)) {
        ToggleRow(
            title = label,
            checked = checked,
            onCheckedChange = onChange,
        )
    }
}

/** Three-way audio quality choice as chips, with a one-line explanation of the chosen level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityPicker(
    title: Int,
    body: Int,
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
    match: (String) -> Boolean,
) {
    val label = stringResource(title)
    if (!match(label)) return
    Column(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm)) {
        AppText(label, AppTheme.type.bodyStrong)
        Spacer(Modifier.height(Dimens.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            AudioQuality.values().forEach { quality ->
                SelectChip(
                    text = stringResource(qualityLabel(quality)),
                    selected = quality == selected,
                    onClick = { onSelect(quality) },
                )
            }
        }
    }
}

private fun qualityLabel(q: AudioQuality): Int = when (q) {
    AudioQuality.High -> R.string.quality_high
    AudioQuality.Standard -> R.string.quality_standard
    AudioQuality.DataSaver -> R.string.quality_saver
}

private fun qualityHint(q: AudioQuality): Int = when (q) {
    AudioQuality.High -> R.string.quality_high_hint
    AudioQuality.Standard -> R.string.quality_standard_hint
    AudioQuality.DataSaver -> R.string.quality_saver_hint
}

@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.sel_confirm_delete),
) {
    com.dopavin.ui.components.GlassDialog(
        title = title,
        body = body,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        destructive = true,
        onDismiss = onDismiss,
    )
}

/**
 * Lyrics provider choice: "Choose" races every provider; the others use only that provider. Only
 * providers that really exist are listed, each with what it can deliver. The accent marks the
 * selected one.
 */
@Composable
private fun LyricsProviderPicker(selected: String, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    SectionLabel(stringResource(R.string.lyrics_provider_title))
    val options = listOf(com.dopavin.data.LyricsProviders.CHOOSE) + com.dopavin.data.LyricsProviders.all
    val kindLabels = mapOf(
        com.dopavin.data.LyricsKind.WordSynced to stringResource(R.string.lyrics_kind_word),
        com.dopavin.data.LyricsKind.LineSynced to stringResource(R.string.lyrics_kind_line),
        com.dopavin.data.LyricsKind.Plain to stringResource(R.string.lyrics_kind_plain),
    )
    options.forEach { id ->
        val isChoose = id == com.dopavin.data.LyricsProviders.CHOOSE
        val caps = if (isChoose) {
            com.dopavin.data.LyricsProviders.all.flatMap { com.dopavin.data.LyricsProviders.capabilities(it) }.toSet()
        } else {
            com.dopavin.data.LyricsProviders.capabilities(id)
        }
        val kinds = caps.sortedBy { it.ordinal }.map { kindLabels.getValue(it) }.joinToString(" · ")
        val isSelected = selected == id
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickableScale(pressedScale = 0.99f, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(id) }
                .semantics { this.selected = isSelected }
                .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AppText(
                    stringResource(
                        when (id) {
                            com.dopavin.data.LyricsProviders.BETTER -> R.string.lyrics_provider_better
                            com.dopavin.data.LyricsProviders.LRCLIB -> R.string.lyrics_provider_lrclib
                            com.dopavin.data.LyricsProviders.PORTATO -> R.string.lyrics_provider_portato
                            com.dopavin.data.LyricsProviders.LYRICSPLUS -> R.string.lyrics_provider_lyricsplus
                            com.dopavin.data.LyricsProviders.UNISON -> R.string.lyrics_provider_unison
                            com.dopavin.data.LyricsProviders.KUGOU -> R.string.lyrics_provider_kugou
                            else -> R.string.lyrics_provider_choose
                        },
                    ),
                    AppTheme.type.bodyStrong,
                    maxLines = 2,
                )
                AppText(
                    stringResource(
                        when (id) {
                            com.dopavin.data.LyricsProviders.BETTER -> R.string.lyrics_provider_better_desc
                            com.dopavin.data.LyricsProviders.LRCLIB -> R.string.lyrics_provider_lrclib_desc
                            com.dopavin.data.LyricsProviders.PORTATO -> R.string.lyrics_provider_portato_desc
                            com.dopavin.data.LyricsProviders.LYRICSPLUS -> R.string.lyrics_provider_lyricsplus_desc
                            com.dopavin.data.LyricsProviders.UNISON -> R.string.lyrics_provider_unison_desc
                            com.dopavin.data.LyricsProviders.KUGOU -> R.string.lyrics_provider_kugou_desc
                            else -> R.string.lyrics_provider_choose_desc
                        },
                    ),
                    AppTheme.type.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 3,
                )
                AppText(kinds, AppTheme.type.caption, color = colors.textSecondary, maxLines = 2)
            }
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(Dimens.iconControl),
                )
            }
        }
    }
}

/** Long informational text under a header. */
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
