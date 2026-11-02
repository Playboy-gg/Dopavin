package com.dopavin.ui.screens
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.Song
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.MusicActions
import com.dopavin.ui.components.LocalBottomInset
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.FloatingBackButton
import com.dopavin.ui.components.ArtworkImage
import com.dopavin.ui.components.EmptyState
import com.dopavin.ui.components.IconAction
import com.dopavin.ui.components.PlayPauseButton
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.components.TonalButton
import com.dopavin.ui.components.rememberArtworkTint
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
data class CollectionUi(
    val title: String,
    val subtitle: String,
    val artworkUri: Uri,
    val songs: List<Song>,
    val isAlbum: Boolean,
)
@Composable
fun CollectionScreen(
    collection: CollectionUi?,
    playback: PlaybackUi,
    liked: Set<Long>,
    actions: MusicActions,
) {
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    if (collection == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconAction(
                icon = R.drawable.ic_back,
                description = stringResource(R.string.cd_back),
                onClick = actions::back,
            )
            EmptyState(
                title = stringResource(R.string.collection_unavailable),
                actionLabel = stringResource(R.string.back_to_library),
                onAction = actions::back,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val barAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(motion.medium),
        label = "collectionBar",
    )
    val tint = rememberArtworkTint(collection.artworkUri)
    val headerTop by animateColorAsState(
        targetValue = tint?.let { lerp(colors.canvas, it, if (colors.isDark) 0.7f else 0.45f) }
            ?: lerp(colors.canvas, colors.surfaceHigh, 0.5f),
        animationSpec = tween(motion.ambient),
        label = "collectionTint",
    )
    val currentId = playback.mediaId
    val isThisPlaying = currentId != null && collection.songs.any { it.id.toString() == currentId }
    val playingNow = isThisPlaying && playback.isPlaying
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Dimens.xl + LocalBottomInset.current),
        ) {
            item(key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(headerTop, colors.canvas)))
                        .statusBarsPadding()
                        .padding(top = Dimens.topBarHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val artShape: Shape = RoundedCornerShape(Dimens.radiusLarge)
                    ArtworkImage(
                        uri = collection.artworkUri,
                        sizePx = if (collection.isAlbum) ArtworkSize.Large else ArtworkSize.Player,
                        shape = artShape,
                        modifier = (
                            if (collection.isAlbum) {
                                Modifier.padding(top = Dimens.sm).size(Dimens.artworkHeader)
                            } else {
                                Modifier
                                    .padding(top = Dimens.sm)
                                    .padding(horizontal = Dimens.screenEdge)
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            }
                            ).shadow(24.dp, artShape),
                        seed = collection.title,
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.screenEdge)
                            .padding(top = Dimens.xl),
                    ) {
                        AppText(
                            text = collection.title,
                            style = AppTheme.type.headline,
                            maxLines = 2,
                            modifier = Modifier.semantics { heading() },
                        )
                        AppText(
                            text = collection.subtitle,
                            style = AppTheme.type.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(Dimens.lg))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TonalButton(
                                text = stringResource(R.string.button_shuffle),
                                icon = R.drawable.ic_shuffle,
                                onClick = { actions.playCollection(collection.songs, true) },
                            )
                            Spacer(Modifier.weight(1f))
                            PlayPauseButton(
                                playing = playingNow,
                                buffering = false,
                                onClick = {
                                    if (isThisPlaying) {
                                        actions.togglePlayPause()
                                    } else {
                                        actions.playCollection(collection.songs, false)
                                    }
                                },
                                size = Dimens.playButtonSmall,
                            )
                        }
                        Spacer(Modifier.height(Dimens.sm))
                    }
                }
            }
            itemsIndexed(collection.songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    subtitle = if (collection.isAlbum) song.artist else song.album,
                    current = song.id.toString() == currentId,
                    playing = playback.isPlaying,
                    onClick = { actions.playSongs(collection.songs, index) },
                    onPlayNow = { actions.playSongs(collection.songs, index) },
                    onPlayNext = { actions.playNext(song) },
                    onAddToQueue = { actions.addToQueue(song) },
                    onRadio = { actions.openRadio(song) },
                    favorite = song.id in liked,
                    onToggleFavorite = { actions.toggleLike(song.id) },
                    onOpenDetails = { actions.openSongDetails(song) },
                    trackNumber = if (collection.isAlbum) (song.track.takeIf { it > 0 } ?: (index + 1)) else null,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.canvas.copy(alpha = barAlpha))
                .statusBarsPadding()
                .height(Dimens.topBarHeight)
                .padding(horizontal = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingBackButton(actions::back)
            Spacer(Modifier.size(Dimens.touchTarget))
            AppText(
                text = collection.title,
                style = AppTheme.type.bodyStrong,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.sm)
                    .alpha(barAlpha),
            )
        }
    }
}
