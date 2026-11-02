package com.dopavin.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.ListenEntry
import com.dopavin.data.RecapMath
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.FloatingBackButton
import com.dopavin.ui.components.GlassDialog
import com.dopavin.ui.components.SectionHeader
import com.dopavin.ui.components.SelectChip
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import java.util.TimeZone
@Composable
fun RecapScreen(entries: List<ListenEntry>, actions: MusicActions) {
    val colors = AppTheme.colors
    var range by rememberSaveable { mutableStateOf(7) }
    var confirmClear by remember { mutableStateOf(false) }
    val summary = remember(entries, range) {
        val now = System.currentTimeMillis()
        RecapMath.summarize(entries, range, now, TimeZone.getDefault().getOffset(now).toLong())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        com.dopavin.ui.components.ScreenHeader(
            onBack = actions::back,
            title = stringResource(R.string.recap_title),
            subtitle = stringResource(R.string.recap_subtitle),
        )
        Row(
            Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            SelectChip(stringResource(R.string.recap_week), range == 7, { range = 7 })
            SelectChip(stringResource(R.string.recap_month), range == 30, { range = 30 })
            SelectChip(stringResource(R.string.recap_all), range == 0, { range = 0 })
        }
        if (summary.plays == 0) {
            EmptyState(
                title = stringResource(R.string.recap_empty_title),
                body = stringResource(R.string.recap_empty_body),
                icon = R.drawable.ic_audio_lines,
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                Stat(summary.minutes.toString(), stringResource(R.string.recap_minutes_label), Modifier.weight(1f))
                Stat(summary.plays.toString(), stringResource(R.string.recap_plays_label), Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.screenEdge),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                Stat(summary.songs.toString(), stringResource(R.string.recap_songs_label), Modifier.weight(1f))
                Stat(summary.artists.toString(), stringResource(R.string.recap_artists_label), Modifier.weight(1f))
            }
            if (summary.dailyMinutes.isNotEmpty()) {
                AppText(
                    stringResource(if (range == 7) R.string.recap_chart_week else R.string.recap_chart_month),
                    AppTheme.type.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
                )
                val peak = (summary.dailyMinutes.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().height(96.dp).padding(horizontal = Dimens.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(if (range == 7) 8.dp else 2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    summary.dailyMinutes.forEach { m ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeightFraction(m.toFloat() / peak)
                                .background(colors.accent, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
            if (summary.topArtists.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(R.string.recap_top_artists),
                    modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.xs),
                )
                summary.topArtists.forEachIndexed { i, a ->
                    RankRow(i + 1, a.name, stringResource(R.string.recap_plays_count, a.plays), null, null)
                }
            }
            if (summary.topSongs.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(R.string.recap_top_songs),
                    modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.xs),
                )
                val songs = summary.topSongs.map { it.song }
                com.dopavin.ui.components.TonalButton(
                    text = stringResource(R.string.recap_play_top),
                    icon = R.drawable.ic_play,
                    accent = true,
                    onClick = { actions.playSongs(songs, 0) },
                    modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs),
                )
                summary.topSongs.forEachIndexed { i, s ->
                    RankRow(
                        i + 1,
                        s.song.title,
                        listOf(s.song.artist, stringResource(R.string.recap_plays_count, s.plays))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        s.song,
                    ) { actions.playSongs(songs, i) }
                }
            }
            AppText(
                stringResource(R.string.recap_note),
                AppTheme.type.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
            )
            SettingsRow(
                title = stringResource(R.string.recap_clear),
                onClick = { confirmClear = true },
                chevron = false,
            )
        }
        Spacer(Modifier.navigationBarsPadding().height(Dimens.xl))
    }
    if (confirmClear) {
        GlassDialog(
            title = stringResource(R.string.recap_clear),
            body = stringResource(R.string.recap_clear_confirm),
            confirmLabel = stringResource(R.string.recap_clear_action),
            destructive = true,
            onConfirm = {
                confirmClear = false
                actions.clearRecap()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
private fun Modifier.fillMaxHeightFraction(fraction: Float): Modifier =
    this.height((96 * fraction.coerceIn(0.04f, 1f)).dp)
@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier
            .background(colors.surfaceRaised, RoundedCornerShape(Dimens.radiusMedium))
            .padding(Dimens.lg),
    ) {
        AppText(value, AppTheme.type.headline)
        AppText(label, AppTheme.type.caption, color = colors.textSecondary)
    }
}
@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String,
    song: com.dopavin.data.Song?,
    onClick: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickableScale(pressedScale = 0.98f, onClick = onClick) else it }
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(rank.toString(), AppTheme.type.label, color = colors.textSecondary, modifier = Modifier.size(28.dp))
        if (song != null) {
            ArtworkImage(
                uri = song.artworkUri,
                sizePx = ArtworkSize.Row,
                shape = RoundedCornerShape(Dimens.radiusSmall),
                modifier = Modifier.size(Dimens.artworkRow),
                seed = song.album,
            )
            Spacer(Modifier.size(Dimens.md))
        }
        Column(Modifier.weight(1f)) {
            AppText(title, AppTheme.type.bodyStrong, maxLines = 1)
            AppText(subtitle, AppTheme.type.caption, color = colors.textSecondary, maxLines = 1)
        }
    }
}
