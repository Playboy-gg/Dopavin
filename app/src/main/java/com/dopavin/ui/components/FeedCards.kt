package com.dopavin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dopavin.R
import com.dopavin.data.ArtworkSize
import com.dopavin.data.Song
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens

/**
 * The big feed card: a wide 16:9 thumbnail with the title and artist laid over a dark gradient and
 * the duration in a corner. Used for Subscriptions and Trending; small square cards stay for the
 * secondary shelves.
 */
@Composable
fun FeedBigCard(
    song: Song,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusLarge)
    val duration = formatTime(song.durationMs)
    val description = stringResource(R.string.cd_feed_card, song.title, song.artist, duration)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .shadow(8.dp, shape)
            .clip(shape)
            .focusRing(shape)
            .clickableScale(pressedScale = 0.97f, onLongClick = onLongPress, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        ArtworkImage(
            uri = song.artworkUri,
            sizePx = ArtworkSize.Large,
            shape = shape,
            modifier = Modifier.fillMaxSize(),
            seed = song.artist,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Dimens.md),
        ) {
            AppText(
                song.title,
                AppTheme.type.bodyStrong,
                color = if (current) colors.accent else Color.White,
                maxLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) {
                    EqualizerBars(
                        playing = playing,
                        color = colors.accent,
                        size = 14.dp,
                        modifier = Modifier.padding(end = Dimens.xs),
                    )
                }
                AppText(
                    song.artist,
                    AppTheme.type.caption,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AppText(
            text = duration,
            style = AppTheme.type.caption,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Dimens.sm)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = Dimens.sm, vertical = 2.dp),
        )
    }
}
