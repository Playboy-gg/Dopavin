package com.dopavin.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.dopavin.ui.glass.glassSurface
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import android.net.Uri
import com.dopavin.data.ArtistPick
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dopavin.R
import com.dopavin.data.Song
import com.dopavin.data.SortOrder
import com.dopavin.data.Visible
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.Destination
import com.dopavin.ui.MusicActions
import com.dopavin.ui.OnlineStage
import com.dopavin.ui.OnlineUi
import com.dopavin.ui.SearchUi
import com.dopavin.ui.ViewState
import com.dopavin.ui.components.AlbumCard
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.data.ArtworkSize
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.songCountText
import com.dopavin.ui.components.albumCountText
import com.dopavin.data.Album
import com.dopavin.data.Artist
import com.dopavin.data.SearchAssist
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import com.dopavin.ui.components.SelectChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.ArtistAvatarCard
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.SectionHeader
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.components.placeholderBrush
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
@Composable
fun SearchScreen(
    view: ViewState,
    visible: Visible,
    results: Visible,
    searchUi: SearchUi,
    online: OnlineUi,
    playback: PlaybackUi,
    currentSong: Song?,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val query = view.query.trim()
    var filter by rememberSaveable { mutableStateOf(SearchFilter.All) }
    val suggestion = searchUi.suggestion?.takeIf { searchUi.forQuery == query && query.isNotEmpty() }
    val commit = { if (query.isNotEmpty()) actions.commitSearch(query) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = Dimens.screenEdge)
                .padding(top = Dimens.lg, bottom = Dimens.md),
        ) {
            AppText(
                text = stringResource(R.string.nav_search),
                style = AppTheme.type.display,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Dimens.md))
            com.dopavin.ui.components.GlassSearchField(
                query = view.query,
                onQueryChange = actions::setQuery,
                hint = stringResource(R.string.search_label),
                onSearch = commit,
            )
        }
        if (query.isNotEmpty()) {
            FilterRow(filter, showOnline = online.stage != OnlineStage.Off, onSelect = { filter = it })
        }
        if (suggestion != null) {
            DidYouMean(suggestion) {
                actions.setQuery(suggestion)
                actions.commitSearch(suggestion)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                query.isEmpty() -> Landing(visible, liked.size, searchUi.recents, actions)
                results.allEmpty && online.songs.isEmpty() &&
                    online.stage != OnlineStage.Loading &&
                    online.stage != OnlineStage.Unreachable ->
                    EmptyState(
                        title = stringResource(R.string.search_empty_title, query),
                        body = stringResource(
                            if (suggestion != null) R.string.search_empty_suggest_body else R.string.search_empty_body,
                        ),
                        actionLabel = stringResource(R.string.search_clear),
                        onAction = { actions.setQuery("") },
                        icon = R.drawable.ic_search,
                    )
                else -> ResultsContent(query, results, online, playback, liked, filter, { filter = it }, commit, actions)
            }
        }
    }
}
private enum class SearchFilter { All, Songs, Artists, Albums, Online }
@Composable
private fun FilterRow(selected: SearchFilter, showOnline: Boolean, onSelect: (SearchFilter) -> Unit) {
    val filters = SearchFilter.entries.filter { it != SearchFilter.Online || showOnline }
    val labels = filters.map {
        when (it) {
            SearchFilter.All -> stringResource(R.string.search_filter_all)
            SearchFilter.Songs -> stringResource(R.string.tab_songs)
            SearchFilter.Artists -> stringResource(R.string.tab_artists)
            SearchFilter.Albums -> stringResource(R.string.search_filter_albums)
            SearchFilter.Online -> stringResource(R.string.search_online_header)
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        modifier = Modifier.padding(bottom = Dimens.sm),
    ) {
        itemsIndexed(filters) { i, f ->
            SelectChip(text = labels[i], selected = f == selected, onClick = { onSelect(f) })
        }
    }
}
@Composable
private fun DidYouMean(suggestion: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusLarge)
    val description = stringResource(R.string.search_did_you_mean_cd, suggestion)
    Row(
        Modifier
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs)
            .fillMaxWidth()
            .clip(shape)
            .clickableScale(pressedScale = 0.98f, onClick = onClick)
            .glassSurface(shape, fallback = colors.surfaceHigh)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = colors.accent, modifier = Modifier.size(Dimens.iconInline))
        AppText(stringResource(R.string.search_did_you_mean), AppTheme.type.body, color = colors.textSecondary)
        AppText(
            text = suggestion,
            style = AppTheme.type.bodyStrong,
            color = colors.accent,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        AppText("?", AppTheme.type.body, color = colors.textSecondary)
    }
}
private class Tile(val label: Int, val count: String, val icon: Int, val liked: Boolean, val sort: SortOrder?)
@Composable
private fun Landing(visible: Visible, likedCount: Int, recents: List<String>, actions: MusicActions) {
    val tiles = listOf(
        Tile(R.string.tab_songs, songCountText(visible.totalSongs), R.drawable.ic_music_note, false, SortOrder.Title),
        Tile(R.string.tab_liked, songCountText(likedCount), R.drawable.ic_favorite, true, null),
        Tile(R.string.sort_recent, stringResource(R.string.search_tile_recent), R.drawable.ic_queue, false, SortOrder.Recent),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
    ) {
        if (recents.isNotEmpty()) {
            item(key = "recent_header") {
                SectionHeader(
                    title = stringResource(R.string.search_recent),
                    actionLabel = stringResource(R.string.search_recent_clear),
                    onAction = actions::clearRecentSearches,
                    modifier = Modifier.padding(bottom = Dimens.sm),
                )
            }
            item(key = "recent_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                ) {
                    items(recents, key = { it }) { text ->
                        RecentChip(text, onClick = { actions.setQuery(text) }, onRemove = { actions.removeRecentSearch(text) })
                    }
                }
            }
        }
        item(key = "browse_header") {
            SectionHeader(
                title = stringResource(R.string.search_browse),
                modifier = Modifier.padding(top = if (recents.isNotEmpty()) Dimens.xl else 0.dp, bottom = Dimens.sm),
            )
        }
        item(key = "browse_tiles") {
            Column(
                Modifier.padding(horizontal = Dimens.screenEdge),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                        pair.forEach { tile ->
                            BrowseTileCard(
                                tile,
                                Modifier.weight(1f),
                            ) {
                                tile.sort?.let(actions::setSort)
                                if (tile.liked) actions.openFavourites() else actions.openDownloads()
                            }
                        }
                    }
                }
            }
        }
        val artists = visible.artists.sortedByDescending { it.songs.size }.take(12)
        if (artists.isNotEmpty()) {
            item(key = "browse_artists_header") {
                SectionHeader(
                    title = stringResource(R.string.search_popular_artists),
                    modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                )
            }
            item(key = "browse_artists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                ) {
                    items(artists, key = { it.key }) { artist ->
                        ArtistAvatarCard(artist = artist, onClick = { actions.openArtist(artist.key) })
                    }
                }
            }
        }
    }
}
@Composable
private fun BrowseTileCard(tile: Tile, modifier: Modifier, onClick: () -> Unit) {
    val name = stringResource(tile.label)
    val shape = RoundedCornerShape(Dimens.radiusLarge)
    Box(
        modifier
            .height(104.dp)
            .clip(shape)
            .clickableScale(pressedScale = 0.97f, onClick = onClick)
            .background(placeholderBrush(name))
            .semantics(mergeDescendants = true) { contentDescription = "$name, ${tile.count}" },
    ) {
        Icon(
            painter = painterResource(tile.icon),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.sm).size(56.dp),
        )
        Column(Modifier.align(Alignment.TopStart).padding(Dimens.lg)) {
            AppText(name, AppTheme.type.bodyStrong, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
            AppText(
                tile.count,
                AppTheme.type.caption,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}
@Composable
private fun RecentChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .heightIn(min = Dimens.chipHeight)
            .clip(CircleShape)
            .glassSurface(CircleShape, fallback = colors.surfaceRaised)
            .clickableScale(pressedScale = 0.97f, onClick = onClick)
            .padding(start = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_history), null, tint = colors.textSecondary, modifier = Modifier.size(Dimens.iconInline))
        AppText(
            text = text,
            style = AppTheme.type.label,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 180.dp).padding(horizontal = Dimens.sm),
        )
        IconAction(
            icon = R.drawable.ic_close,
            description = stringResource(R.string.search_recent_remove, text),
            onClick = onRemove,
            tint = colors.textSecondary,
            size = 36.dp,
        )
    }
}
private sealed interface Top {
    data class ArtistTop(val artist: Artist) : Top
    data class AlbumTop(val album: Album) : Top
    data class SongTop(val song: Song, val index: Int) : Top
}
private fun topResult(query: String, results: Visible): Top? {
    val q = SearchAssist.norm(query)
    results.artists.firstOrNull { SearchAssist.norm(it.name) == q }?.let { return Top.ArtistTop(it) }
    results.albums.firstOrNull { SearchAssist.norm(it.title) == q }?.let { return Top.AlbumTop(it) }
    results.artists.firstOrNull { SearchAssist.norm(it.name).startsWith(q) }?.let { return Top.ArtistTop(it) }
    results.songs.firstOrNull()?.let { return Top.SongTop(it, 0) }
    results.artists.firstOrNull()?.let { return Top.ArtistTop(it) }
    results.albums.firstOrNull()?.let { return Top.AlbumTop(it) }
    return null
}
@Composable
private fun TopResultCard(top: Top, results: Visible, onCommit: () -> Unit, actions: MusicActions) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusXLarge)
    val (title, type, detail, uri, round) = when (top) {
        is Top.ArtistTop -> TopInfo(top.artist.name, stringResource(R.string.search_type_artist), songCountText(top.artist.songs.size), top.artist.songs.first().artworkUri, true)
        is Top.AlbumTop -> TopInfo(top.album.title, stringResource(R.string.search_type_album), top.album.artist ?: stringResource(R.string.various_artists), top.album.artworkUri, false)
        is Top.SongTop -> TopInfo(top.song.title, stringResource(R.string.search_type_song), top.song.artist, top.song.artworkUri, false)
    }
    val open = {
        onCommit()
        when (top) {
            is Top.ArtistTop -> actions.openArtist(top.artist.key)
            is Top.AlbumTop -> actions.openAlbum(top.album.key)
            is Top.SongTop -> actions.playSongs(results.songs, top.index)
        }
    }
    Row(
        Modifier
            .padding(horizontal = Dimens.screenEdge)
            .fillMaxWidth()
            .clip(shape)
            .clickableScale(pressedScale = 0.98f, onClick = open)
            .glassSurface(shape, fallback = colors.surfaceHigh)
            .padding(Dimens.lg)
            .semantics(mergeDescendants = true) { contentDescription = "$type, $title, $detail" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
    ) {
        ArtworkImage(
            uri = uri,
            sizePx = ArtworkSize.Cell,
            shape = if (round) CircleShape else RoundedCornerShape(Dimens.radiusMedium),
            modifier = Modifier.size(88.dp),
            seed = title,
        )
        Column(Modifier.weight(1f)) {
            AppText(type.uppercase(), AppTheme.type.caption, color = colors.accent, maxLines = 1)
            Spacer(Modifier.height(Dimens.xs))
            AppText(title, AppTheme.type.title, maxLines = 2)
            Spacer(Modifier.height(Dimens.xs))
            AppText(detail, AppTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1)
        }
        if (top is Top.SongTop) {
            IconAction(
                icon = R.drawable.ic_play,
                description = title,
                onClick = open,
                tint = colors.onAccent,
                container = colors.accent,
                size = Dimens.playButtonSmall.coerceAtMost(52.dp),
            )
        }
    }
}
private data class TopInfo(val title: String, val type: String, val detail: String, val uri: android.net.Uri?, val round: Boolean)
@Composable
private fun ResultsContent(
    query: String,
    results: Visible,
    online: OnlineUi,
    playback: PlaybackUi,
    liked: Set<Long>,
    filter: SearchFilter,
    onFilter: (SearchFilter) -> Unit,
    onCommit: () -> Unit,
    actions: MusicActions,
) {
    val all = filter == SearchFilter.All
    val showArtists = all || filter == SearchFilter.Artists
    val showAlbums = all || filter == SearchFilter.Albums
    val showSongs = all || filter == SearchFilter.Songs
    val showOnline = all || filter == SearchFilter.Online
    val top = remember(query, results) { if (all) topResult(query, results) else null }
    val songLimit = if (all) 6 else Int.MAX_VALUE
    val songs = results.songs.take(songLimit)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
    ) {
        if (top != null) {
            item(key = "r_top_header") {
                SectionHeader(stringResource(R.string.search_top_result), Modifier.padding(bottom = Dimens.sm))
            }
            item(key = "r_top") { TopResultCard(top, results, onCommit, actions) }
        }
        if (showArtists && (results.artists.isNotEmpty() || online.artists.isNotEmpty())) {
            item(key = "r_artists_header") {
                SectionHeader(stringResource(R.string.tab_artists), Modifier.padding(top = Dimens.xl, bottom = Dimens.sm))
            }
            item(key = "r_artists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                ) {
                    items(results.artists, key = { it.key }) { artist ->
                        ArtistAvatarCard(
                            artist = artist,
                            onClick = { onCommit(); actions.openArtist(artist.key) },
                            avatarSize = 96.dp,
                        )
                    }
                    items(online.artists, key = { "oa_${it.url}" }) { hit ->
                        OnlineArtistCard(hit, onClick = { onCommit(); actions.openArtistProfile(hit.name, hit.url) })
                    }
                }
            }
        }
        if (showAlbums && results.albums.isNotEmpty()) {
            item(key = "r_albums_header") {
                SectionHeader(stringResource(R.string.search_filter_albums), Modifier.padding(top = Dimens.xl, bottom = Dimens.sm))
            }
            item(key = "r_albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    items(results.albums, key = { it.key }) { album ->
                        AlbumCard(
                            album = album,
                            current = false,
                            onClick = { onCommit(); actions.openAlbum(album.key) },
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
        }
        if (showSongs && results.songs.isNotEmpty()) {
            item(key = "r_songs_header") {
                SectionHeader(stringResource(R.string.tab_songs), Modifier.padding(top = Dimens.xl, bottom = Dimens.xs))
            }
            itemsIndexed(songs, key = { _, song -> "r_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    subtitle = if (song.format.isBlank()) song.artist else stringResource(R.string.song_subtitle_dl, song.artist, song.format),
                    current = song.id.toString() == playback.mediaId,
                    playing = playback.isPlaying,
                    onClick = { onCommit(); actions.playSongs(results.songs, index) },
                    onPlayNow = { actions.playSongs(results.songs, index) },
                    onPlayNext = { actions.playNext(song) },
                    onAddToQueue = { actions.addToQueue(song) },
                    onRadio = { actions.openRadio(song) },
                    onNotInterested = { actions.blockSuggestion(song) },
                    favorite = song.id in liked,
                    onToggleFavorite = { actions.toggleLike(song.id) },
                    onOpenDetails = { actions.openSongDetails(song) },
                )
            }
            if (all && results.songs.size > songLimit) {
                item(key = "r_songs_all") {
                    Box(Modifier.fillMaxWidth().padding(top = Dimens.sm), contentAlignment = Alignment.Center) {
                        TonalButton(
                            text = stringResource(R.string.search_show_all, results.songs.size),
                            onClick = { onFilter(SearchFilter.Songs) },
                        )
                    }
                }
            }
        }
        if (showOnline && online.stage != OnlineStage.Off && online.stage != OnlineStage.Idle) {
            item(key = "y_header") {
                SectionHeader(stringResource(R.string.search_online_header), Modifier.padding(top = Dimens.xl, bottom = Dimens.xs))
            }
            when (online.stage) {
                OnlineStage.Loading -> items(3, key = { "y_skel_$it" }) { SkeletonRow(it) }
                OnlineStage.Unreachable -> item(key = "y_unreachable") {
                    com.dopavin.ui.components.RetryHint(stringResource(R.string.search_online_unreachable), onRetry = actions::retryOnline)
                }
                else -> itemsIndexed(online.songs, key = { _, song -> "y_${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        subtitle = stringResource(R.string.online_song_subtitle, song.artist),
                        current = song.id.toString() == playback.mediaId,
                        playing = playback.isPlaying,
                        onClick = { onCommit(); actions.playSongs(online.songs, index) },
                        onPlayNow = { actions.playSongs(online.songs, index) },
                        onPlayNext = { actions.playNext(song) },
                        onAddToQueue = { actions.addToQueue(song) },
                        onRadio = { actions.openRadio(song) },
                        onNotInterested = { actions.blockSuggestion(song) },
                        favorite = song.id in liked,
                        onToggleFavorite = { actions.toggleLike(song.id) },
                        onOpenDetails = { actions.openSongDetails(song) },
                    )
                }
            }
            if (online.stage == OnlineStage.Ready && (online.hasMore || online.loadingMore)) {
                item(key = "y_more") {
                    Box(Modifier.fillMaxWidth().padding(vertical = Dimens.md), contentAlignment = Alignment.Center) {
                        if (online.loadingMore) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TonalButton(
                                text = stringResource(R.string.search_online_more),
                                onClick = actions::loadMoreOnline,
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun SkeletonRow(index: Int) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowMinHeight)
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        Box(Modifier.size(Dimens.artworkRow).clip(RoundedCornerShape(Dimens.radiusSmall)).background(colors.surfaceRaised))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            Box(Modifier.height(12.dp).width((160 - index * 20).dp).clip(CircleShape).background(colors.surfaceRaised))
            Box(Modifier.height(10.dp).width(100.dp).clip(CircleShape).background(colors.surfaceRaised))
        }
    }
}
@Composable
private fun OnlineHint(text: String) {
    AppText(
        text = text,
        style = AppTheme.type.bodySmall,
        color = AppTheme.colors.textSecondary,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
    )
}
@Composable
private fun OnlineArtistCard(hit: ArtistPick.Hit, onClick: () -> Unit) {
    val description = stringResource(R.string.search_artist_channel_desc, hit.name)
    val uri = remember(hit.avatar) { hit.avatar?.let(Uri::parse) }
    Column(
        Modifier
            .width(96.dp)
            .clickableScale(pressedScale = 0.96f, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            uri = uri,
            sizePx = ArtworkSize.Cell,
            shape = CircleShape,
            modifier = Modifier.size(96.dp),
            seed = hit.name,
        )
        Spacer(Modifier.height(Dimens.sm))
        AppText(
            text = hit.name,
            style = AppTheme.type.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
