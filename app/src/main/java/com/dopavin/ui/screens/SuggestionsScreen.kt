package com.dopavin.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dopavin.R
import com.dopavin.data.Mood
import com.dopavin.data.reasonRes
import com.dopavin.playback.PlaybackUi
import com.dopavin.ui.OnlineStage
import com.dopavin.ui.MusicActions
import com.dopavin.ui.SuggestedSong
import com.dopavin.ui.SuggestionsUi
import com.dopavin.ui.components.AppText
import com.dopavin.ui.components.SongRow
import com.dopavin.ui.components.RetryHint
import com.dopavin.ui.components.ScreenHeader
import com.dopavin.ui.components.clickableScale
import com.dopavin.ui.theme.AppTheme
import com.dopavin.ui.theme.Dimens
internal fun moodLabelRes(mood: Mood): Int = when (mood) {
    Mood.Chill -> R.string.mood_chill
    Mood.Happy -> R.string.mood_happy
    Mood.Energetic -> R.string.mood_energetic
    Mood.Focus -> R.string.mood_focus
    Mood.Reflective -> R.string.mood_reflective
    Mood.Romantic -> R.string.mood_romantic
}
@Composable
fun SuggestionsScreen(
    title: String,
    subtitle: String,
    state: SuggestionsUi,
    playback: PlaybackUi,
    liked: Set<Long>,
    actions: MusicActions,
) {
    Column(Modifier.fillMaxSize()) {
        when (state.stage) {
            OnlineStage.Loading, OnlineStage.Unreachable -> {
                ScreenHeader(onBack = actions::back, title = title, subtitle = subtitle)
                if (state.stage == OnlineStage.Unreachable) {
                    RetryHint(stringResource(R.string.suggestions_unreachable), onRetry = actions::retryOnline)
                } else AppText(
                    text = stringResource(
                        if (state.stage == OnlineStage.Loading) {
                            R.string.search_online_loading
                        } else {
                            R.string.suggestions_unreachable
                        },
                    ),
                    style = AppTheme.type.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenEdge, vertical = Dimens.lg),
                )
            }
            else -> {
                if (state.list.isEmpty()) {
                    ScreenHeader(onBack = actions::back, title = title, subtitle = subtitle)
                    RetryHint(stringResource(R.string.suggestions_unreachable), onRetry = actions::retryOnline)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Dimens.xl),
                    ) {
                        item(key = "page_header") {
                            ScreenHeader(onBack = actions::back, title = title, subtitle = subtitle)
                        }
                        item(key = "action_pair") { ActionPair(actions) }
                        itemsIndexed(state.list, key = { _, item -> item.song.id }) { index, item ->
                            SongRow(
                                song = item.song,
                                subtitle = stringResource(
                                    R.string.song_subtitle,
                                    stringResource(item.reason.reasonRes()),
                                    item.song.artist,
                                ),
                                current = item.song.id.toString() == playback.mediaId,
                                playing = playback.isPlaying,
                                onClick = { actions.playSongs(state.list.map { it.song }, index) },
                                onPlayNow = { actions.playSongs(state.list.map { it.song }, index) },
                                onPlayNext = { actions.playNext(item.song) },
                                onAddToQueue = { actions.addToQueue(item.song) },
                                onRadio = { actions.openRadio(item.song) },
                                onNotInterested = { actions.blockSuggestion(item.song) },
                                favorite = item.song.id in liked,
                                onToggleFavorite = { actions.toggleLike(item.song.id) },
                                onOpenDetails = { actions.openSongDetails(item.song) },
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ActionPair(actions: MusicActions) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenEdge, vertical = Dimens.sm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(colors.accent)
                .clickableScale(onClick = actions::playSuggestions)
                .padding(vertical = Dimens.sm),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                stringResource(R.string.suggestions_play_all),
                AppTheme.type.label,
                color = colors.onAccent,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(colors.textPrimary.copy(alpha = 0.10f))
                .clickableScale(onClick = actions::appendSuggestions)
                .padding(vertical = Dimens.sm),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                stringResource(R.string.suggestions_append),
                AppTheme.type.label,
                color = colors.textPrimary,
            )
        }
    }
}
