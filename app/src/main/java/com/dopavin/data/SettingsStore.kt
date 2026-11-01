package com.dopavin.data

import android.content.Context

enum class ThemeMode { System, Dark, Light }

/** Audio quality ladder shared by streaming and downloads. High = best stream the provider offers. */
enum class AudioQuality { High, Standard, DataSaver }

/**
 * The stream quality to use right now: the Wi-Fi choice on an unmetered connection, the mobile-data choice
 * on a metered one. When the network state cannot be read, the Wi-Fi choice is used.
 */
fun AppSettings.effectiveStreamQuality(context: android.content.Context): AudioQuality {
    val metered = runCatching {
        val cm = context.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    }.getOrDefault(false)
    return if (metered) streamQualityMobile else streamQuality
}

/** User-facing options from the Settings screen. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    /** Dark theme with a true-black canvas (saves power on OLED screens). */
    val pureBlack: Boolean = false,
    /** Accent tint: 0 = app colours, 1..n = preset, -1 = follows the playing song (see AccentTint). */
    val accentTint: Int = 0,
    val skipSilence: Boolean = false,
    /** Pause when headphones / Bluetooth disconnect. */
    val pauseOnUnplug: Boolean = true,
    /** Keep the display on while the full player is open. */
    val keepScreenOn: Boolean = false,
    /** F-197: vibration feedback on taps and tab changes. */
    val haptics: Boolean = true,
    /** Changing the speed keeps the pitch (time-stretch). Off = pitch follows speed (vinyl feel). */
    val keepPitch: Boolean = true,
    /** The sleep timer fades the volume out over its last seconds instead of cutting the music. */
    val sleepFade: Boolean = true,
    /** Fetch lyrics from LRCLIB (sends title, artist, album, duration). */
    val lyricsOnline: Boolean = true,
    /** Lyrics provider choice: "choose" (race all providers) or a provider id. */
    val lyricsProvider: String = LyricsProviders.CHOOSE,
    /** Search and Home list YouTube Music songs; playback streams from the network. */
    val onlinePlayback: Boolean = true,
    /** Append generated picks near the queue's end (owner spec §8.2 Smart Autoplay). */
    val autoplay: Boolean = true,
    /** F-205: autoplay leaves out songs the user listened to recently. */
    val autoplayFresh: Boolean = true,
    /** F-219: seconds the song fades out / the next fades in; 0 = off. */
    val fadeSeconds: Int = 0,
    /** F-206: look for a newer GitHub release when the app opens (at most twice a day). */
    val autoUpdateCheck: Boolean = true,
    /** F-203: the current lyric line under the artist in Now Playing. */
    val lyricStrip: Boolean = false,
    /** Which stream the player picks when a song is not downloaded. */
    val streamQuality: AudioQuality = AudioQuality.High,
    /** F-192: quality used while the connection is metered (mobile data); High = same as Wi-Fi. */
    val streamQualityMobile: AudioQuality = AudioQuality.High,
    /** Which stream a download keeps. */
    val downloadQuality: AudioQuality = AudioQuality.High,
    /** Downloads wait for an unmetered (Wi-Fi) connection. */
    val downloadWifiOnly: Boolean = false,
    /** Liking a song also downloads it. */
    val autoDownloadLiked: Boolean = false,
    /** Download cache limit in MB; 0 = unlimited. Over the limit the oldest downloads are removed. */
    val downloadLimitMb: Int = 0,
    /** Resume music when headphones / Bluetooth reconnect shortly after they dropped. */
    val resumeOnConnect: Boolean = false,
    /** Now Playing background glows with the song's artwork colours, spreading from the top. */
    val ambience: Boolean = true,
    /** Liquid-glass surfaces (needs Android 12+ for blur, 13+ for the lens). */
    val glassEnabled: Boolean = true,
    /** Backdrop blur radius, dp (0..40). */
    val glassBlur: Int = 16,
    /** Lens (edge refraction) height, dp (0..32). 0 disables the lens. */
    val glassLensHeight: Int = 12,
    /** Lens strength, percent (0..100). */
    val glassRefraction: Int = 50,
    /** Colour tint laid over the glass, percent (0..60). */
    val glassTint: Int = 18,
    /** Colour saturation of what shows through, percent (100..200). */
    val glassSaturation: Int = 150,
    /** Rim-light brightness, percent (0..100). */
    val glassHighlight: Int = 60,
    /** Soft drop shadow under floating glass, percent (0..100). */
    val glassShadow: Int = 40,
    /** Render resolution of the glass, percent (40..100). Lower = faster, softer. */
    val glassQuality: Int = 60,
    /** Rainbow edge fringe on the lens (Android 13+, costs more GPU). */
    val glassDispersion: Boolean = false,
)

/** SharedPreferences for settings and the equaliser. Read on any thread; writes use apply(). */
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

    // ---- Playback speed ----

    /** Percent: 50..200, 100 = normal. */
    var speedPercent: Int
        get() = prefs.getInt(KEY_SPEED, 100)
        set(value) = prefs.edit().putInt(KEY_SPEED, value).apply()

    // ---- Liked songs ----

    /** Ids of liked songs (stable online ids). */
    var likedIds: Set<Long>
        get() = prefs.getString(KEY_LIKED, null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        set(value) = prefs.edit().putString(KEY_LIKED, value.joinToString(",")).apply()

    // ---- Backup ----

    /** Every stored preference (settings, equaliser, speed) as JSON, except the liked ids (exported separately). */
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

    /** Applies [json] from a backup. Only keys that look like our own preference names are accepted. */
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

    /** Restore defaults: replaces every user-tweak key with its stock value. Likes are preserved
     * deliberately (they are curation, not app preferences). */
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

    // ---- Equaliser ----

    var eqEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()

    /** Index of a system preset, or -1 for custom band levels. */
    var eqPreset: Int
        get() = prefs.getInt(KEY_EQ_PRESET, -1)
        set(value) = prefs.edit().putInt(KEY_EQ_PRESET, value).apply()

    /** Band levels in millibels, or null when never customised. */
    var eqLevels: List<Int>?
        get() = prefs.getString(KEY_EQ_LEVELS, null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_EQ_LEVELS, value?.joinToString(",")).apply()

    /** 0..1000 */
    var bassStrength: Int
        get() = prefs.getInt(KEY_BASS, 0)
        set(value) = prefs.edit().putInt(KEY_BASS, value).apply()

    /** Loudness gain in millibels, 0..1000 */
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
