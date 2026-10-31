package com.dopavin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.LibraryTab
import com.dopavin.data.Song
import com.dopavin.data.SortOrder
import com.dopavin.data.Visible
import com.dopavin.data.Playlist
import com.dopavin.playback.PlaybackUi
import com.dopavin.data.DownloadError
import com.dopavin.data.DownloadItem
import com.dopavin.data.DownloadStatus
import com.dopavin.ui.Destination
import com.dopavin.ui.DownloadsUi
import com.dopavin.ui.components.LocalSheetHost
import com.dopavin.ui.components.SheetAction
import com.dopavin.ui.components.SheetRequest
import com.dopavin.ui.MusicActions
import com.dopavin.ui.ViewState
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.components.AlphabetScroller
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.FloatingBackButton
import com.dopavin.ui.components.ArtistRow
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.GradientButton
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.SelectChip
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.sectionLabel
import com.dopavin.ui.components.songCountText
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * S-04. Downloads (the Library tab, F-110): every song saved inside the app, as Songs / Artists /
 * Liked, with sort and an alphabet fast-scroller. Unfinished downloads sit above the list with their
 * progress. Long-press starts a multi-selection (F-100) with a bottom action bar.
 */
@Composable
fun LibraryScreen(
    view: ViewState,
    visible: Visible,
    playback: PlaybackUi,
    liked: Set<Long>,
    songsById: Map<Long, Song>,
    playlists: List<Playlist>,
    downloads: DownloadsUi,
    actions: MusicActions,
) {
    // The title, tabs and unfinished downloads are the first item of the list, so they scroll away;
    // only the glass back button stays where it is (same as the artist and playlist pages).
    val header: @Composable () -> Unit = {
        Column {
            LibraryHeader(view, actions)
            ActiveDownloads(downloads.active, actions)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LibraryContent(view, visible, playback, liked, songsById, playlists, downloads, actions, header)
        FloatingBackButton(actions::back)
    }
}

@Composable
private fun LibraryHeader(view: ViewState, actions: MusicActions) {
    Column(Modifier.statusBarsPadding()) {
        // Room for the fixed glass back button; the title sits below it and scrolls with the page.
        Spacer(Modifier.height(Dimens.touchTarget + Dimens.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Dimens.screenEdge, end = Dimens.sm, top = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(R.string.title_downloads),
                style = AppTheme.type.display,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (view.tab == LibraryTab.Songs) SortSheetButton(view.sort, actions::setSort)
            IconAction(
                icon = R.drawable.ic_queue,
                description = stringResource(R.string.pl_title),
                onClick = actions::openPlaylists,
            )
            IconAction(
                icon = R.drawable.ic_settings,
                description = stringResource(R.string.cd_settings),
                onClick = actions::openSettings,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            SelectChip(
                text = stringResource(R.string.tab_songs),
                selected = view.tab == LibraryTab.Songs,
                onClick = { actions.setTab(LibraryTab.Songs) },
            )
            SelectChip(
                text = stringResource(R.string.tab_artists),
                selected = view.tab == LibraryTab.Artists,
                onClick = { actions.setTab(LibraryTab.Artists) },
            )
            SelectChip(
                text = stringResource(R.string.tab_liked),
                selected = view.tab == LibraryTab.Liked,
                onClick = { actions.setTab(LibraryTab.Liked) },
            )
        }
    }
}

/** The sort button opens a bottom sheet (F-109), never a dropdown. */
@Composable
private fun SortSheetButton(sort: SortOrder, onSort: (SortOrder) -> Unit) {
    val host = LocalSheetHost.current
    val titleLabel = stringResource(R.string.sort_title)
    val artistLabel = stringResource(R.string.sort_artist)
    val recentLabel = stringResource(R.string.sort_recent)
    val heading = stringResource(R.string.cd_sort)
    IconAction(
        icon = R.drawable.ic_sort,
        description = heading,
        onClick = {
            host.show(
                SheetRequest(
                    title = heading,
                    actions = SortOrder.values().map { order ->
                        SheetAction(
                            label = when (order) {
                                SortOrder.Title -> titleLabel
                                SortOrder.Artist -> artistLabel
                                SortOrder.Recent -> recentLabel
                            },
                            icon = if (order == sort) R.drawable.ic_check else null,
                            onClick = { onSort(order) },
                        )
                    },
                ),
            )
        },
    )
}

/** Unfinished downloads: one compact row each (progress, cancel or retry). */
@Composable
private fun ActiveDownloads(items: List<DownloadItem>, actions: MusicActions) {
    if (items.isEmpty()) return
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs)
            .background(colors.surfaceRaised, RoundedCornerShape(Dimens.radiusMedium))
            .padding(vertical = Dimens.xs),
    ) {
        items.take(4).forEach { item ->
            val failed = item.status == DownloadStatus.Failed
            val status = when {
                failed -> stringResource(
                    when (item.error) {
                        DownloadError.WifiOnly -> R.string.dl_err_wifi
                        DownloadError.NoSpace -> R.string.dl_err_space
                        DownloadError.Unavailable -> R.string.dl_err_unavailable
                        else -> R.string.dl_err_network
                    },
                )
                item.status == DownloadStatus.Queued -> stringResource(R.string.dl_status_queued)
                item.progress >= 0f -> stringResource(R.string.dl_status_percent, (item.progress * 100).toInt())
                else -> stringResource(R.string.dl_status_starting)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Dimens.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = Dimens.sm)) {
                    AppText(item.song.title, AppTheme.type.label)
                    AppText(
                        status,
                        AppTheme.type.caption,
                        color = if (failed) colors.error else colors.textSecondary,
                    )
                    if (!failed) {
                        Spacer(Modifier.height(Dimens.xs))
                        if (item.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { item.progress },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = colors.accent,
                                trackColor = colors.divider,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = colors.accent,
                                trackColor = colors.divider,
                            )
                        }
                    }
                }
                if (failed) {
                    IconAction(
                        icon = R.drawable.ic_download,
                        description = stringResource(R.string.menu_retry_download),
                        onClick = { actions.downloadSong(item.song) },
                    )
                }
                IconAction(
                    icon = R.drawable.ic_close,
                    description = stringResource(R.string.menu_cancel_download),
                    onClick = { actions.cancelDownload(item.videoId) },
                    tint = colors.textSecondary,
                )
            }
        }
        if (items.size > 4) {
            AppText(
                stringResource(R.string.dl_more_count, items.size - 4),
                AppTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = Dimens.lg, bottom = Dimens.sm),
            )
        }
    }
}

@Composable
private fun LibraryContent(
    view: ViewState,
    visible: Visible,
    playback: PlaybackUi,
    liked: Set<Long>,
    songsById: Map<Long, Song>,
    playlists: List<Playlist>,
    downloads: DownloadsUi,
    actions: MusicActions,
    header: @Composable () -> Unit,
) {
    if (visible.totalSongs == 0) {
        Column(Modifier.fillMaxSize()) {
            header()
            // Something is still downloading: the list above says so; no "nothing here" yet.
            if (downloads.active.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EmptyState(
                        title = stringResource(R.string.dl_empty_title),
                        body = stringResource(R.string.dl_empty_body),
                        actionLabel = stringResource(R.string.dl_empty_action),
                        onAction = { actions.setDestination(Destination.Search) },
                    )
                }
            }
        }
        return
    }
    when (view.tab) {
        LibraryTab.Songs -> SongList(
            visible.songs, view.sort, playback, liked, songsById, playlists, actions, header,
        )
        LibraryTab.Artists -> ArtistList(visible, actions, header)
        LibraryTab.Liked -> {
            val likedSongs = remember(visible.songs, liked) { visible.songs.filter { it.id in liked } }
            if (likedSongs.isEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    header()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EmptyState(
                            title = stringResource(R.string.liked_empty_title),
                            body = stringResource(R.string.liked_empty_body),
                        )
                    }
                }
            } else {
                SongList(
                    likedSongs, view.sort, playback, liked, songsById, playlists, actions, header,
                )
            }
        }
    }
}

// ---- Songs ----

private sealed interface Entry {
    val key: String

    class Header(val letter: Char) : Entry {
        override val key: String = "h$letter"
    }

    class Item(val song: Song, val index: Int) : Entry {
        override val key: String = "s${song.id}"
    }
}

/** 'A'..'Z' for letters, '0' for digits, '#' for everything else. */
private fun sectionOf(title: String): Char {
    val c = title.firstOrNull() ?: return '#'
    return when {
        c.isLetter() -> c.uppercaseChar()
        c.isDigit() -> '0'
        else -> '#'
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    sort: SortOrder,
    playback: PlaybackUi,
    liked: Set<Long>,
    songsById: Map<Long, Song>,
    playlists: List<Playlist>,
    actions: MusicActions,
    header: @Composable () -> Unit,
) {
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var playlistPicker by remember { mutableStateOf(false) }
    var newPlaylistDialog by remember { mutableStateOf(false) }
    val selectedSongs = remember(songs, selection) { songs.filter { it.id in selection } }

    val grouped = sort == SortOrder.Title
    val entries = remember(songs, grouped) {
        val list = ArrayList<Entry>(songs.size + 32)
        var previous: Char? = null
        songs.forEachIndexed { index, song ->
            if (grouped) {
                val section = sectionOf(song.title)
                if (section != previous) {
                    list.add(Entry.Header(section))
                    previous = section
                }
            }
            list.add(Entry.Item(song, index))
        }
        list
    }
    // Position of each section header inside the LazyColumn (page header is item 0, actions row item 1).
    val headerIndex = remember(entries) {
        val map = LinkedHashMap<Char, Int>()
        entries.forEachIndexed { i, entry -> if (entry is Entry.Header) map[entry.letter] = i + 2 }
        map
    }
    val letters = remember(headerIndex) { headerIndex.keys.toList() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
        ) {
            item(key = "page_header") { header() }
            item(key = "actions") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GradientButton(
                        text = stringResource(R.string.button_shuffle),
                        icon = R.drawable.ic_shuffle,
                        onClick = { actions.playCollection(songs, true) },
                        description = stringResource(R.string.cd_play_all),
                    )
                    TonalButton(
                        text = stringResource(R.string.button_play),
                        icon = R.drawable.ic_play,
                        onClick = { actions.playCollection(songs, false) },
                    )
                    Spacer(Modifier.weight(1f))
                    AppText(
                        text = songCountText(songs.size),
                        style = AppTheme.type.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    is Entry.Header -> SectionLetter(entry.letter)
                    is Entry.Item -> {
                        val song = entry.song
                        val selected = song.id in selection
                        SongRow(
                            song = song,
                            subtitle = if (song.format.isBlank()) song.artist else stringResource(R.string.song_subtitle_dl, song.artist, song.format),
                            current = song.id.toString() == playback.mediaId,
                            playing = playback.isPlaying,
                            onClick = {
                                if (selection.isEmpty()) actions.playSongs(songs, entry.index)
                                else selection = selection.toggleId(song.id)
                            },
                            onPlayNow = { actions.playSongs(songs, entry.index) },
                            onPlayNext = { actions.playNext(song) },
                            onAddToQueue = { actions.addToQueue(song) },
                            onRadio = { actions.openRadio(song) },
                            favorite = song.id in liked,
                            onToggleFavorite = { actions.toggleLike(song.id) },
                            onOpenDetails = { actions.openSongDetails(song) },
                            selected = selected,
                            onLongPress = { selection = selection.toggleId(song.id) },
                        )
                    }
                }
            }
        }
        if (grouped) {
            AlphabetScroller(
                letters = letters,
                onSelect = { letter ->
                    val target = headerIndex[letter] ?: return@AlphabetScroller
                    scope.launch { listState.scrollToItem(target) }
                },
            )
        }
        // F-100: the multi-selection action bar, only while something is selected.
        if (selection.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm)
                    .navigationBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            AppTheme.colors.surfaceRaised,
                            RoundedCornerShape(Dimens.radiusMedium),
                        )
                        .padding(horizontal = Dimens.xs, vertical = Dimens.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(
                        icon = R.drawable.ic_close,
                        description = stringResource(R.string.sel_cancel),
                        onClick = { selection = emptySet() },
                        tint = AppTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    AppText(
                        text = stringResource(R.string.sel_count, selection.size),
                        style = AppTheme.type.label,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(
                        icon = R.drawable.ic_queue,
                        description = stringResource(R.string.sel_add_queue, selection.size),
                        onClick = {
                            actions.addToQueueAll(selectedSongs)
                            selection = emptySet()
                        },
                        tint = AppTheme.colors.textPrimary,
                    )
                    IconAction(
                        icon = R.drawable.ic_favorite,
                        description = stringResource(R.string.sel_like, selection.size),
                        onClick = {
                            actions.likeSeveral(selectedSongs)
                            selection = emptySet()
                        },
                        tint = AppTheme.colors.accent,
                    )
                    IconAction(
                        icon = R.drawable.ic_music_note,
                        description = stringResource(R.string.sel_playlist, selection.size),
                        onClick = { playlistPicker = true },
                        tint = AppTheme.colors.textPrimary,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.xs, start = Dimens.xs, end = Dimens.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = stringResource(R.string.sel_hint),
                        style = AppTheme.type.caption,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { selection = songs.map { it.id }.toSet() },
                    ) {
                        AppText(stringResource(R.string.sel_select_all), AppTheme.type.label)
                    }
                }
            }
        }
    }
    if (playlistPicker) {
        com.dopavin.ui.components.GlassDialog(
            title = stringResource(R.string.sel_playlist, selection.size),
            onDismiss = { playlistPicker = false },
        ) {
            val colors = AppTheme.colors
            val ink = if (com.dopavin.ui.glass.LocalGlassStyle.current.enabled && com.dopavin.ui.glass.LocalGlassBackdrop.current != null) {
                if (colors.isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
            } else colors.textPrimary
            if (playlists.isEmpty()) {
                AppText(stringResource(R.string.pl_empty), AppTheme.type.bodySmall, color = ink.copy(alpha = 0.7f))
            } else {
                playlists.forEach { playlist ->
                    com.dopavin.ui.components.DialogPillButton(
                        text = playlist.name,
                        fill = ink.copy(alpha = 0.10f),
                        textColor = ink,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            actions.appendToPlaylist(playlist.name, selectedSongs)
                            playlistPicker = false
                            selection = emptySet()
                        },
                    )
                }
            }
            com.dopavin.ui.components.DialogPillButton(
                text = stringResource(R.string.pl_new_title),
                fill = colors.accent.copy(alpha = 0.85f),
                textColor = colors.onAccent,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    playlistPicker = false
                    newPlaylistDialog = true
                },
            )
        }
    }
    if (newPlaylistDialog) {
        PlaylistNameDialog(
            onDismiss = { newPlaylistDialog = false },
            onSave = { name ->
                newPlaylistDialog = false
                actions.createPlaylist(name, selectedSongs)
                selection = emptySet()
            },
        )
    }
}

/** Small helper: add/remove one id from the selection set. */
private fun Set<Long>.toggleId(id: Long): Set<Long> =
    if (id in this) this - id else this + id

@Composable
private fun SectionLetter(letter: Char) {
    AppText(
        text = sectionLabel(letter),
        style = AppTheme.type.label,
        color = AppTheme.colors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.screenEdge, top = Dimens.lg, bottom = Dimens.xs)
            .semantics { heading() },
    )
}

// ---- Artists ----

@Composable
private fun ArtistList(visible: Visible, actions: MusicActions, header: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
    ) {
        item(key = "page_header") { header() }
        items(visible.artists, key = { it.key }) { artist ->
            ArtistRow(artist = artist, onClick = { actions.openArtist(artist.key) })
        }
    }
}
