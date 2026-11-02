package com.dopavin.ui.screens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.Playlist
import com.dopavin.data.Song
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.MusicViewModel.StorageUi
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.FloatingBackButton
import androidx.compose.foundation.layout.Box
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.MarqueeText
import com.dopavin.ui.components.ScreenHeader
import com.dopavin.ui.components.SectionHeader
import com.dopavin.ui.components.SettingsRow
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.formatTime
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
@Composable
fun SongDetailsDialog(song: Song, actions: MusicActions) {
    val ink = com.dopavin.ui.components.glassInk()
    com.dopavin.ui.components.GlassDialog(
        title = stringResource(R.string.details_title),
        dismissLabel = stringResource(R.string.lyrics_done),
        onDismiss = actions::closeSongDetails,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(
                uri = song.artworkUri,
                sizePx = ArtworkSize.Row,
                shape = RoundedCornerShape(Dimens.radiusMedium),
                modifier = Modifier.size(Dimens.artworkQuick),
                seed = song.album,
            )
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                MarqueeText(song.title, AppTheme.type.headline, color = ink)
                MarqueeText(song.artist, AppTheme.type.body, color = ink.copy(alpha = 0.7f))
            }
        }
        DetailLine(
            stringResource(R.string.details_source),
            stringResource(if (song.isDownloaded) R.string.details_source_downloaded else R.string.details_source_online),
            ink,
        )
        if (!song.isDownloaded) {
            AppText(
                stringResource(R.string.details_stream_only),
                AppTheme.type.bodySmall,
                color = ink.copy(alpha = 0.7f),
                maxLines = 4,
            )
        }
        DetailLine(stringResource(R.string.details_duration), formatTime(song.durationMs), ink)
        if (song.isDownloaded) {
            if (song.format.isNotBlank()) DetailLine(stringResource(R.string.details_format), song.format, ink)
            DetailLine(
                stringResource(R.string.details_size),
                if (song.sizeBytes > 0L) {
                    String.format(Locale.ROOT, "%.1f MB", song.sizeBytes / 1024f / 1024f)
                } else {
                    stringResource(R.string.details_unknown)
                },
                ink,
            )
            DetailLine(
                stringResource(R.string.details_added),
                if (song.dateAdded > 0L) {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(song.dateAdded))
                } else {
                    stringResource(R.string.details_unknown)
                },
                ink,
            )
        }
    }
}
@Composable
private fun DetailLine(label: String, value: String, ink: androidx.compose.ui.graphics.Color) {
    Column {
        AppText(label, AppTheme.type.caption, color = ink.copy(alpha = 0.7f))
        AppText(value, AppTheme.type.label, color = ink, maxLines = 2)
    }
}
@Composable
private fun InfoValue(label: String, value: String) {
    Column(Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm)) {
        AppText(label, AppTheme.type.caption, color = AppTheme.colors.textSecondary)
        AppText(value, AppTheme.type.label)
    }
}
@Composable
fun StorageScreen(storage: StorageUi, actions: MusicActions) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(onBack = actions::back, title = stringResource(R.string.storage_title))
            SectionHeader(
                title = stringResource(R.string.storage_downloads),
                modifier = Modifier.padding(top = Dimens.lg, bottom = Dimens.xs),
            )
            InfoValue(
                label = stringResource(R.string.storage_downloads),
                value = stringResource(
                    R.string.storage_downloads_size,
                    storage.downloadCount,
                    sizeText(storage.downloadBytes),
                ),
            )
            InfoValue(
                label = stringResource(R.string.storage_stores),
                value = sizeText(storage.appDataBytes),
            )
            InfoValue(
                label = stringResource(R.string.storage_free),
                value = sizeText(storage.freeBytes),
            )
            AppText(
                stringResource(R.string.storage_online_note),
                AppTheme.type.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
            )
            if (storage.lyricsBytes > 0L) {
                SettingsRow(
                    title = stringResource(R.string.storage_clear_lyrics),
                    subtitle = stringResource(R.string.storage_clear_lyrics_body, sizeText(storage.lyricsBytes)),
                    onClick = actions::clearLyricsCache,
                    chevron = false,
                )
            }
            if (storage.downloadCount > 0) {
                SettingsRow(
                    title = stringResource(R.string.settings_delete_downloads),
                    subtitle = stringResource(R.string.settings_delete_downloads_body),
                    onClick = { confirmDeleteAll = true },
                    chevron = false,
                )
            }
            Spacer(Modifier.navigationBarsPadding().heightIn(Dimens.xl))
        }
    }
    if (confirmDeleteAll) {
        com.dopavin.ui.components.GlassDialog(
            title = stringResource(R.string.settings_delete_downloads),
            body = stringResource(R.string.settings_delete_downloads_body),
            confirmLabel = stringResource(R.string.sel_confirm_delete),
            destructive = true,
            onConfirm = {
                confirmDeleteAll = false
                actions.deleteAllDownloads()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}
@Composable
private fun sizeText(bytes: Long): String = when {
    bytes <= 0L -> stringResource(R.string.size_unknown_mib)
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.1f GB", bytes / 1024f / 1024f / 1024f)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1024f / 1024f)
}
@Composable
fun PlaylistsScreen(playlists: List<Playlist>, actions: MusicActions) {
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(
                onBack = actions::back,
                title = stringResource(R.string.pl_title),
                actions = {
                    IconAction(
                        icon = R.drawable.ic_queue,
                        description = stringResource(R.string.pl_new_title),
                        onClick = { creating = true },
                    )
                },
            )
            if (playlists.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.pl_title),
                    body = stringResource(R.string.pl_empty),
                )
            } else {
                playlists.forEach { playlist ->
                    SettingsRow(
                        title = playlist.name,
                        subtitle = stringResource(R.string.pl_count, playlist.songs.size),
                        onClick = { actions.openPlaylist(playlist.name) },
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding().heightIn(Dimens.xl))
        }
    }
    if (creating) {
        PlaylistNameDialog(
            onDismiss = { creating = false },
            onSave = { name ->
                creating = false
                actions.createPlaylist(name, emptyList())
            },
        )
    }
}
@Composable
fun PlaylistDetailScreen(
    name: String,
    cover: String?,
    playback: PlaybackUi,
    songs: List<Song>,
    liked: Set<Long>,
    actions: MusicActions,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.00f to Color.Black,
                                    0.42f to Color.Black,
                                    0.55f to Color.Black.copy(alpha = 0.88f),
                                    0.67f to Color.Black.copy(alpha = 0.64f),
                                    0.78f to Color.Black.copy(alpha = 0.38f),
                                    0.88f to Color.Black.copy(alpha = 0.16f),
                                    0.95f to Color.Black.copy(alpha = 0.04f),
                                    1.00f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    PlaylistCover(name, cover, songs)
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenEdge)
                        .padding(bottom = Dimens.md),
                ) {
                    AppText(
                        text = name,
                        style = AppTheme.type.display,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    AppText(
                        text = stringResource(R.string.pl_count, songs.size),
                        style = AppTheme.type.caption,
                        color = AppTheme.colors.textSecondary,
                    )
                    if (songs.isNotEmpty()) {
                        Row(
                            Modifier.padding(top = Dimens.md),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TonalButton(
                                text = stringResource(R.string.play_play),
                                icon = R.drawable.ic_play,
                                accent = true,
                                onClick = { actions.playSongs(songs, 0) },
                            )
                            TonalButton(
                                text = stringResource(R.string.play_shuffle),
                                icon = R.drawable.ic_shuffle,
                                onClick = { actions.playCollection(songs, true) },
                            )
                            com.dopavin.ui.components.CollectionMoreButton(
                                title = name,
                                actions = listOf(
                                    com.dopavin.ui.components.SheetAction(stringResource(R.string.list_download_all), R.drawable.ic_download) {
                                        actions.downloadAll(songs)
                                    },
                                    com.dopavin.ui.components.SheetAction(stringResource(R.string.list_add_all_queue), R.drawable.ic_queue) {
                                        actions.addToQueueAll(songs)
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            if (songs.isEmpty()) {
                EmptyState(
                    title = name,
                    body = stringResource(R.string.pl_empty),
                )
            }
            songs.forEachIndexed { index, song ->
                SongRow(
                    song = song,
                    subtitle = song.album,
                    current = song.id.toString() == playback.mediaId,
                    playing = playback.isPlaying,
                    onClick = { actions.playSongs(songs, index) },
                    onPlayNow = { actions.playSongs(songs, index) },
                    onPlayNext = { actions.playNext(song) },
                    onAddToQueue = { actions.addToQueue(song) },
                    onRadio = { actions.openRadio(song) },
                    favorite = song.id in liked,
                    onToggleFavorite = { actions.toggleLike(song.id) },
                    onOpenDetails = { actions.openSongDetails(song) },
                    onRemove = { actions.removeSongFromPlaylist(name, index) },
                )
            }
            SettingsRow(
                title = stringResource(R.string.pl_delete),
                onClick = { confirmDelete = true },
                chevron = false,
            )
            Spacer(Modifier.navigationBarsPadding().heightIn(Dimens.xl))
        }
        FloatingBackButton(actions::back)
    }
    if (confirmDelete) {
        com.dopavin.ui.components.GlassDialog(
            title = stringResource(R.string.pl_delete),
            body = stringResource(R.string.pl_delete_body, name),
            confirmLabel = stringResource(R.string.pl_delete),
            destructive = true,
            onConfirm = {
                actions.deletePlaylist(name)
                actions.back()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
@Composable
internal fun PlaylistNameDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    com.dopavin.ui.components.GlassDialog(
        title = stringResource(R.string.pl_new_title),
        confirmLabel = stringResource(R.string.pl_save),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name) },
        onDismiss = onDismiss,
    ) {
        val colors = AppTheme.colors
        val ink = if (com.dopavin.ui.glass.LocalGlassStyle.current.enabled && com.dopavin.ui.glass.LocalGlassBackdrop.current != null) {
            if (colors.isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
        } else colors.textPrimary
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(ink.copy(alpha = 0.10f))
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (name.isEmpty()) {
                AppText(stringResource(R.string.pl_name_hint), AppTheme.type.body, color = ink.copy(alpha = 0.55f))
            }
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = AppTheme.type.body.copy(color = ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (name.isBlank()) {
            AppText(stringResource(R.string.pl_name_empty), AppTheme.type.bodySmall, color = colors.error)
        }
    }
}
@Composable
internal fun PlaylistCover(name: String, cover: String?, songs: List<Song>) {
    val art = remember(songs) { songs.map { it.artworkUri }.distinct().take(4) }
    if (!cover.isNullOrBlank()) {
        ArtworkImage(
            uri = android.net.Uri.parse(cover),
            sizePx = ArtworkSize.Player,
            shape = androidx.compose.ui.graphics.RectangleShape,
            modifier = Modifier.fillMaxSize(),
            seed = name,
        )
    } else if (art.size >= 4) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..1) {
                        ArtworkImage(
                            uri = art[row * 2 + col],
                            sizePx = ArtworkSize.Row,
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            seed = name,
                        )
                    }
                }
            }
        }
    } else {
        ArtworkImage(
            uri = art.firstOrNull(),
            sizePx = ArtworkSize.Player,
            shape = androidx.compose.ui.graphics.RectangleShape,
            modifier = Modifier.fillMaxSize(),
            seed = name,
        )
    }
}
