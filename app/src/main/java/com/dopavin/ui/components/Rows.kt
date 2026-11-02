package com.dopavin.ui.components
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.Album
import com.dopavin.data.Artist
import com.dopavin.data.ArtworkSize
import com.dopavin.data.Song
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
@Composable
fun SongRow(
    song: Song,
    subtitle: String,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null,
    onRadio: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onPlayNow: (() -> Unit)? = null,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    selected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val duration = formatTime(song.durationMs)
    val nowPlaying = stringResource(R.string.state_now_playing)
    val description = stringResource(R.string.row_description, song.title, song.artist, duration)
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.rowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.rowMinHeight)
                    .focusRing(RectangleShape)
                    .clickableScale(pressedScale = 0.985f, onLongClick = onLongPress ?: { menuOpen = true }, onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        if (current) stateDescription = nowPlaying
                        if (selected) this.selected = true
                    }
                    .padding(start = Dimens.screenEdge, top = Dimens.sm, bottom = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(Dimens.artworkRow)) {
                    if (trackNumber == null) {
                        ArtworkImage(
                            uri = song.artworkUri,
                            sizePx = ArtworkSize.Row,
                            shape = RoundedCornerShape(Dimens.radiusSmall),
                            modifier = Modifier.fillMaxSize(),
                            seed = song.album,
                        )
                        if (current) {
                            MenuScrimOverlay(shape = RoundedCornerShape(Dimens.radiusSmall)) {
                                EqualizerBars(playing = playing, color = Color.White)
                            }
                        }
                        if (selected) {
                            MenuScrimOverlay(fill = colors.accent.copy(alpha = 0.9f)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = colors.onAccent,
                                    modifier = Modifier.size(Dimens.iconControl),
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            if (current) {
                                EqualizerBars(playing = playing, color = colors.accent)
                            } else {
                                AppText(
                                    text = if (trackNumber > 0) trackNumber.toString() else "–",
                                    style = type.numeric,
                                    color = if (selected) colors.accent else colors.textSecondary,
                                )
                            }
                        }
                        if (selected) {
                            MenuScrimOverlay(fill = colors.accent.copy(alpha = 0.28f), shape = RectangleShape) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(Dimens.iconControl),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = song.title,
                        style = type.bodyStrong,
                        color = if (current) colors.accent else colors.textPrimary,
                    )
                    val downloads = LocalDownloads.current
                    val saved = song.isDownloaded || (song.videoId?.let { downloads.isSaved(it) } == true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (saved) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.cd_downloaded),
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(Dimens.xs))
                        }
                        AppText(text = subtitle, style = type.bodySmall, color = colors.textSecondary)
                    }
                }
                Spacer(Modifier.width(Dimens.sm))
                AppText(
                    text = duration,
                    style = type.numeric,
                    color = colors.textSecondary,
                    overflow = TextOverflow.Clip,
                )
            }
            SongActionsOverflow(
                songTitle = song.title,
                onOpen = { menuOpen = true },
            )
        }
        SongActionsMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            song = song,
            onPlayNow = onPlayNow,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onRadio = onRadio,
            onNotInterested = onNotInterested,
            favorite = favorite,
            onToggleFavorite = onToggleFavorite,
            onOpenDetails = onOpenDetails,
            onRemove = onRemove,
        )
    }
}
@Composable
private fun MenuScrimOverlay(
    shape: Shape = RectangleShape,
    fill: Color = Color.Black.copy(alpha = 0.55f),
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
@Composable
fun SongActionsOverflow(
    songTitle: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconAction(
        icon = R.drawable.ic_more,
        description = stringResource(R.string.cd_more_options, songTitle),
        onClick = onOpen,
        modifier = modifier,
        tint = AppTheme.colors.textSecondary,
    )
}
@Composable
fun SongActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: Song,
    onPlayNow: (() -> Unit)?,
    onPlayNext: () -> Unit,
    onAddToQueue: (() -> Unit)?,
    onRadio: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val actions = LocalActions.current
    val downloads = LocalDownloads.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val list = buildList {
        if (onPlayNow != null) add(SheetAction(stringResource(R.string.menu_play_now), R.drawable.ic_play, onClick = onPlayNow))
        if (onMoveUp != null) add(SheetAction(stringResource(R.string.queue_move_up), onClick = onMoveUp))
        if (onMoveDown != null) add(SheetAction(stringResource(R.string.queue_move_down), onClick = onMoveDown))
        add(SheetAction(stringResource(R.string.menu_play_next), R.drawable.ic_queue, onClick = onPlayNext))
        if (onAddToQueue != null) add(SheetAction(stringResource(R.string.menu_add_queue), R.drawable.ic_playlist_add, onClick = onAddToQueue))
        if (onToggleFavorite != null && favorite != null) {
            add(
                SheetAction(
                    stringResource(if (favorite) R.string.menu_fav_remove else R.string.menu_fav_add),
                    if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    onClick = onToggleFavorite,
                ),
            )
        }
        if (actions != null && song.videoId != null) {
            val videoId = song.videoId
            when {
                downloads.isSaved(videoId) -> {
                    add(SheetAction(stringResource(R.string.menu_save_device), R.drawable.ic_save) { actions.saveToDevice(song) })
                    add(SheetAction(stringResource(R.string.menu_delete_download), R.drawable.ic_delete, destructive = true) { actions.deleteDownload(song) })
                }
                downloads.item(videoId)?.status == com.dopavin.data.DownloadStatus.Failed ->
                    add(SheetAction(stringResource(R.string.menu_retry_download), R.drawable.ic_download) { actions.downloadSong(song) })
                downloads.item(videoId) != null ->
                    add(SheetAction(stringResource(R.string.menu_cancel_download), R.drawable.ic_close) { actions.cancelDownload(videoId) })
                else ->
                    add(SheetAction(stringResource(R.string.menu_download), R.drawable.ic_download) { actions.downloadSong(song) })
            }
        }
        if (actions != null && song.artist.isNotBlank()) {
            add(SheetAction(stringResource(R.string.menu_go_artist), R.drawable.ic_person) { actions.openArtistOf(song) })
        }
        add(SheetAction(stringResource(R.string.menu_share), R.drawable.ic_share) { shareSong(context, song) })
        if (onRadio != null) add(SheetAction(stringResource(R.string.menu_song_radio), R.drawable.ic_music_note, onClick = onRadio))
        if (onNotInterested != null) add(SheetAction(stringResource(R.string.menu_not_interested), R.drawable.ic_close, onClick = onNotInterested))
        if (onOpenDetails != null) add(SheetAction(stringResource(R.string.menu_details), R.drawable.ic_info, onClick = onOpenDetails))
        if (onRemove != null) add(SheetAction(stringResource(R.string.menu_remove_playlist), R.drawable.ic_delete, destructive = true, onClick = onRemove))
    }
    ShowSheetEffect(
        expanded = expanded,
        onDismiss = onDismiss,
        request = SheetRequest(
            title = song.title,
            subtitle = song.artist,
            artwork = song.artworkUri,
            actions = list,
        ),
    )
}
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = title,
            style = AppTheme.type.title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .heightIn(min = Dimens.touchTarget)
                    .clip(CircleShape)
                    .focusRing(CircleShape)
                    .clickableScale(onClick = onAction)
                    .padding(horizontal = Dimens.sm),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(actionLabel, AppTheme.type.label, color = AppTheme.colors.accent)
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(Dimens.iconInline),
                    )
                }
            }
        }
    }
}
@Composable
fun AlbumCard(
    album: Album,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val artist = album.artist ?: stringResource(R.string.various_artists)
    val nowPlaying = stringResource(R.string.state_now_playing)
    val description = stringResource(
        R.string.row_description,
        album.title,
        artist,
        songCountText(album.songs.size),
    )
    val shape = RoundedCornerShape(Dimens.radiusMedium)
    Column(
        modifier
            .clickableScale(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .focusRing(shape)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (current) stateDescription = nowPlaying
            },
    ) {
        ArtworkImage(
            uri = album.artworkUri,
            sizePx = ArtworkSize.Cell,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            seed = album.title,
        )
        Spacer(Modifier.height(Dimens.sm))
        AppText(
            text = album.title,
            style = type.bodyStrong,
            color = if (current) colors.accent else colors.textPrimary,
        )
        AppText(text = artist, style = type.bodySmall, color = colors.textSecondary)
    }
}
@Composable
fun QuickTile(
    title: String,
    artwork: Uri?,
    seed: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusMedium)
    val nowPlaying = stringResource(R.string.state_now_playing)
    Row(
        modifier
            .height(Dimens.artworkQuick)
            .clickableScale(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(colors.surfaceRaised)
            .focusRing(shape)
            .semantics(mergeDescendants = true) {
                contentDescription = title
                if (current) stateDescription = nowPlaying
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            uri = artwork,
            sizePx = ArtworkSize.Row,
            shape = RectangleShape,
            modifier = Modifier.size(Dimens.artworkQuick),
            seed = seed,
        )
        AppText(
            text = title,
            style = AppTheme.type.label,
            color = if (current) colors.accent else colors.textPrimary,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.md),
        )
    }
}
@Composable
fun ArtistAvatarCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp = Dimens.avatarCard,
) {
    val count = songCountText(artist.songs.size)
    val description = stringResource(R.string.artist_subtitle, artist.name, count)
    Column(
        modifier
            .width(avatarSize)
            .clickableScale(pressedScale = 0.96f, onClick = onClick)
            .clip(RoundedCornerShape(Dimens.radiusMedium))
            .focusRing(RoundedCornerShape(Dimens.radiusMedium))
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            uri = artist.songs.first().uri,
            sizePx = ArtworkSize.Cell,
            shape = CircleShape,
            modifier = Modifier.size(avatarSize),
            seed = artist.name,
        )
        Spacer(Modifier.height(Dimens.sm))
        AppText(
            text = artist.name,
            style = AppTheme.type.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
@Composable
fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val count = songCountText(artist.songs.size)
    val description = stringResource(R.string.artist_subtitle, artist.name, count)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowMinHeight)
            .focusRing(RectangleShape)
            .clickableScale(pressedScale = 0.985f, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            uri = artist.songs.first().uri,
            sizePx = ArtworkSize.Row,
            shape = CircleShape,
            modifier = Modifier.size(Dimens.artworkRow),
            seed = artist.name,
        )
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            AppText(text = artist.name, style = type.bodyStrong)
            AppText(text = count, style = type.bodySmall, color = colors.textSecondary)
        }
    }
}
