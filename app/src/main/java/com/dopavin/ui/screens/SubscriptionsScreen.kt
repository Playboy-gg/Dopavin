package com.dopavin.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.Song
import com.dopavin.data.Subscription
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.SubsUi
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.FeedBigCard
import com.dopavin.ui.components.ScreenHeader
import com.dopavin.ui.components.SectionHeader
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/** The newest few uploads get big cards; the rest of the feed is compact rows. */
private const val BIG_CARDS = 3

/**
 * F-129. Subscribed channels (tap one for all of its uploads), then the merged latest-uploads feed:
 * the first few as big cards, the rest as rows. Pull down to check for new uploads. Unsubscribe
 * lives at the bottom so it is never hit by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    state: SubsUi,
    playback: PlaybackUi,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxSize()) {
        if (state.channels.isEmpty()) {
            ScreenHeader(onBack = actions::back, title = stringResource(R.string.subs_title))
            EmptyState(
                title = stringResource(R.string.subs_empty_title),
                body = stringResource(R.string.subs_empty_body),
                icon = R.drawable.ic_person,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = actions::refreshSubscriptions,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
            ) {
                item(key = "page_header") {
                    ScreenHeader(onBack = actions::back, title = stringResource(R.string.subs_title))
                }
                item(key = "channels_h") {
                    SectionHeader(
                        title = stringResource(R.string.subs_channels),
                        modifier = Modifier.padding(top = Dimens.sm, bottom = Dimens.sm),
                    )
                }
                item(key = "channels") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.screenEdge),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
                    ) {
                        items(state.channels, key = { it.url }) { channel ->
                            ChannelBubble(channel) {
                                actions.openArtistProfile(channel.name, channel.url)
                            }
                        }
                    }
                }
                item(key = "feed_h") {
                    SectionHeader(
                        title = stringResource(R.string.subs_latest),
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                    )
                }
                if (state.feed.isEmpty()) {
                    item(key = "feed_empty") {
                        AppText(
                            text = stringResource(if (state.loading) R.string.subs_loading else R.string.subs_no_uploads),
                            style = AppTheme.type.body,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = Dimens.screenEdge, vertical = Dimens.md),
                        )
                    }
                }
                itemsIndexed(state.feed, key = { _, song -> "feed_${song.id}" }) { index, song ->
                    val current = song.id.toString() == playback.mediaId
                    if (index < BIG_CARDS) {
                        FeedBigCard(
                            song = song,
                            current = current,
                            playing = playback.isPlaying,
                            onClick = { actions.playSongs(state.feed, index) },
                            modifier = Modifier
                                .padding(horizontal = Dimens.screenEdge)
                                .padding(bottom = Dimens.md)
                                .fillMaxWidth(),
                        )
                    } else {
                        SongRow(
                            song = song,
                            subtitle = song.artist,
                            current = current,
                            playing = playback.isPlaying,
                            onClick = { actions.playSongs(state.feed, index) },
                            onPlayNext = { actions.playNext(song) },
                            onAddToQueue = { actions.addToQueue(song) },
                            onRadio = { actions.openRadio(song) },
                            onPlayNow = { actions.playSongs(state.feed, index) },
                            favorite = song.id in liked,
                            onToggleFavorite = { actions.toggleLike(song.id) },
                            onOpenDetails = { actions.openSongDetails(song) },
                        )
                    }
                }
                item(key = "manage_h") {
                    SectionHeader(
                        title = stringResource(R.string.subs_manage),
                        modifier = Modifier.padding(top = Dimens.xl, bottom = Dimens.sm),
                    )
                }
                items(state.channels, key = { "m_" + it.url }) { channel ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            channel.name,
                            AppTheme.type.body,
                            modifier = Modifier.weight(1f),
                        )
                        TonalButton(
                            text = stringResource(R.string.subs_unsubscribe),
                            onClick = { actions.unsubscribe(channel.url) },
                        )
                    }
                }
            }
        }
    }
}

/** Round channel avatar with its name underneath. */
@Composable
private fun ChannelBubble(channel: Subscription, onClick: () -> Unit) {
    val label = stringResource(R.string.cd_channel, channel.name)
    Column(
        Modifier
            .width(76.dp)
            .clickableScale(pressedScale = 0.95f, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            uri = channel.avatar?.let(android.net.Uri::parse),
            sizePx = ArtworkSize.Cell,
            shape = CircleShape,
            modifier = Modifier.size(68.dp),
            seed = channel.name,
        )
        Spacer(Modifier.height(Dimens.xs))
        AppText(
            channel.name,
            AppTheme.type.caption,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
