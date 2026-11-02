package com.dopavin.data
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
data class HistoryEntry(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val songId: Long,
    val videoId: String?,
    val thumbUri: String?,
    val at: Long,
) {
    fun toSong(): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        album = "",
        albumKey = UNKNOWN_ALBUM_KEY,
        durationMs = durationMs,
        dateAdded = 0L,
        track = 0,
        isOnline = videoId != null,
        videoId = videoId,
        thumbUri = thumbUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() },
    )
}
class HistoryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("history", Context.MODE_PRIVATE)
    private val entries = ArrayList<HistoryEntry>()
    init {
        synchronized(entries) { load() }
    }
    fun record(song: Song, now: Long = System.currentTimeMillis()): Boolean {
        val title = song.title.trim()
        if (title.isBlank()) return false
        return synchronized(entries) {
            val changed = HistoryMath.upsertNewest(
                entries = entries,
                now = now,
                gapMs = GAP_MS,
                at = { it.at },
                withAt = { e, t -> e.copy(at = t) },
                match = { e -> e.videoId == song.videoId && (e.videoId != null || e.songId == song.id) },
                create = {
                    HistoryEntry(
                        title = title,
                        artist = song.artist.trim(),
                        durationMs = song.durationMs,
                        songId = song.id,
                        videoId = song.videoId,
                        thumbUri = song.thumbUri?.toString(),
                        at = now,
                    )
                },
                cap = CAP,
            )
            if (changed) persist()
            changed
        }
    }
    fun topArtists(limit: Int): List<String> = synchronized(entries) {
        val counts = HashMap<String, Int>()
        for (e in entries) {
            val name = e.artist.trim()
            if (name.isBlank() || name.length > MAX_ARTIST_LEN) continue
            counts[name.lowercase()] = (counts[name.lowercase()] ?: 0) + 1
        }
        val best = counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
        best.map { key ->
            entries.last { it.artist.trim().lowercase() == key }.artist.trim()
        }.distinct()
    }
    fun topArtistSongs(limit: Int): List<Song> = synchronized(entries) {
        topArtists(limit).mapNotNull { name ->
            entries.lastOrNull { it.artist.trim().equals(name, ignoreCase = true) }?.toSong()
        }
    }
    fun lastOnlineSong(): Song? = synchronized(entries) {
        entries.filter { it.videoId != null }.maxByOrNull { it.at }?.toSong()
    }
    fun recent(limit: Int): List<Song> = synchronized(entries) {
        entries.sortedByDescending { it.at }.filter { it.videoId != null }.take(limit).map { it.toSong() }
    }
    fun clear() {
        synchronized(entries) {
            entries.clear()
            version++
        }
        prefs.edit().remove(KEY_JSON).apply()
    }
    fun version(): Long = synchronized(entries) { version }
    private var version = 0L
    private fun persist() {
        version++
        val json = JSONArray()
        for (e in entries) {
            json.put(
                JSONObject()
                    .put(K_TITLE, e.title)
                    .put(K_ARTIST, e.artist)
                    .put(K_DURATION, e.durationMs)
                    .put(K_SONG_ID, e.songId)
                    .put(K_VIDEO, e.videoId.orEmpty())
                    .put(K_THUMB, e.thumbUri.orEmpty())
                    .put(K_AT, e.at),
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
                val videoId = e.optString(K_VIDEO).takeIf { it.isNotBlank() }
                val songId = e.optLong(K_SONG_ID)
                if (songId <= 0L && videoId == null) continue
                entries += HistoryEntry(
                    title = e.optString(K_TITLE),
                    artist = e.optString(K_ARTIST),
                    durationMs = e.optLong(K_DURATION),
                    songId = songId,
                    videoId = videoId,
                    thumbUri = e.optString(K_THUMB).takeIf { it.isNotBlank() },
                    at = e.optLong(K_AT),
                )
            }
        }
    }
    private companion object {
        const val KEY_JSON = "plays"
        const val K_TITLE = "t"
        const val K_ARTIST = "a"
        const val K_DURATION = "d"
        const val K_SONG_ID = "id"
        const val K_VIDEO = "v"
        const val K_THUMB = "th"
        const val K_AT = "at"
        const val CAP = 100
        const val GAP_MS = 90_000L
        const val MAX_ARTIST_LEN = 80
    }
}
