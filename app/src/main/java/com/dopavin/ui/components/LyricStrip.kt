package com.dopavin.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.dopavin.ui.screens.karaokeText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dopavin.data.LyricsStatus
import com.dopavin.data.LyricsUi
import com.dopavin.data.Song
import com.dopavin.data.currentLineIndex
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.theme.AppTheme

/**
 * F-203: the line being sung, one row under the artist in the full player, highlighted word by word like karaoke; a tap opens the lyrics. It asks for the lyrics itself (only
 * when the setting is on and they are not loaded for this song yet) and shows nothing for songs without
 * synced lyrics. Position is polled at 4 Hz, which is plenty for a line that changes every few seconds.
 */
@Composable
fun LyricStrip(
    song: Song?,
    playback: PlaybackUi,
    lyrics: LyricsUi,
    enabled: Boolean,
    lyricsOnline: Boolean,
    actions: MusicActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled || song == null || !lyricsOnline) return
    LaunchedEffect(song.id) {
        if (lyrics.songId != song.id) actions.loadLyrics(song)
    }
    val ready = lyrics.songId == song.id && lyrics.status == LyricsStatus.Found && lyrics.synced.isNotEmpty()
    if (!ready) return
    // 20 updates a second: smooth enough for one small line, and 3x cheaper than every frame (this strip sits
    // on the player, which also draws the artwork, glass buttons and the seek bar).
    val position = rememberPlaybackPosition(playback.mediaId, playback.isPlaying, actions::position, intervalMs = 50L)
    val lines = lyrics.synced
    val index by remember(lines, lyrics.offsetMs) {
        derivedStateOf { currentLineIndex(lines, position.longValue + lyrics.offsetMs + STRIP_LEAD_MS) }
    }
    val colors = AppTheme.colors
    val reduce = AppTheme.motion.reduce
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            // Tapping the strip opens the full lyrics.
            .clickable(interactionSource = noRipple, indication = null, onClick = onClick),
    ) {
        // Crossfade only on the line number, so a line change fades but the highlight itself never restarts.
        Crossfade(targetState = index, label = "lyricStrip") { i ->
            val line = lines.getOrNull(i)
            if (line == null || line.text.isBlank() || line.text.trim() == "♪") {
                Spacer(Modifier.height(20.dp))
            } else {
                val shown = if (i == index) {
                    karaokeText(
                        line,
                        lines.getOrNull(i + 1)?.timeMs,
                        position.longValue + lyrics.offsetMs + STRIP_LEAD_MS,
                        colors.accent,
                        colors.textSecondary.copy(alpha = 0.55f),
                        reduce,
                    )
                } else {
                    // Outgoing line while it fades: fully sung.
                    AnnotatedString(line.text.trim())
                }
                Text(
                    text = shown,
                    style = AppTheme.type.bodySmall,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Highlight slightly before the word is sung, same lead as the lyrics page. */
private const val STRIP_LEAD_MS = 300L
