package com.dopavin.ui.components
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
class VolumeControl(val level: Int, val max: Int, val setLevel: (Int) -> Unit)
@Composable
fun rememberSystemVolume(): VolumeControl {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    DisposableEffect(audio) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return VolumeControl(level, max) { value ->
        val clamped = value.coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        level = clamped
    }
}
