package com.dopavin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.dopavin.ui.components.TonalButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dopavin.R
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.OnlineListUi
import com.dopavin.ui.OnlineStage
import com.dopavin.ui.MusicActions
import com.dopavin.ui.Route
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.FloatingBackButton
import com.dopavin.ui.components.CoverHero
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.components.RetryHint
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * S-10. One Home shelf opened in full — a category or an artist's YouTube songs, played top-down.
 * Quiet Loading / Unreachable lines like the Search screen; no tints, no tabs, no local mixing.
 */
@Composable
fun OnlineListScreen(
    route: Route.OnlineList,
    playback: PlaybackUi,
    state: OnlineListUi,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val title = state.title.ifBlank { route.title }
    Box(Modifier.fillMaxSize()) {
        when (state.stage) {
            OnlineStage.Ready -> {
                val songs = state.songs
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
                ) {
                    item(key = "hero") {
                        CoverHero(
                            title = title,
                            subtitle = stringResource(R.string.online_list_subtitle),
                            cover = { PlaylistCover(title, state.cover, songs) },
                            actions = {
                                if (songs.isNotEmpty()) {
                                    Row(
                                        Modifier.padding(top = Dimens.md),
                                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TonalButton(
                                            text = stringResource(R.string.radio_play),
                                            icon = R.drawable.ic_play,
                                            accent = true,
                                            onClick = { actions.playCollection(songs, false) },
                                        )
                                        TonalButton(
                                            text = stringResource(R.string.radio_shuffle),
                                            icon = R.drawable.ic_shuffle,
                                            onClick = { actions.playCollection(songs, true) },
                                        )
                                        com.dopavin.ui.components.CollectionMoreButton(
                                            title = title,
                                            actions = listOf(
                                                com.dopavin.ui.components.SheetAction(stringResource(R.string.radio_save), R.drawable.ic_playlist_add) {
                                                    actions.createPlaylist(title, songs, state.cover)
                                                },
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
                            },
                        )
                    }
                    if (songs.isEmpty()) {
                        item(key = "empty") { OnlineListHint(stringResource(R.string.online_list_empty)) }
                    }
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            subtitle = stringResource(R.string.online_song_subtitle, song.artist),
                            current = song.id.toString() == playback.mediaId,
                            playing = playback.isPlaying,
                            onClick = { actions.playSongs(songs, index) },
                            onPlayNow = { actions.playSongs(songs, index) },
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
            }
            else -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = Dimens.topBarHeight)) {
                AppText(
                    text = title,
                    style = AppTheme.type.headline,
                    modifier = Modifier.padding(horizontal = Dimens.screenEdge).semantics { heading() },
                )
                if (state.stage == OnlineStage.Loading) {
                    OnlineListHint(stringResource(R.string.search_online_loading))
                } else {
                    RetryHint(stringResource(R.string.search_online_unreachable), onRetry = actions::retryOnline)
                }
            }
        }
        // Fixed over the page; the root draws it as real glass.
        FloatingBackButton(actions::back)
    }
}

@Composable
private fun OnlineListHint(text: String) {
    AppText(
        text = text,
        style = AppTheme.type.bodySmall,
        color = AppTheme.colors.textSecondary,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
    )
}
