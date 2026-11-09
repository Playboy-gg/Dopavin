package com.dopavin.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One counted listen: the song was played for at least [ListenLog.MIN_PLAYED_MS]. */
data class ListenEntry(
    val key: String,
    val title: String,
    val artist: String,
    val videoId: String?,
    val songId: Long,
    val thumbUri: String?,
    val playedMs: Long,
    val at: Long,
) {
    fun toSong(): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        album = "",
        albumKey = UNKNOWN_ALBUM_KEY,
        durationMs = 0L,
        dateAdded = 0L,
        track = 0,
        isOnline = videoId != null,
        videoId = videoId,
        thumbUri = thumbUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() },
    )
}

/**
 * Listening log behind the Recap screen (F-190). Unlike [HistoryStore], which keeps one row per song, every
 * counted listen is its own entry, so plays and minutes can be added up. Stored on the device only
 * (SharedPreferences JSON, capped); it is wiped by "Clear Recap" and by resetting personalisation.
 */
class ListenLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("listen_log", Context.MODE_PRIVATE)
    private val entries = ArrayList<ListenEntry>()
    private var version = 0L

    init {
        synchronized(entries) { load() }
    }

    /** Adds a listen when [playedMs] reaches the minimum; returns true when something was recorded. */
    fun record(song: Song, playedMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (!RecapMath.counts(playedMs, song.durationMs)) return false
        if (song.title.isBlank()) return false
        synchronized(entries) {
            entries += ListenEntry(
                key = song.videoId ?: "l${song.id}",
                title = song.title.trim(),
                artist = song.artist.trim(),
                videoId = song.videoId,
                songId = song.id,
                thumbUri = song.thumbUri?.toString(),
                playedMs = playedMs.coerceAtLeast(0L),
                at = now,
            )
            if (entries.size > CAP) repeat(entries.size - CAP) { entries.removeAt(0) }
            persist()
        }
        return true
    }

    fun all(): List<ListenEntry> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            version++
        }
        prefs.edit().remove(KEY_JSON).apply()
    }

    private fun persist() {
        version++
        val json = JSONArray()
        for (e in entries) {
            json.put(
                JSONObject()
                    .put("k", e.key).put("t", e.title).put("a", e.artist)
                    .put("v", e.videoId.orEmpty()).put("i", e.songId)
                    .put("u", e.thumbUri.orEmpty()).put("p", e.playedMs).put("at", e.at),
            )
        }
        prefs.edit().putString(KEY_JSON, json.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY_JSON, null) ?: return
        runCatching {
            val json = JSONArray(raw)
            for (i in 0 until json.length()) {
                val e = json.optJSONObject(i) ?: continue
                val key = e.optString("k")
                if (key.isBlank()) continue
                entries += ListenEntry(
                    key = key,
                    title = e.optString("t"),
                    artist = e.optString("a"),
                    videoId = e.optString("v").takeIf { it.isNotBlank() },
                    songId = e.optLong("i"),
                    thumbUri = e.optString("u").takeIf { it.isNotBlank() },
                    playedMs = e.optLong("p"),
                    at = e.optLong("at"),
                )
            }
        }
    }

    companion object {
        const val MIN_PLAYED_MS = 30_000L
        private const val CAP = 3000
        private const val KEY_JSON = "listens"
    }
}

/** What the Recap screen shows for one time range. */
data class RecapSummary(
    val minutes: Long,
    val plays: Int,
    val songs: Int,
    val artists: Int,
    val topSongs: List<RecapSong>,
    val topArtists: List<RecapArtist>,
    /** Minutes per day, oldest first, ending today; empty for the all-time range. */
    val dailyMinutes: List<Int>,
)

data class RecapSong(val song: Song, val plays: Int)
data class RecapArtist(val name: String, val plays: Int)

/** Pure counting rules of the Recap, kept free of Android so they are unit-tested. */
object RecapMath {
    private const val DAY_MS = 86_400_000L

    /** A listen counts after 30 s, or after half the song when the song is shorter than a minute. */
    fun counts(playedMs: Long, durationMs: Long): Boolean {
        val needed = if (durationMs in 1 until 60_000L) durationMs / 2 else ListenLog.MIN_PLAYED_MS
        return playedMs >= needed
    }

    /**
     * @param days 7, 30 or 0 for all time.
     * @param zoneOffsetMs local time zone offset, so a "day" matches the user's calendar day.
     */
    fun summarize(
        entries: List<ListenEntry>,
        days: Int,
        now: Long,
        zoneOffsetMs: Long,
        topLimit: Int = 5,
    ): RecapSummary {
        val today = Math.floorDiv(now + zoneOffsetMs, DAY_MS)
        val inRange = if (days <= 0) entries else entries.filter { Math.floorDiv(it.at + zoneOffsetMs, DAY_MS) > today - days }
        val songCounts = LinkedHashMap<String, Pair<ListenEntry, Int>>()
        val artistCounts = HashMap<String, Pair<String, Int>>()
        for (e in inRange) {
            val prev = songCounts[e.key]
            // Keep the newest entry of a song, so its title and artwork are the freshest.
            songCounts[e.key] = (if (prev == null || e.at >= prev.first.at) e else prev.first) to ((prev?.second ?: 0) + 1)
            val a = e.artist.trim()
            if (a.isNotEmpty()) {
                val k = a.lowercase()
                val p = artistCounts[k]
                artistCounts[k] = (p?.first ?: a) to ((p?.second ?: 0) + 1)
            }
        }
        val daily = if (days <= 0) emptyList() else {
            val ms = LongArray(days)
            for (e in inRange) {
                val idx = (Math.floorDiv(e.at + zoneOffsetMs, DAY_MS) - (today - days + 1)).toInt()
                if (idx in 0 until days) ms[idx] += e.playedMs
            }
            ms.map { (it / 60_000L).toInt() }
        }
        return RecapSummary(
            minutes = inRange.sumOf { it.playedMs } / 60_000L,
            plays = inRange.size,
            songs = songCounts.size,
            artists = artistCounts.size,
            topSongs = songCounts.values
                .sortedWith(compareByDescending<Pair<ListenEntry, Int>> { it.second }.thenByDescending { it.first.at })
                .take(topLimit).map { RecapSong(it.first.toSong(), it.second) },
            topArtists = artistCounts.values
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
                .take(topLimit).map { RecapArtist(it.first, it.second) },
            dailyMinutes = daily,
        )
    }
}
