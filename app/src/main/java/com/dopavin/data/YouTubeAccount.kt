package com.dopavin.data

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class YtTrack(val videoId: String, val title: String, val artist: String, val durationSec: Int, val thumb: String?)

data class YtPlaylist(val browseId: String, val title: String)

/**
 * F-221: the owner's own YouTube Music session. The owner signs in on Google's normal sign-in page inside a
 * WebView; Dopavin never sees the password, it only keeps the session cookies that page leaves behind
 * ([AccountStore.youTubeCookie]). With them it can ask YouTube Music for the owner's liked songs and library
 * playlists, authenticating the way the web player does (cookie + SAPISIDHASH header).
 *
 * Honest limits: this uses YouTube Music's own web endpoints, which are not a public, documented API and can
 * change without notice; the reply parser therefore searches the reply tree for the known item shapes instead of
 * following one fixed path. Signing in stays optional and everything else in the app works without it.
 * Parsing and hashing are unit-tested on made-up replies; nothing here could be run against the live service
 * from the sandbox.
 */
object YouTubeAccount {
    const val ORIGIN = "https://music.youtube.com"
    const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

    /** Query prefix of an online list that shows the owner's liked songs (see MusicViewModel.loadOnlineList). */
    const val LIKED_QUERY = "ytacc:liked"

    // ---- Cookie and auth header ----

    fun cookieValue(cookie: String, name: String): String? =
        cookie.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')?.takeIf { it.isNotEmpty() }

    fun sapisid(cookie: String): String? = cookieValue(cookie, "SAPISID") ?: cookieValue(cookie, "__Secure-3PAPISID")

    /** A real session cookie, not just any text. */
    fun isSignedIn(cookie: String): Boolean = sapisid(cookie) != null

    fun sha1Hex(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** `SAPISIDHASH <unix seconds>_<sha1("<seconds> <SAPISID> <origin>")>`, as the YouTube web client sends it. */
    fun authHeader(sapisid: String, unixSeconds: Long, origin: String = ORIGIN): String =
        "SAPISIDHASH ${unixSeconds}_${sha1Hex("$unixSeconds $sapisid $origin")}"

    // ---- Reply parsing ----

    /** "3:45" -> 225, "1:02:03" -> 3723, anything else -> 0. */
    fun parseDuration(text: String?): Int {
        val parts = text?.trim()?.split(':')?.map { it.toIntOrNull() ?: return 0 } ?: return 0
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun collect(node: Any?, key: String, out: MutableList<Map<String, Any?>>) {
        when (node) {
            is Map<*, *> -> for ((k, v) in node) {
                if (k == key) MiniJson.obj(v)?.let { out += it } else collect(v, key, out)
            }
            is List<*> -> node.forEach { collect(it, key, out) }
        }
    }

    private fun firstKey(node: Any?, key: String): Any? {
        when (node) {
            is Map<*, *> -> {
                if (node.containsKey(key)) return node[key]
                for (v in node.values) firstKey(v, key)?.let { return it }
            }
            is List<*> -> for (v in node) firstKey(v, key)?.let { return it }
        }
        return null
    }

    private fun runText(column: Any?): String? {
        val text = MiniJson.obj(MiniJson.obj(column)?.values?.firstOrNull())?.get("text")
        val runs = MiniJson.list(MiniJson.obj(text)?.get("runs"))
        return MiniJson.str(MiniJson.obj(runs?.firstOrNull())?.get("text"))?.takeIf { it.isNotBlank() }
    }

    /** Every song row in a browse reply; rows without a video id (unavailable songs) are skipped. */
    fun parseTracks(json: String): List<YtTrack> {
        val rows = ArrayList<Map<String, Any?>>()
        collect(MiniJson.parse(json), "musicResponsiveListItemRenderer", rows)
        return rows.mapNotNull { row ->
            val videoId = MiniJson.str(MiniJson.obj(row["playlistItemData"])?.get("videoId"))
                ?: MiniJson.str(firstKey(row["overlay"], "videoId"))
                ?: return@mapNotNull null
            val flex = MiniJson.list(row["flexColumns"]).orEmpty()
            val title = runText(flex.getOrNull(0)) ?: return@mapNotNull null
            val artist = runText(flex.getOrNull(1)).orEmpty()
            val fixed = MiniJson.list(row["fixedColumns"])?.firstOrNull()
            val duration = parseDuration(runText(fixed))
            val thumbs = MiniJson.list(
                MiniJson.obj(MiniJson.obj(MiniJson.obj(row["thumbnail"])?.get("musicThumbnailRenderer"))?.get("thumbnail"))?.get("thumbnails"),
            )
            val thumb = MiniJson.str(MiniJson.obj(thumbs?.lastOrNull())?.get("url"))
            YtTrack(videoId, title, artist, duration, thumb)
        }.distinctBy { it.videoId }
    }

    /** The owner's library playlists (browse ids start with "VL"); the liked-songs list (VLLM) is left out. */
    fun parsePlaylists(json: String): List<YtPlaylist> {
        val items = ArrayList<Map<String, Any?>>()
        collect(MiniJson.parse(json), "musicTwoRowItemRenderer", items)
        return items.mapNotNull { item ->
            val browseId = MiniJson.str(firstKey(item["navigationEndpoint"], "browseId")) ?: return@mapNotNull null
            if (!browseId.startsWith("VL") || browseId == "VLLM") return@mapNotNull null
            val title = MiniJson.str(MiniJson.obj(MiniJson.list(MiniJson.obj(item["title"])?.get("runs"))?.firstOrNull())?.get("text"))
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            YtPlaylist(browseId, title)
        }.distinctBy { it.browseId }
    }

    /** Token for the next page of a long list, or null at the end. Both reply generations are understood. */
    fun continuation(json: String): String? {
        val root = MiniJson.parse(json)
        MiniJson.str(MiniJson.obj(firstKey(root, "nextContinuationData"))?.get("continuation"))?.let { return it }
        return MiniJson.str(MiniJson.obj(firstKey(root, "continuationCommand"))?.get("token"))
    }

    // ---- Network (blocking; call off the main thread) ----

    private fun clientVersion(): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return "1." + f.format(Date(System.currentTimeMillis() - 86_400_000L)) + ".01.00"
    }

    private fun browseBody(browseId: String?): String = buildString {
        append("{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"").append(clientVersion())
        append("\",\"hl\":\"en\",\"gl\":\"US\"},\"user\":{\"lockedSafetyMode\":false}}")
        if (browseId != null) append(",\"browseId\":\"").append(ListenBrainz.escape(browseId)).append('"')
        append('}')
    }

    /** One browse call; the reply text, or null on any failure (signed out, offline, changed endpoint). */
    fun browse(cookie: String, browseId: String?, continuation: String? = null): String? {
        val sapisid = sapisid(cookie) ?: return null
        val address = buildString {
            append(ORIGIN).append("/youtubei/v1/browse?prettyPrint=false")
            if (continuation != null) {
                val t = java.net.URLEncoder.encode(continuation, "UTF-8")
                append("&ctoken=").append(t).append("&continuation=").append(t).append("&type=next")
            }
        }
        return try {
            val conn = URL(address).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("Authorization", authHeader(sapisid, System.currentTimeMillis() / 1000))
            conn.setRequestProperty("X-Goog-AuthUser", "0")
            conn.setRequestProperty("X-Origin", ORIGIN)
            conn.setRequestProperty("Origin", ORIGIN)
            conn.setRequestProperty("Referer", "$ORIGIN/")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            try {
                conn.outputStream.use { it.write(browseBody(if (continuation == null) browseId else null).toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** All pages of a song list (up to [maxPages]). Null when the first call fails, so callers can tell "failed" from "empty". */
    private fun allTracks(cookie: String, browseId: String, maxPages: Int): List<YtTrack>? {
        var reply = browse(cookie, browseId) ?: return null
        val out = ArrayList<YtTrack>(parseTracks(reply))
        var page = 1
        while (page < maxPages) {
            val next = continuation(reply) ?: break
            reply = browse(cookie, null, next) ?: break
            val more = parseTracks(reply)
            if (more.isEmpty()) break
            out += more
            page++
        }
        return out.distinctBy { it.videoId }
    }

    fun likedSongs(cookie: String, maxPages: Int = 5): List<YtTrack>? = allTracks(cookie, "VLLM", maxPages)

    fun playlistSongs(cookie: String, browseId: String, maxPages: Int = 5): List<YtTrack>? = allTracks(cookie, browseId, maxPages)

    fun libraryPlaylists(cookie: String): List<YtPlaylist>? =
        browse(cookie, "FEmusic_liked_playlists")?.let { parsePlaylists(it) }
}
