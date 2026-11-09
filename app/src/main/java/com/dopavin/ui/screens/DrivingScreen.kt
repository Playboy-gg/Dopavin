package com.dopavin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dopavin.data.LyricsStatus
import com.dopavin.data.LyricsUi
import com.dopavin.data.Song
import com.dopavin.data.currentLineIndex
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.ui.MusicActions
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.PlayPauseButton
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * Driving mode: only what you need with one glance and one big tap. Large title, three huge
 * transport buttons, screen stays on. Opened from Now Playing > More controls.
 */
@Composable
fun DrivingScreen(
    playback: PlaybackUi,
    song: Song?,
    lyrics: LyricsUi,
    lyricsOnline: Boolean,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    val nothing = stringResource(R.string.driving_nothing)
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconAction(
                icon = R.drawable.ic_close,
                description = stringResource(R.string.driving_exit),
                onClick = actions::back,
                glass = true,
                size = 56.dp,
            )
        }
        Spacer(Modifier.height(Dimens.lg))
        AppText(
            text = playback.title.ifBlank { nothing },
            style = AppTheme.type.title,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.sm))
        AppText(
            text = playback.artist,
            style = AppTheme.type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // The space between title and controls shows the lyrics line by line (empty when there are none).
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (song != null) DrivingLyrics(song, playback, lyrics, lyricsOnline, actions)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = R.drawable.ic_skip_previous,
                description = stringResource(R.string.cd_previous),
                onClick = actions::previous,
                size = 96.dp,
                iconSize = 48.dp,
                glass = true,
            )
            PlayPauseButton(
                playing = playback.isPlaying,
                buffering = playback.isBuffering,
                onClick = actions::togglePlayPause,
                size = 140.dp,
            )
            IconAction(
                icon = R.drawable.ic_skip_next,
                description = stringResource(R.string.cd_next),
                onClick = actions::next,
                enabled = playback.hasNext,
                size = 96.dp,
                iconSize = 48.dp,
                glass = true,
            )
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}

/**
 * Three lyric lines for a glance: the one before, the one being sung (karaoke highlight) and the
 * next. Only synced lyrics are shown; plain lyrics, no lyrics and loading leave the space empty.
 */
@Composable
private fun DrivingLyrics(
    song: Song,
    playback: PlaybackUi,
    lyrics: LyricsUi,
    lyricsOnline: Boolean,
    actions: MusicActions,
) {
    // Now Playing may have loaded these already; only fetch when the shown lyrics are for another song.
    LaunchedEffect(song.id, lyricsOnline) {
        if (lyrics.songId != song.id) actions.loadLyrics(song)
    }
    if (lyrics.songId != song.id || lyrics.status != LyricsStatus.Found || lyrics.synced.isEmpty()) return
    val colors = AppTheme.colors
    val lines = lyrics.synced
    val position = com.dopavin.ui.components.rememberFramePosition(playback.mediaId, playback.isPlaying, actions::position)
    val offset = lyrics.offsetMs
    val index by remember(lines) {
        derivedStateOf { currentLineIndex(lines, position.longValue + offset + DRIVING_LEAD_MS) }
    }
    val dim = colors.textSecondary.copy(alpha = 0.45f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.lg)) {
        lines.getOrNull(index - 1)?.let {
            androidx.compose.material3.Text(
                it.text.ifBlank { "♪" }, color = dim, textAlign = TextAlign.Center, maxLines = 2,
                style = AppTheme.type.body.copy(fontSize = 20.sp, lineHeight = 26.sp),
            )
        }
        val current = lines.getOrNull(index)
        if (current != null) {
            val text = karaokeText(
                line = current,
                nextMs = lines.getOrNull(index + 1)?.timeMs,
                positionMs = position.longValue + offset + DRIVING_LEAD_MS,
                sung = colors.textPrimary,
                upcoming = colors.textSecondary.copy(alpha = 0.55f),
                reduceMotion = AppTheme.motion.reduce,
            )
            androidx.compose.material3.Text(
                text, color = colors.textPrimary, textAlign = TextAlign.Center, maxLines = 3,
                style = AppTheme.type.title.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            )
        }
        lines.getOrNull(index + 1)?.let {
            androidx.compose.material3.Text(
                it.text.ifBlank { "♪" }, color = dim, textAlign = TextAlign.Center, maxLines = 2,
                style = AppTheme.type.body.copy(fontSize = 20.sp, lineHeight = 26.sp),
            )
        }
    }
}

private const val DRIVING_LEAD_MS = 300L
