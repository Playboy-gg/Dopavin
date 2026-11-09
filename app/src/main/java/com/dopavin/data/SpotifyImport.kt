package com.dopavin.data

import java.net.HttpURLConnection
import java.net.URL

/** A public Spotify playlist or album, read without any account. */
data class SpotifyList(val name: String, val tracks: List<SpotifyTrack>)

data class SpotifyTrack(val title: String, val artist: String)

/**
 * F-204: reads the track list of a PUBLIC Spotify playlist or album link from Spotify's public embed page
 * (no sign-in, no API key). The page layout is not a documented API and can change, so the reader looks for the
 * `trackList` array anywhere in the page data instead of at a fixed path, and every failure is reported as null.
 * Only title and artist are read; matching to playable songs happens elsewhere (YouTube search).
 */
object SpotifyImport {
    private val linkPattern = Regex("""open\.spotify\.com/(?:intl-[a-z]+/)?(playlist|album)/([A-Za-z0-9]{22})""")
    private val uriPattern = Regex("""spotify:(playlist|album):([A-Za-z0-9]{22})""")

    /** "playlist" or "album" plus the 22-character id, or null when [text] holds no such link. */
    fun parseLink(text: String): Pair<String, String>? {
        val m = linkPattern.find(text) ?: uriPattern.find(text) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    /** Parses the embed page [html]; null when no track list can be found. */
    fun parsePage(html: String): SpotifyList? {
        val start = html.indexOf("id=\"__NEXT_DATA__\"")
        if (start < 0) return null
        val open = html.indexOf('>', start)
        val close = html.indexOf("</script>", open)
        if (open < 0 || close < 0) return null
        val root = MiniJson.parse(html.substring(open + 1, close).trim()) ?: return null
        val entity = findEntity(root, 0) ?: return null
        val tracks = (MiniJson.list(entity["trackList"]) ?: return null).mapNotNull { item ->
            val m = MiniJson.obj(item) ?: return@mapNotNull null
            val title = clean(MiniJson.str(m["title"]))
            if (title.isBlank()) return@mapNotNull null
            SpotifyTrack(title, clean(MiniJson.str(m["subtitle"])))
        }
        if (tracks.isEmpty()) return null
        val name = clean(MiniJson.str(entity["name"]) ?: MiniJson.str(entity["title"])).ifBlank { "Spotify" }
        return SpotifyList(name, tracks)
    }

    private fun findEntity(node: Any?, depth: Int): Map<String, Any?>? {
        if (depth > 12) return null
        MiniJson.obj(node)?.let { map ->
            if (map["trackList"] is List<*>) return map
            for (value in map.values) findEntity(value, depth + 1)?.let { return it }
        }
        MiniJson.list(node)?.let { list ->
            for (value in list) findEntity(value, depth + 1)?.let { return it }
        }
        return null
    }

    private fun clean(s: String?): String = s.orEmpty().replace(' ', ' ').trim()

    /** Downloads and parses the list behind a link. Blocking; call off the main thread. Null on any failure. */
    fun fetch(kind: String, id: String): SpotifyList? = try {
        val conn = URL("https://open.spotify.com/embed/$kind/$id").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        conn.setRequestProperty("Accept-Language", "en")
        try {
            if (conn.responseCode != 200) null else parsePage(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
