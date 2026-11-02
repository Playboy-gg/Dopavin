package com.dopavin.data
import android.content.Context
enum class ThemeMode { System, Dark, Light }
enum class AudioQuality { High, Standard, DataSaver }
fun AppSettings.effectiveStreamQuality(context: android.content.Context): AudioQuality {
    val metered = runCatching {
        val cm = context.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    }.getOrDefault(false)
    return if (metered) streamQualityMobile else streamQuality
}
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val pureBlack: Boolean = false,
    val accentTint: Int = 0,
    val skipSilence: Boolean = false,
    val pauseOnUnplug: Boolean = true,
    val keepScreenOn: Boolean = false,
    val haptics: Boolean = true,
    val keepPitch: Boolean = true,
    val sleepFade: Boolean = true,
    val lyricsOnline: Boolean = true,
    val lyricsProvider: String = LyricsProviders.CHOOSE,
    val onlinePlayback: Boolean = true,
    val autoplay: Boolean = true,
    val autoplayFresh: Boolean = true,
    val fadeSeconds: Int = 0,
    val autoUpdateCheck: Boolean = true,
    val lyricStrip: Boolean = false,
    val streamQuality: AudioQuality = AudioQuality.High,
    val streamQualityMobile: AudioQuality = AudioQuality.High,
    val downloadQuality: AudioQuality = AudioQuality.High,
    val downloadWifiOnly: Boolean = false,
    val autoDownloadLiked: Boolean = false,
    val downloadLimitMb: Int = 0,
    val resumeOnConnect: Boolean = false,
    val ambience: Boolean = true,
    val glassEnabled: Boolean = true,
    val glassBlur: Int = 16,
    val glassLensHeight: Int = 12,
    val glassRefraction: Int = 50,
    val glassTint: Int = 18,
    val glassSaturation: Int = 150,
    val glassHighlight: Int = 60,
    val glassShadow: Int = 40,
    val glassQuality: Int = 60,
    val glassDispersion: Boolean = false,
)
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun load(): AppSettings = AppSettings(
        theme = ThemeMode.values().getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.System },
        pureBlack = prefs.getBoolean(KEY_PURE_BLACK, false),
        accentTint = prefs.getInt(KEY_ACCENT_TINT, 0),
        skipSilence = prefs.getBoolean(KEY_SKIP_SILENCE, false),
        pauseOnUnplug = prefs.getBoolean(KEY_PAUSE_UNPLUG, true),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_ON, false),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
        keepPitch = prefs.getBoolean(KEY_KEEP_PITCH, true),
        sleepFade = prefs.getBoolean(KEY_SLEEP_FADE, true),
        lyricsOnline = prefs.getBoolean(KEY_LYRICS_ONLINE, true),
        lyricsProvider = LyricsProviders.normalise(prefs.getString(KEY_LYRICS_PROVIDER, LyricsProviders.CHOOSE)),
        onlinePlayback = prefs.getBoolean(KEY_ONLINE_PLAYBACK, true),
        autoplay = prefs.getBoolean(KEY_SMART_AUTOPLAY, true),
        autoplayFresh = prefs.getBoolean(KEY_AUTOPLAY_FRESH, true),
        fadeSeconds = prefs.getInt("fade_seconds", 0).coerceIn(0, 12),
        autoUpdateCheck = prefs.getBoolean(KEY_AUTO_UPDATE, true),
        lyricStrip = prefs.getBoolean(KEY_LYRIC_STRIP, false),
        streamQuality = AudioQuality.values()
            .getOrElse(prefs.getInt(KEY_STREAM_QUALITY, 0)) { AudioQuality.High },
        streamQualityMobile = AudioQuality.values()
            .getOrElse(prefs.getInt(KEY_STREAM_QUALITY_MOBILE, 0)) { AudioQuality.High },
        downloadQuality = AudioQuality.values()
            .getOrElse(prefs.getInt(KEY_DOWNLOAD_QUALITY, 0)) { AudioQuality.High },
        downloadWifiOnly = prefs.getBoolean(KEY_DOWNLOAD_WIFI, false),
        autoDownloadLiked = prefs.getBoolean(KEY_AUTO_DOWNLOAD_LIKED, false),
        downloadLimitMb = prefs.getInt("download_limit_mb", 0).coerceAtLeast(0),
        resumeOnConnect = prefs.getBoolean("resume_on_connect", false),
        ambience = prefs.getBoolean("ambience", true),
        glassEnabled = prefs.getBoolean("glass_enabled", true),
        glassBlur = prefs.getInt("glass_blur", 16).coerceIn(0, 40),
        glassLensHeight = prefs.getInt("glass_lens", 12).coerceIn(0, 32),
        glassRefraction = prefs.getInt("glass_refraction", 50).coerceIn(0, 100),
        glassTint = prefs.getInt("glass_tint", 18).coerceIn(0, 60),
        glassSaturation = prefs.getInt("glass_saturation", 150).coerceIn(100, 200),
        glassHighlight = prefs.getInt("glass_highlight", 60).coerceIn(0, 100),
        glassShadow = prefs.getInt("glass_shadow", 40).coerceIn(0, 100),
        glassQuality = prefs.getInt("glass_quality", 60).coerceIn(40, 100),
        glassDispersion = prefs.getBoolean("glass_dispersion", false),
    )
    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt(KEY_THEME, settings.theme.ordinal)
            .putBoolean(KEY_PURE_BLACK, settings.pureBlack)
            .putInt(KEY_ACCENT_TINT, settings.accentTint)
            .putBoolean(KEY_SKIP_SILENCE, settings.skipSilence)
            .putBoolean(KEY_PAUSE_UNPLUG, settings.pauseOnUnplug)
            .putBoolean(KEY_KEEP_ON, settings.keepScreenOn)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putBoolean(KEY_KEEP_PITCH, settings.keepPitch)
            .putBoolean(KEY_SLEEP_FADE, settings.sleepFade)
            .putBoolean(KEY_LYRICS_ONLINE, settings.lyricsOnline)
            .putString(KEY_LYRICS_PROVIDER, settings.lyricsProvider)
            .putBoolean(KEY_ONLINE_PLAYBACK, settings.onlinePlayback)
            .putBoolean(KEY_SMART_AUTOPLAY, settings.autoplay)
            .putBoolean(KEY_AUTOPLAY_FRESH, settings.autoplayFresh)
            .putInt("fade_seconds", settings.fadeSeconds)
            .putBoolean(KEY_AUTO_UPDATE, settings.autoUpdateCheck)
            .putBoolean(KEY_LYRIC_STRIP, settings.lyricStrip)
            .putInt(KEY_STREAM_QUALITY, settings.streamQuality.ordinal)
            .putInt(KEY_STREAM_QUALITY_MOBILE, settings.streamQualityMobile.ordinal)
            .putInt(KEY_DOWNLOAD_QUALITY, settings.downloadQuality.ordinal)
            .putBoolean(KEY_DOWNLOAD_WIFI, settings.downloadWifiOnly)
            .putBoolean(KEY_AUTO_DOWNLOAD_LIKED, settings.autoDownloadLiked)
            .putInt("download_limit_mb", settings.downloadLimitMb)
            .putBoolean("resume_on_connect", settings.resumeOnConnect)
            .putBoolean("ambience", settings.ambience)
            .putBoolean("glass_enabled", settings.glassEnabled)
            .putInt("glass_blur", settings.glassBlur)
            .putInt("glass_lens", settings.glassLensHeight)
            .putInt("glass_refraction", settings.glassRefraction)
            .putInt("glass_tint", settings.glassTint)
            .putInt("glass_saturation", settings.glassSaturation)
            .putInt("glass_highlight", settings.glassHighlight)
            .putInt("glass_shadow", settings.glassShadow)
            .putInt("glass_quality", settings.glassQuality)
            .putBoolean("glass_dispersion", settings.glassDispersion)
            .apply()
    }
    var speedPercent: Int
        get() = prefs.getInt(KEY_SPEED, 100)
        set(value) = prefs.edit().putInt(KEY_SPEED, value).apply()
    var likedIds: Set<Long>
        get() = prefs.getString(KEY_LIKED, null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        set(value) = prefs.edit().putString(KEY_LIKED, value.joinToString(",")).apply()
    fun exportPrefs(): org.json.JSONObject {
        val out = org.json.JSONObject()
        for ((key, value) in prefs.all) {
            if (key == KEY_LIKED) continue
            when (value) {
                is Boolean, is Int, is String -> out.put(key, value)
                is Long -> out.put(key, value.toInt())
                else -> Unit
            }
        }
        return out
    }
    fun importPrefs(json: org.json.JSONObject) {
        val edit = prefs.edit()
        for (key in json.keys()) {
            if (key == KEY_LIKED || !Regex("[a-z_]{1,40}").matches(key)) continue
            when (val v = json.get(key)) {
                is Boolean -> edit.putBoolean(key, v)
                is Int -> edit.putInt(key, v)
                is String -> edit.putString(key, v)
                else -> Unit
            }
        }
        edit.apply()
    }
    fun restoreDefaults() {
        save(AppSettings())
        prefs.edit()
            .putInt(KEY_SPEED, 100)
            .putBoolean(KEY_EQ_ENABLED, false)
            .putInt(KEY_EQ_PRESET, -1)
            .remove(KEY_EQ_LEVELS)
            .putInt(KEY_BASS, 0)
            .putInt(KEY_LOUDNESS, 0)
            .apply()
    }
    var eqEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()
    var eqPreset: Int
        get() = prefs.getInt(KEY_EQ_PRESET, -1)
        set(value) = prefs.edit().putInt(KEY_EQ_PRESET, value).apply()
    var eqLevels: List<Int>?
        get() = prefs.getString(KEY_EQ_LEVELS, null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_EQ_LEVELS, value?.joinToString(",")).apply()
    var bassStrength: Int
        get() = prefs.getInt(KEY_BASS, 0)
        set(value) = prefs.edit().putInt(KEY_BASS, value).apply()
    var loudnessMb: Int
        get() = prefs.getInt(KEY_LOUDNESS, 0)
        set(value) = prefs.edit().putInt(KEY_LOUDNESS, value).apply()
    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LIKED = "liked_ids"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_PURE_BLACK = "pure_black"
        const val KEY_ACCENT_TINT = "accent_tint"
        const val KEY_KEEP_PITCH = "keep_pitch"
        const val KEY_SLEEP_FADE = "sleep_fade"
        const val KEY_STREAM_QUALITY = "stream_quality"
        const val KEY_STREAM_QUALITY_MOBILE = "stream_quality_mobile"
        const val KEY_DOWNLOAD_QUALITY = "download_quality"
        const val KEY_DOWNLOAD_WIFI = "download_wifi_only"
        const val KEY_AUTO_DOWNLOAD_LIKED = "auto_download_liked"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_EQ_LEVELS = "eq_levels"
        const val KEY_BASS = "bass"
        const val KEY_LOUDNESS = "loudness"
        const val KEY_PAUSE_UNPLUG = "pause_on_unplug"
        const val KEY_KEEP_ON = "keep_screen_on"
        const val KEY_HAPTICS = "haptics"
        const val KEY_LYRICS_ONLINE = "lyrics_online"
        const val KEY_LYRICS_PROVIDER = "lyrics_provider"
        const val KEY_ONLINE_PLAYBACK = "online_playback"
        const val KEY_SMART_AUTOPLAY = "smart_autoplay"
        const val KEY_AUTOPLAY_FRESH = "autoplay_fresh"
        const val KEY_AUTO_UPDATE = "auto_update_check"
        const val KEY_LYRIC_STRIP = "lyric_strip"
        const val KEY_SPEED = "speed_percent"
    }
}
