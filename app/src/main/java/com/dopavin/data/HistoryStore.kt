package com.dopavin.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One recorded play of a song, local or online. Whatever identifies it is kept: an online entry
 * carries [videoId], a local one the MediaStore [songId]; the other may be absent. */
data class HistoryEntry(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val songId: Long,
    val videoId: String?,
    val thumbUri: String?,
    val at: Long,
) {
    /** Rebuilds the playable Song from what was recorded (online rows need nothing else). */
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

/**
 * What the user actually played (owner F-86, approved 2026-10-20). A tiny JSON list in
 * SharedPreferences - the only local source of taste available without an account. The newest
 * entries end the array; the whole thing is capped, so scans stay trivial. Main-thread reads are
 * served from the memory mirror; writes go through apply().
 */
class HistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("history", Context.MODE_PRIVATE)

    private val entries = ArrayList<HistoryEntry>()

    init {
        synchronized(entries) { load() }
    }

    /** Upserts [song]: the same identity closer than [GAP_MS] changes nothing; a later replay MOVES the row to the end. */
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

    /** Artist names with at least one play, most-played first. Blank names are skipped. */
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
        // Display uses the casing of the most recent entry for that artist.
        best.map { key ->
            entries.last { it.artist.trim().lowercase() == key }.artist.trim()
        }.distinct()
    }

    /** For each of the [limit] most-played artists, their newest played song (for name + artwork). */
    fun topArtistSongs(limit: Int): List<Song> = synchronized(entries) {
        topArtists(limit).mapNotNull { name ->
            entries.lastOrNull { it.artist.trim().equals(name, ignoreCase = true) }?.toSong()
        }
    }

    /** The most recently played online song, or null before any online play. */
    fun lastOnlineSong(): Song? = synchronized(entries) {
        entries.filter { it.videoId != null }.maxByOrNull { it.at }?.toSong()
    }

    /** The newest plays, newest first (owner F-97, the Home history shelf). */
    fun recent(limit: Int): List<Song> = synchronized(entries) {
        entries.sortedByDescending { it.at }.filter { it.videoId != null }.take(limit).map { it.toSong() }
    }

    /** Wipes the log (reset personalization, owner spec §11). */
    fun clear() {
        synchronized(entries) {
            entries.clear()
            version++
        }
        prefs.edit().remove(KEY_JSON).apply()
    }

    /** Rises whenever something new was recorded; the Home driver re-runs on it. */
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
