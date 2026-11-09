package com.dopavin.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dopavin.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of the audio effects for the Sound screen. Levels are in millibels (100 mB = 1 dB). */
data class EffectsState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bandFreqsHz: List<Int> = emptyList(),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val levelsMb: List<Int> = emptyList(),
    val presets: List<String> = emptyList(),
    /** Index into [presets], or -1 for custom. */
    val preset: Int = -1,
    val bassSupported: Boolean = false,
    /** 0..1000 */
    val bassStrength: Int = 0,
    /** 0..1000 mB */
    val loudnessMb: Int = 0,
    /** Playback speed, 0.5..2.0. */
    val speed: Float = 1f,
)

enum class SleepMode { Off, Timer, EndOfTrack }

data class SleepState(val mode: SleepMode = SleepMode.Off, val remainingMs: Long = 0L)

/**
 * Service-side features the UI controls: audio effects, sleep timer, skip silence.
 * The service and the UI live in one process, so a plain object is enough; everything runs on the
 * main thread. The service attaches its player here and detaches in onDestroy.
 */
@OptIn(UnstableApi::class)
object PlaybackRuntime {

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var store: SettingsStore? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null

    private var currentSpeed = 1f
    private var keepPitch = true
    private var sleepFade = true

    private val _effects = MutableStateFlow(EffectsState())
    val effects: StateFlow<EffectsState> = _effects.asStateFlow()

    private val _sleep = MutableStateFlow(SleepState())
    val sleep: StateFlow<SleepState> = _sleep.asStateFlow()

    private var sleepEndsAt = 0L
    private const val FADE_MS = 10_000L

    private val tick = object : Runnable {
        override fun run() {
            val left = sleepEndsAt - SystemClock.elapsedRealtime()
            if (left <= 0L) {
                player?.pause()
                cancelSleep() // also restores the volume
            } else {
                _sleep.value = SleepState(SleepMode.Timer, left)
                val fading = sleepFade && left <= FADE_MS
                if (fading) player?.volume = (left.toFloat() / FADE_MS).coerceIn(0f, 1f)
                handler.postDelayed(this, if (fading) 250L else 1000L)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            rebuildEffects(audioSessionId)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // The "end of this song" timer has fired: ExoPlayer paused at the end of the item.
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _sleep.value.mode == SleepMode.EndOfTrack
            ) {
                cancelSleep()
            }
        }
    }

    fun attach(context: Context, exo: ExoPlayer) {
        player = exo
        val settings = SettingsStore(context)
        store = settings
        val saved = settings.load()
        exo.setSkipSilenceEnabled(saved.skipSilence)
        exo.setHandleAudioBecomingNoisy(saved.pauseOnUnplug)
        keepPitch = saved.keepPitch
        sleepFade = saved.sleepFade
        currentSpeed = settings.speedPercent / 100f
        exo.playbackParameters = parametersFor(currentSpeed)
        exo.addListener(listener)
        rebuildEffects(exo.audioSessionId)
    }

    fun detach() {
        cancelSleep()
        player?.removeListener(listener)
        releaseEffects()
        player = null
        store = null
    }

    // ---- Pause on unplug / speed ----

    fun applyPauseOnUnplug(enabled: Boolean) {
        player?.setHandleAudioBecomingNoisy(enabled)
    }

    fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.5f, 2f)
        currentSpeed = value
        store?.speedPercent = Math.round(value * 100)
        player?.playbackParameters = parametersFor(value)
        _effects.value = _effects.value.copy(speed = value)
    }

    /** Pitch stays at 1 (time-stretch) or follows the speed, per the "keep pitch" setting. */
    private fun parametersFor(speed: Float) = PlaybackParameters(speed, if (keepPitch) 1f else speed)

    fun applyKeepPitch(enabled: Boolean) {
        keepPitch = enabled
        player?.playbackParameters = parametersFor(currentSpeed)
    }

    fun applySleepFade(enabled: Boolean) {
        sleepFade = enabled
        if (!enabled) player?.volume = 1f
    }

    // ---- Skip silence ----

    fun applySkipSilence(enabled: Boolean) {
        player?.setSkipSilenceEnabled(enabled)
    }

    // ---- Sleep timer ----

    fun startSleepTimer(minutes: Int) {
        cancelSleep()
        sleepEndsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        tick.run()
    }

    fun sleepAtEndOfTrack() {
        cancelSleep()
        player?.setPauseAtEndOfMediaItems(true)
        _sleep.value = SleepState(SleepMode.EndOfTrack, 0L)
    }

    fun cancelSleep() {
        handler.removeCallbacks(tick)
        player?.volume = 1f
        player?.setPauseAtEndOfMediaItems(false)
        _sleep.value = SleepState()
    }

    // ---- Effects ----

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        loudness = null
        _effects.value = EffectsState(speed = currentSpeed)
    }

    private fun rebuildEffects(sessionId: Int) {
        releaseEffects()
        val saved = store ?: return
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            val eq = Equalizer(0, sessionId)
            val bands = eq.numberOfBands.toInt()
            val presetIndex = saved.eqPreset
            val savedLevels = saved.eqLevels
            if (presetIndex in 0 until eq.numberOfPresets) {
                eq.usePreset(presetIndex.toShort())
            } else if (savedLevels != null && savedLevels.size == bands) {
                val range = eq.bandLevelRange
                savedLevels.forEachIndexed { band, level ->
                    eq.setBandLevel(band.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
            }
            eq.setEnabled(saved.eqEnabled)
            equalizer = eq
        } catch (e: Exception) {
            equalizer = null
        }
        try {
            val boost = BassBoost(0, sessionId)
            if (boost.strengthSupported) {
                boost.setStrength(saved.bassStrength.coerceIn(0, 1000).toShort())
                boost.setEnabled(saved.bassStrength > 0)
            }
            bassBoost = boost
        } catch (e: Exception) {
            bassBoost = null
        }
        try {
            val enhancer = LoudnessEnhancer(sessionId)
            enhancer.setTargetGain(saved.loudnessMb.coerceIn(0, 1000))
            enhancer.setEnabled(saved.loudnessMb > 0)
            loudness = enhancer
        } catch (e: Exception) {
            loudness = null
        }
        publish()
    }

    private fun publish() {
        val eq = equalizer
        if (eq == null) {
            _effects.value = EffectsState(speed = currentSpeed)
            return
        }
        val bands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        _effects.value = EffectsState(
            available = true,
            enabled = eq.getEnabled(),
            bandFreqsHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 },
            minLevelMb = range[0].toInt(),
            maxLevelMb = range[1].toInt(),
            levelsMb = (0 until bands).map { eq.getBandLevel(it.toShort()).toInt() },
            presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
            preset = store?.eqPreset ?: -1,
            bassSupported = bassBoost?.strengthSupported == true,
            bassStrength = store?.bassStrength ?: 0,
            loudnessMb = store?.loudnessMb ?: 0,
            speed = currentSpeed,
        )
    }

    fun setEqEnabled(enabled: Boolean) {
        val eq = equalizer ?: return
        runCatching { eq.setEnabled(enabled) }
        store?.eqEnabled = enabled
        publish()
    }

    fun setEqBand(band: Int, levelMb: Int) {
        val eq = equalizer ?: return
        runCatching {
            val range = eq.bandLevelRange
            eq.setBandLevel(band.toShort(), levelMb.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
        }
        store?.eqPreset = -1
        store?.eqLevels = (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()).toInt() }
        publish()
    }

    fun setEqPreset(index: Int) {
        val eq = equalizer ?: return
        runCatching { eq.usePreset(index.toShort()) }
        store?.eqPreset = index
        store?.eqLevels = (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()).toInt() }
        publish()
    }

    fun setBass(strength: Int) {
        val boost = bassBoost ?: return
        val value = strength.coerceIn(0, 1000)
        runCatching {
            boost.setStrength(value.toShort())
            boost.setEnabled(value > 0)
        }
        store?.bassStrength = value
        publish()
    }

    fun setLoudness(gainMb: Int) {
        val enhancer = loudness ?: return
        val value = gainMb.coerceIn(0, 1000)
        runCatching {
            enhancer.setTargetGain(value)
            enhancer.setEnabled(value > 0)
        }
        store?.loudnessMb = value
        publish()
    }

    fun resetEffects() {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                for (band in 0 until eq.numberOfBands.toInt()) eq.setBandLevel(band.toShort(), 0)
            }
        }
        store?.eqPreset = -1
        store?.eqLevels = null
        setBass(0)
        setLoudness(0)
        publish()
    }
}
