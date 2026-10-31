package com.dopavin.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.LyricLine
import com.dopavin.data.LyricsStatus
import com.dopavin.data.LyricsUi
import com.dopavin.data.Song
import com.dopavin.data.currentLineIndex
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.EqualizerBars
import com.dopavin.ui.components.LoadingState
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.components.rememberArtworkTint
import com.dopavin.ui.components.rememberPlaybackPosition
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import kotlin.math.abs

/**
 * Lyrics panel (shown in place of the artwork on Now Playing). Synced lyrics put the line being sung
 * front and centre: it grows and brightens, the lines around it fall off like a spotlight, and
 * when the provider has word timing the sung words light up one by one (karaoke). Long
 * instrumental gaps show three dots that fill up until the next line. The list follows the song
 * and yields to manual scrolls; any line can be tapped to seek there. Plain lyrics read as text.
 * Loading is keyed on the song and the online-lyrics setting.
 */
@Composable
fun LyricsPanel(
    song: Song,
    playback: PlaybackUi,
    lyrics: LyricsUi,
    lyricsOnline: Boolean,
    actions: MusicActions,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(song.id, lyricsOnline) { actions.loadLyrics(song) }
    val shown = if (lyrics.songId == song.id) lyrics.status else LyricsStatus.Loading
    var sourceDialog by remember { mutableStateOf(false) }
    var syncDialog by remember { mutableStateOf(false) }
    var cardDialog by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        if (shown == LyricsStatus.Found || shown == LyricsStatus.NotFound || shown == LyricsStatus.Failed) {
            LyricsControls(
                lyrics = lyrics,
                showSync = shown == LyricsStatus.Found && lyrics.synced.isNotEmpty(),
                onSource = { sourceDialog = true },
                onSync = { syncDialog = true },
                showCard = shown == LyricsStatus.Found,
                onCard = { cardDialog = true },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (shown) {
                LyricsStatus.Idle, LyricsStatus.Loading -> LoadingState(Modifier.fillMaxSize())
                LyricsStatus.Found -> LyricsBody(song, playback, lyrics, actions)
                LyricsStatus.NotFound -> EmptyState(
                    title = stringResource(R.string.lyrics_none_title),
                    body = stringResource(R.string.lyrics_none_body_all),
                    actionLabel = stringResource(R.string.error_retry),
                    onAction = { actions.retryLyrics(song) },
                    icon = R.drawable.ic_lyrics,
                    modifier = Modifier.fillMaxSize(),
                )
                LyricsStatus.Instrumental -> EmptyState(
                    title = stringResource(R.string.lyrics_instrumental),
                    icon = R.drawable.ic_music_note,
                    modifier = Modifier.fillMaxSize(),
                )
                LyricsStatus.Failed -> {
                    val cause = buildString {
                        append(stringResource(R.string.lyrics_failed_body))
                        lyrics.error?.let { append("\n"); append(it) }
                        lyrics.errorDetail?.let { append("\n("); append(it); append(")") }
                    }
                    EmptyState(
                        title = stringResource(R.string.lyrics_failed_title),
                        body = cause,
                        actionLabel = stringResource(R.string.error_retry),
                        onAction = { actions.retryLyrics(song) },
                        icon = R.drawable.ic_lyrics,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                LyricsStatus.Disabled -> EmptyState(
                    title = stringResource(R.string.lyrics_off_title),
                    body = stringResource(R.string.lyrics_off_body_all),
                    actionLabel = stringResource(R.string.lyrics_turn_on),
                    onAction = { actions.setLyricsOnline(true) },
                    icon = R.drawable.ic_lyrics,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (sourceDialog) {
        com.dopavin.ui.components.GlassDialog(
            title = stringResource(R.string.lyrics_source_title),
            dismissLabel = stringResource(R.string.lyrics_done),
            onDismiss = { sourceDialog = false },
        ) {
            val colors = AppTheme.colors
            val ink = com.dopavin.ui.components.glassInk()
            listOf(com.dopavin.data.LyricsProviders.CHOOSE) .plus(com.dopavin.data.LyricsProviders.all).forEach { id ->
                val selected = lyrics.selection == id
                com.dopavin.ui.components.DialogPillButton(
                    text = providerName(id),
                    fill = if (selected) colors.accent else ink.copy(alpha = 0.10f),
                    textColor = if (selected) colors.onAccent else ink,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sourceDialog = false
                        actions.loadLyricsFrom(song, id)
                    },
                )
            }
        }
    }
    if (cardDialog) LyricsCardDialog(song, lyrics, onDismiss = { cardDialog = false })
    if (syncDialog) {
        com.dopavin.ui.components.GlassDialog(
            title = stringResource(R.string.lyrics_sync_title),
            body = stringResource(R.string.lyrics_sync_hint),
            dismissLabel = stringResource(R.string.lyrics_done),
            onDismiss = { syncDialog = false },
        ) {
            val ink = com.dopavin.ui.components.glassInk()
            AppText(
                text = stringResource(R.string.lyrics_sync_value, "%+.1f".format(lyrics.offsetMs / 1000f)),
                style = AppTheme.type.headline,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), modifier = Modifier.fillMaxWidth()) {
                com.dopavin.ui.components.DialogPillButton(
                    text = "−  " + stringResource(R.string.lyrics_sync_later),
                    fill = ink.copy(alpha = 0.10f), textColor = ink, modifier = Modifier.weight(1f),
                    onClick = { actions.setLyricsOffset(song, lyrics.offsetMs - com.dopavin.data.LyricsOffsetStore.STEP_MS) },
                )
                com.dopavin.ui.components.DialogPillButton(
                    text = stringResource(R.string.lyrics_sync_earlier) + "  +",
                    fill = ink.copy(alpha = 0.10f), textColor = ink, modifier = Modifier.weight(1f),
                    onClick = { actions.setLyricsOffset(song, lyrics.offsetMs + com.dopavin.data.LyricsOffsetStore.STEP_MS) },
                )
            }
            com.dopavin.ui.components.DialogPillButton(
                text = stringResource(R.string.lyrics_sync_reset),
                fill = ink.copy(alpha = 0.10f), textColor = ink, modifier = Modifier.fillMaxWidth(),
                onClick = { actions.setLyricsOffset(song, 0L) },
            )
        }
    }
}

@Composable
private fun providerName(id: String): String = stringResource(
    when (id) {
        com.dopavin.data.LyricsProviders.BETTER -> R.string.lyrics_provider_better
        com.dopavin.data.LyricsProviders.LRCLIB -> R.string.lyrics_provider_lrclib
        com.dopavin.data.LyricsProviders.PORTATO -> R.string.lyrics_provider_portato
        com.dopavin.data.LyricsProviders.LYRICSPLUS -> R.string.lyrics_provider_lyricsplus
        com.dopavin.data.LyricsProviders.UNISON -> R.string.lyrics_provider_unison
        com.dopavin.data.LyricsProviders.KUGOU -> R.string.lyrics_provider_kugou
        else -> R.string.lyrics_provider_choose
    },
)

/** Source line ("via LRCLIB · Line-synced") with the source and timing buttons. */
@Composable
private fun LyricsControls(
    lyrics: LyricsUi,
    showSync: Boolean,
    onSource: () -> Unit,
    onSync: () -> Unit,
    showCard: Boolean,
    onCard: () -> Unit,
) {
    val label = if (lyrics.status == LyricsStatus.Found && lyrics.source != null) {
        val kind = when (lyrics.kind) {
            com.dopavin.data.LyricsKind.WordSynced -> R.string.lyrics_kind_word
            com.dopavin.data.LyricsKind.LineSynced -> R.string.lyrics_kind_line
            else -> R.string.lyrics_kind_plain
        }
        stringResource(R.string.lyrics_via, providerName(lyrics.source), stringResource(kind))
    } else {
        providerName(lyrics.selection)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Dimens.screenEdge, end = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(label, AppTheme.type.caption, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        if (showCard) {
            com.dopavin.ui.components.IconAction(
                icon = R.drawable.ic_share,
                description = stringResource(R.string.cd_lyrics_card),
                onClick = onCard,
                tint = AppTheme.colors.textSecondary,
            )
        }
        if (showSync) {
            com.dopavin.ui.components.IconAction(
                icon = R.drawable.ic_timer,
                description = stringResource(R.string.cd_lyrics_sync),
                onClick = onSync,
                tint = AppTheme.colors.textSecondary,
            )
        }
        com.dopavin.ui.components.IconAction(
            icon = R.drawable.ic_cloud,
            description = stringResource(R.string.cd_lyrics_source),
            onClick = onSource,
            tint = AppTheme.colors.textSecondary,
        )
    }
}

/** One entry of the lyrics list: a line, or a count-in gap between two lines. */
private sealed interface LyricEntry {
    data class Line(val index: Int) : LyricEntry
    data class Gap(val startMs: Long, val endMs: Long) : LyricEntry
}

/** Lines plus a Gap entry before the first line / after a line when the pause is long. */
private fun buildEntries(lines: List<LyricLine>): List<LyricEntry> {
    val out = ArrayList<LyricEntry>(lines.size + 4)
    var prevEnd = 0L
    lines.forEachIndexed { i, line ->
        if (line.timeMs - prevEnd >= GAP_MIN_MS) out += LyricEntry.Gap(prevEnd, line.timeMs)
        out += LyricEntry.Line(i)
        prevEnd = if (line.endMs > line.timeMs) line.endMs else line.timeMs + LINE_GUESS_MS
    }
    return out
}

@Composable
private fun LyricsBody(
    song: Song,
    playback: PlaybackUi,
    lyrics: LyricsUi,
    actions: MusicActions,
) {
    if (lyrics.synced.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
        ) {
            AppText(
                text = lyrics.plain.orEmpty(),
                style = AppTheme.type.title,
                maxLines = Int.MAX_VALUE,
                color = AppTheme.colors.textPrimary,
            )
            Attribution()
        }
        return
    }

    val position = com.dopavin.ui.components.rememberFramePosition(playback.mediaId, playback.isPlaying, actions::position)
    val offset = lyrics.offsetMs
    val lines = lyrics.synced
    val entries = remember(lines) { buildEntries(lines) }
    val index by remember(lines) {
        derivedStateOf { currentLineIndex(lines, (position.longValue + offset) + LEAD_MS) }
    }
    // The list row to centre: the current line, or the count-in dots while a long gap plays.
    val focusEntry by remember(entries) {
        derivedStateOf {
            val pos = (position.longValue + offset)
            val gap = entries.indexOfFirst { it is LyricEntry.Gap && pos >= it.startMs && pos < it.endMs - LEAD_MS }
            if (gap >= 0) gap else entries.indexOfFirst { it is LyricEntry.Line && it.index == index }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(focusEntry) {
        // Follow the song, but never fight a scroll the user started; the next change resumes.
        if (focusEntry >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(focusEntry, scrollOffset = -SCROLL_LEAD_PX)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade the list out at the edges (alpha mask, so it works on any background).
                val top = FADE_TOP_DP.dp.toPx() / size.height
                val bottom = FADE_BOTTOM_DP.dp.toPx() / size.height
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        top to Color.Black,
                        1f - bottom to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Spacer(Modifier.height(Dimens.lg)) }
            itemsIndexed(
                entries,
                key = { i, entry ->
                    when (entry) {
                        is LyricEntry.Line -> "l${entry.index}"
                        is LyricEntry.Gap -> "g$i"
                    }
                },
            ) { entryIndex, entry ->
                when (entry) {
                    is LyricEntry.Line -> {
                        val line = lines[entry.index]
                        val active = entry.index == index && focusEntry == entryIndex
                        LyricRow(
                            line = line,
                            nextMs = lines.getOrNull(entry.index + 1)?.timeMs,
                            active = active,
                            far = abs(entry.index - index) > 2,
                            playing = playback.isPlaying,
                            positionMs = if (active) (position.longValue + offset) else 0L,
                            onSeek = { actions.seekTo((line.timeMs - offset).coerceAtLeast(0L)) },
                        )
                    }
                    is LyricEntry.Gap -> GapDots(
                        active = focusEntry == entryIndex,
                        progress = if (focusEntry == entryIndex) {
                            (((position.longValue + offset) - entry.startMs).toFloat() /
                                (entry.endMs - entry.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                    )
                }
            }
            item { Attribution() }
            item { Spacer(Modifier.height(Dimens.xl)) }
        }
    }
}

/**
 * One timed line. The active line sits at full colour; with word timing its words light up as they
 * are sung (a word fades from dim to full over its own duration). The lines around it are dimmed
 * and scaled down so the eye rests on the line being sung.
 */
@Composable
private fun LyricRow(
    line: LyricLine,
    nextMs: Long?,
    active: Boolean,
    far: Boolean,
    playing: Boolean,
    positionMs: Long,
    onSeek: () -> Unit,
) {
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    val dim = colors.textSecondary.copy(alpha = if (far) FAR_ALPHA else NEAR_ALPHA)
    val colorTarget = if (active) colors.textPrimary else dim
    val color by animateColorAsState(colorTarget, tween(motion.medium), label = "lyricColor")
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else NEAR_SCALE,
        animationSpec = tween(motion.medium),
        label = "lyricScale",
    )
    val style = AppTheme.type.title.copy(fontSize = LYRIC_SP.sp, lineHeight = (LYRIC_SP * 1.25f).sp, fontWeight = FontWeight.Bold)
    val text: AnnotatedString = if (active) {
        karaokeText(line, nextMs, positionMs + LEAD_MS, colors.textPrimary, colors.textSecondary.copy(alpha = 0.55f), AppTheme.motion.reduce)
    } else {
        AnnotatedString(line.text.ifBlank { "♪" })
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale(pressedScale = 0.985f) { onSeek() }
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
        )
    }
}

/**
 * The active line as karaoke text. Word times come from the provider, or are estimated for
 * line-synced lyrics ([Karaoke.words]). Each word sweeps from dim to full character by character
 * with an eased overlap, so the highlight glides along the line instead of switching word by word.
 */
internal fun karaokeText(
    line: LyricLine,
    nextMs: Long?,
    positionMs: Long,
    sung: Color,
    upcoming: Color,
    /** Reduce motion: a word turns on as a whole when it starts, no sweep. */
    reduceMotion: Boolean = false,
): AnnotatedString {
    val words = com.dopavin.data.Karaoke.words(line, nextMs)
    if (words.isEmpty()) return AnnotatedString(line.text.ifBlank { "♪" })
    return buildAnnotatedString {
        words.forEachIndexed { i, w ->
            val progress = com.dopavin.data.wordProgress(w, positionMs)
            // Pieces, not chars: vowel signs, conjuncts and cursive letters are never split (Hindi, Arabic ...).
            val pieces = com.dopavin.data.Karaoke.units(w.text)
            pieces.forEachIndexed { c, piece ->
                val p = if (reduceMotion) (if (progress > 0f) 1f else 0f) else com.dopavin.data.Karaoke.charProgress(progress, c, pieces.size)
                withStyle(SpanStyle(color = lerp(upcoming, sung, p))) { append(piece) }
            }
            if (w.spaceAfter && i < words.lastIndex) append(' ')
        }
    }
}

/** Three dots that fill one after another across a long instrumental gap. */
@Composable
private fun GapDots(active: Boolean, progress: Float) {
    val colors = AppTheme.colors
    val alpha by animateFloatAsState(if (active) 1f else 0.25f, tween(AppTheme.motion.medium), label = "gapAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        repeat(3) { i ->
            val fill = (progress * 3f - i).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(CircleShape)
                    .background(lerp(colors.textSecondary.copy(alpha = 0.4f), colors.textPrimary, fill)),
            )
        }
    }
}

@Composable
private fun Attribution() {
    AppText(
        text = stringResource(R.string.lyrics_credit),
        style = AppTheme.type.caption,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
    )
}

/** Highlight a line slightly before it is sung, which reads better than lagging behind. */
private const val LEAD_MS = 300L
private const val SCROLL_LEAD_PX = 220
/** Resting weight of the lines just above and below the current one, and of the rest. */
private const val NEAR_ALPHA = 0.5f
private const val FAR_ALPHA = 0.28f
private const val NEAR_SCALE = 0.94f
/** Count-in dots appear for pauses of at least this long; a line without an end time is assumed this long. */
private const val GAP_MIN_MS = 6_000L
private const val LINE_GUESS_MS = 3_000L
/** One-off decorative sizes (logged in docs/07): lyric type size and the list edge fades. */
private const val LYRIC_SP = 26
private const val FADE_TOP_DP = 40
private const val FADE_BOTTOM_DP = 72


/** F-194: pick up to [com.dopavin.ui.components.LYRICS_CARD_MAX_LINES] lines and a style, then share the card as an image. */
@Composable
private fun LyricsCardDialog(song: Song, lyrics: LyricsUi, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ink = com.dopavin.ui.components.glassInk()
    val colors = AppTheme.colors
    val max = com.dopavin.ui.components.LYRICS_CARD_MAX_LINES
    val all = remember(lyrics.synced, lyrics.plain) {
        val raw = if (lyrics.synced.isNotEmpty()) lyrics.synced.map { it.text } else lyrics.plain.orEmpty().lines()
        raw.map { it.trim() }.filter { it.isNotBlank() && it != "♪" }
    }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    var style by remember { mutableStateOf(com.dopavin.ui.components.LyricsCardStyle.Dark) }
    val failed = stringResource(R.string.lyrics_card_failed)
    com.dopavin.ui.components.GlassDialog(
        title = stringResource(R.string.lyrics_card_title),
        body = stringResource(R.string.lyrics_card_hint, max),
        confirmLabel = stringResource(R.string.lyrics_card_share),
        confirmEnabled = picked.isNotEmpty(),
        dismissLabel = stringResource(R.string.lyrics_done),
        onConfirm = {
            val lines = picked.sorted().map { all[it] }
            val cover = song.artworkUri.let {
                com.dopavin.data.ArtworkLoader.peek(it, com.dopavin.data.ArtworkSize.Large)
                    ?: com.dopavin.data.ArtworkLoader.peek(it, com.dopavin.data.ArtworkSize.Cell)
                    ?: com.dopavin.data.ArtworkLoader.peek(it, com.dopavin.data.ArtworkSize.Row)
            }
            val bitmap = com.dopavin.ui.components.renderLyricsCard(
                lines, song.title, song.artist, cover, style, colors.accent.toArgb(),
            )
            if (!com.dopavin.ui.components.shareLyricsCard(context, bitmap)) {
                android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_SHORT).show()
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), modifier = Modifier.fillMaxWidth()) {
            com.dopavin.ui.components.LyricsCardStyle.values().forEach { s ->
                val selected = s == style
                com.dopavin.ui.components.DialogPillButton(
                    text = stringResource(
                        when (s) {
                            com.dopavin.ui.components.LyricsCardStyle.Dark -> R.string.lyrics_card_dark
                            com.dopavin.ui.components.LyricsCardStyle.Light -> R.string.lyrics_card_light
                            com.dopavin.ui.components.LyricsCardStyle.Accent -> R.string.lyrics_card_accent
                        },
                    ),
                    fill = if (selected) colors.accent else ink.copy(alpha = 0.10f),
                    textColor = if (selected) colors.onAccent else ink,
                    modifier = Modifier.weight(1f),
                    onClick = { style = s },
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            all.forEachIndexed { i, text ->
                val on = i in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickableScale(pressedScale = 0.985f) {
                            if (on) picked.remove(i) else if (picked.size < max) picked.add(i)
                        }
                        .padding(vertical = Dimens.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(22.dp)
                            .checkBoxLook(on, colors.accent, ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.onAccent),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(Dimens.md))
                    AppText(text, AppTheme.type.body, color = ink, maxLines = 2, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun Modifier.checkBoxLook(on: Boolean, accent: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color): Modifier =
    if (on) {
        this.background(accent, androidx.compose.foundation.shape.CircleShape)
    } else {
        this.border(1.5.dp, ink.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
    }
