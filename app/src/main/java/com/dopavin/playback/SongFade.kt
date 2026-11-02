package com.dopavin.playback
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import com.dopavin.data.SettingsStore
class SongFade(context: Context, private val player: Player) {
    private val settings = SettingsStore(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var fadeMs = 0L
    private var readAt = 0L
    private var applied = false
    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, TICK_MS)
        }
    }
    fun start() = handler.post(tick)
    fun stop() {
        handler.removeCallbacks(tick)
        release()
    }
    private fun step() {
        val now = SystemClock.elapsedRealtime()
        if (now - readAt > 2_000L) {
            fadeMs = settings.load().fadeSeconds * 1_000L
            readAt = now
        }
        if (fadeMs <= 0L || !player.isPlaying || PlaybackRuntime.sleep.value.mode == SleepMode.Timer) {
            release()
            return
        }
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration < fadeMs * 4) {
            release()
            return
        }
        val position = player.currentPosition
        val remaining = duration - position
        val fadingOut = remaining < fadeMs && player.hasNextMediaItem() && player.repeatMode != Player.REPEAT_MODE_ONE
        val half = fadeMs / 2
        val volume = when {
            fadingOut -> remaining.toFloat() / fadeMs
            position < half -> position.toFloat() / half
            else -> 1f
        }.coerceIn(0f, 1f)
        if (volume < 1f || applied) {
            player.volume = volume
            applied = volume < 1f
        }
    }
    private fun release() {
        if (applied) {
            player.volume = 1f
            applied = false
        }
    }
    private companion object {
        const val TICK_MS = 120L
    }
}
