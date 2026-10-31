package com.dopavin.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** The online part of Home as it was last fetched, so the next cold start can paint it at once. */
data class HomeSnapshot(
    val savedAt: Long,
    val trending: List<Song>,
    val relatedTitle: String,
    val related: List<Song>,
    val artists: List<Pair<String, List<Song>>>,
)

/**
 * Tiny SharedPreferences JSON mirror of the Home shelves (stale-while-revalidate, F-117). Only
 * display metadata is stored (title, artist, duration, video id, thumbnail); stream urls never are.
 * One small read at start-up, writes use apply().
 */
class HomeCache(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("home_cache", Context.MODE_PRIVATE)

    fun load(): HomeSnapshot? = runCatching {
        val raw = prefs.getString(KEY, null) ?: return null
        val o = JSONObject(raw)
        HomeSnapshot(
            savedAt = o.optLong("at"),
            trending = songs(o.optJSONArray("trending")),
            relatedTitle = o.optString("relatedTitle"),
            related = songs(o.optJSONArray("related")),
            artists = (o.optJSONArray("artists") ?: JSONArray()).let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val shelf = arr.optJSONObject(i) ?: return@mapNotNull null
                    val list = songs(shelf.optJSONArray("songs"))
                    if (list.isEmpty()) null else shelf.optString("name") to list
                }
            },
        )
    }.getOrNull()

    fun save(snapshot: HomeSnapshot) {
        val o = JSONObject()
            .put("at", snapshot.savedAt)
            .put("trending", array(snapshot.trending))
            .put("relatedTitle", snapshot.relatedTitle)
            .put("related", array(snapshot.related))
            .put(
                "artists",
                JSONArray().apply {
                    snapshot.artists.forEach { (name, list) ->
                        put(JSONObject().put("name", name).put("songs", array(list)))
                    }
                },
            )
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private fun array(list: List<Song>): JSONArray = songsToJson(list)

    private fun songs(arr: JSONArray?): List<Song> = songsFromJson(arr)

    private companion object {
        const val KEY = "snapshot"
    }
}

/** Display-metadata JSON of online songs, shared by the Home cache and the subscription feed cache. */
internal fun songsToJson(list: List<Song>): JSONArray = JSONArray().apply {
    list.forEach { song ->
        put(
            JSONObject()
                .put("v", song.videoId.orEmpty())
                .put("t", song.title)
                .put("a", song.artist)
                .put("d", song.durationMs)
                .put("th", song.thumbUri?.toString().orEmpty())
                .put("cu", song.channelUrl.orEmpty()),
        )
    }
}

internal fun songsFromJson(arr: JSONArray?): List<Song> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val videoId = o.optString("v").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Song(
            id = stableOnlineId(videoId),
            title = o.optString("t"),
            artist = o.optString("a"),
            album = "",
            albumKey = UNKNOWN_ALBUM_KEY,
            durationMs = o.optLong("d"),
            dateAdded = 0L,
            track = 0,
            isOnline = true,
            videoId = videoId,
            thumbUri = o.optString("th").takeIf { it.isNotBlank() }?.let(Uri::parse),
            channelUrl = o.optString("cu").takeIf { it.isNotBlank() },
        )
    }
}
