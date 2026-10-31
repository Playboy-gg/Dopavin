package com.dopavin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableLongState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Polls the player position while it is playing. Returns a state holder so only the composable that
 * reads `.longValue` recomposes. Re-reads immediately when the song or play state changes.
 */
@Composable
fun rememberPlaybackPosition(
    mediaId: String?,
    playing: Boolean,
    position: () -> Long,
    intervalMs: Long = 250L,
): MutableLongState {
    val state = remember { mutableLongStateOf(0L) }
    LaunchedEffect(mediaId, playing) {
        state.longValue = position()
        if (playing) {
            while (isActive) {
                delay(intervalMs)
                state.longValue = position()
            }
        }
    }
    return state
}


/**
 * Like [rememberPlaybackPosition] but sampled every display frame while playing, so word-by-word
 * lyric highlighting glides instead of stepping. Only readers of `.longValue` recompose.
 */
@Composable
fun rememberFramePosition(
    mediaId: String?,
    playing: Boolean,
    position: () -> Long,
): MutableLongState {
    val state = remember { mutableLongStateOf(0L) }
    LaunchedEffect(mediaId, playing) {
        state.longValue = position()
        if (playing) {
            while (isActive) {
                androidx.compose.runtime.withFrameNanos { }
                state.longValue = position()
            }
        }
    }
    return state
}
