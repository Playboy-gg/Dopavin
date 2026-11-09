package com.dopavin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.ArtistUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.OnlineStage
import com.dopavin.ui.PLAYLIST_QUERY_PREFIX
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.FloatingBackButton
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.components.RetryHint
import com.dopavin.ui.components.SectionHeader
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/** Songs shown before the "More" button. */
private const val PREVIEW_SONGS = 6

/**
 * F-133. One artist (YouTube channel): the whole picture as a full-width square, a Subscribe button,
 * Play / Shuffle, then every song found and the albums as a row of cards.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ArtistProfileScreen(
    state: ArtistUi,
    subscribed: Boolean,
    playback: PlaybackUi,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    var expanded by rememberSaveable(state.url, state.name) { mutableStateOf(false) }
    val shownSongs = if (expanded) state.songs else state.songs.take(PREVIEW_SONGS)
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
    ) {
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                // The photo itself fades out (alpha mask), so its lower part melts into whatever the page
                // really has behind it (ambient tint, solid canvas, light or dark) instead of ending on a painted edge.
                ArtworkImage(
                    uri = state.avatar?.let(android.net.Uri::parse),
                    sizePx = ArtworkSize.Player,
                    shape = RectangleShape,
                    modifier = Modifier
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
                        }
                        .semantics { contentDescription = state.name },
                    seed = state.name,
                )
                // Name and the two actions sit on the photo; the buttons are liquid glass over it.
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenEdge)
                        .padding(bottom = Dimens.md),
                ) {
                    AppText(
                        text = state.name,
                        style = AppTheme.type.display,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = Dimens.md),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                    ) {
                        TonalButton(
                            text = stringResource(if (subscribed) R.string.artist_subscribed else R.string.artist_subscribe),
                            icon = if (subscribed) R.drawable.ic_check else null,
                            accent = !subscribed,
                            onClick = { actions.toggleChannelSubscription(state.url, state.name) },
                        )
                        if (state.songs.isNotEmpty()) {
                            TonalButton(
                                text = stringResource(R.string.artist_shuffle),
                                icon = R.drawable.ic_shuffle,
                                onClick = { actions.playCollection(state.songs, true) },
                            )
                        }
                        TonalButton(
                            text = stringResource(R.string.menu_share),
                            icon = R.drawable.ic_share,
                            onClick = { actions.shareArtist(state.name, state.url) },
                        )
                    }
                }
            }
        }
        when (state.stage) {
            OnlineStage.Loading, OnlineStage.Idle, OnlineStage.Off -> item(key = "loading") {
                Hint(stringResource(R.string.search_online_loading))
            }
            OnlineStage.Unreachable -> item(key = "failed") {
                RetryHint(stringResource(R.string.artist_load_failed), onRetry = actions::retryOnline)
            }
            OnlineStage.Ready -> {
                if (state.songs.isNotEmpty()) {
                    item(key = "songs_h") {
                        SectionHeader(
                            title = stringResource(R.string.artist_songs),
                            modifier = Modifier.padding(top = Dimens.sm, bottom = Dimens.xs),
                        )
                    }
                    itemsIndexed(shownSongs, key = { _, song -> "s_${song.id}" }) { index, song ->
                        SongRow(
                            song = song,
                            subtitle = song.artist,
                            current = song.id.toString() == playback.mediaId,
                            playing = playback.isPlaying,
                            onClick = { actions.playSongs(state.songs, index) },
                            onPlayNext = { actions.playNext(song) },
                            onAddToQueue = { actions.addToQueue(song) },
                            onRadio = { actions.openRadio(song) },
                            onPlayNow = { actions.playSongs(state.songs, index) },
                            favorite = song.id in liked,
                            onToggleFavorite = { actions.toggleLike(song.id) },
                            onOpenDetails = { actions.openSongDetails(song) },
                        )
                    }
                }
                if (!expanded && state.songs.size > PREVIEW_SONGS) {
                    item(key = "songs_more") {
                        Box(Modifier.fillMaxWidth().padding(top = Dimens.xs), contentAlignment = Alignment.Center) {
                            TonalButton(
                                text = stringResource(R.string.artist_more),
                                icon = R.drawable.ic_expand_more,
                                onClick = { expanded = true },
                            )
                        }
                    }
                }
                if (state.albums.isNotEmpty()) {
                    item(key = "albums_h") {
                        SectionHeader(
                            title = stringResource(R.string.artist_albums),
                            modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                        )
                    }
                    item(key = "albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                        ) {
                            items(state.albums, key = { it.url }) { album ->
                                val label = stringResource(R.string.cd_album_card, album.name)
                                Column(
                                    Modifier
                                        .width(150.dp)
                                        .clickableScale(pressedScale = 0.96f) {
                                            actions.openOnlineList(album.name, PLAYLIST_QUERY_PREFIX + album.url)
                                        }
                                        .semantics(mergeDescendants = true) { contentDescription = label },
                                ) {
                                    ArtworkImage(
                                        uri = album.thumb?.let(android.net.Uri::parse),
                                        sizePx = ArtworkSize.Cell,
                                        shape = RoundedCornerShape(Dimens.radiusLarge),
                                        modifier = Modifier.width(150.dp).aspectRatio(1f),
                                        seed = album.name,
                                    )
                                    Spacer(Modifier.height(Dimens.xs))
                                    AppText(
                                        text = album.name,
                                        style = AppTheme.type.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    FloatingBackButton(actions::back)
    }
}

@Composable
private fun Hint(text: String) {
    AppText(
        text = text,
        style = AppTheme.type.body,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
    )
}
