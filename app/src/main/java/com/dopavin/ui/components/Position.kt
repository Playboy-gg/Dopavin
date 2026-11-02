package com.dopavin.ui.components
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableLongState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
