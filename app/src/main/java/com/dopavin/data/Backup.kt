package com.dopavin.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup file: liked songs, playlists and settings as one small JSON document. Downloaded audio is
 * not part of it (it can be fetched again). Pure functions so the format is unit-testable.
 */
object Backup {
    const val VERSION = 1

    class Content(
        val likedIds: Set<Long>,
        val playlists: Map<String, List<Song>>,
        val prefs: JSONObject?,
    )

    fun encode(likedIds: Set<Long>, playlists: Map<String, List<Song>>, prefs: JSONObject): String =
        JSONObject()
            .put("app", "dopavin")
            .put("version", VERSION)
            .put("liked", JSONArray().apply { likedIds.forEach { put(it) } })
            .put("playlists", JSONObject(PlaylistCodec.encode(playlists)))
            .put("settings", prefs)
            .toString()

    /** Null when [raw] is not a Dopavin backup. */
    fun decode(raw: String): Content? = try {
        val root = JSONObject(raw)
        if (root.optString("app") != "dopavin" || root.optInt("version", 0) > VERSION) {
            null
        } else {
            val liked = root.optJSONArray("liked")
                ?.let { array -> (0 until array.length()).map { array.getLong(it) }.toSet() }
                ?: emptySet()
            val playlists = root.optJSONObject("playlists")?.toString()
                ?.let { PlaylistCodec.decode(it) }
                ?: emptyMap()
            Content(liked, playlists, root.optJSONObject("settings"))
        }
    } catch (e: Exception) {
        null
    }
}
