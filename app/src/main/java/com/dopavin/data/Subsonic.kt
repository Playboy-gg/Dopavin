package com.dopavin.data
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
data class SubsonicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val coverArt: String?,
)
data class SubsonicServer(val url: String, val user: String, val password: String)
object Subsonic {
    const val ID_PREFIX = "sub:"
    private const val API_VERSION = "1.16.1"
    private const val CLIENT = "Dopavin"
    fun isServerSong(videoId: String?): Boolean = videoId?.startsWith(ID_PREFIX) == true
    fun baseUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty()) return ""
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }
    fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun newSalt(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    fun url(
        server: SubsonicServer,
        endpoint: String,
        params: List<Pair<String, String>> = emptyList(),
        salt: String = newSalt(),
    ): String = buildString {
        append(baseUrl(server.url)).append("/rest/").append(endpoint).append(".view")
        append("?u=").append(enc(server.user))
        append("&t=").append(md5(server.password + salt))
        append("&s=").append(salt)
        append("&v=").append(API_VERSION).append("&c=").append(CLIENT).append("&f=json")
        for ((k, v) in params) append('&').append(k).append('=').append(enc(v))
    }
    fun streamUrl(server: SubsonicServer, trackId: String, maxBitRate: Int = 0): String =
        url(server, "stream", buildList {
            add("id" to trackId)
            if (maxBitRate > 0) add("maxBitRate" to maxBitRate.toString())
        })
    fun coverUrl(server: SubsonicServer, coverArt: String, size: Int = 512): String =
        url(server, "getCoverArt", listOf("id" to coverArt, "size" to size.toString()))
    sealed interface Reply {
        data object Ok : Reply
        data class Failed(val message: String) : Reply
        data object Unreachable : Reply
    }
    fun parseStatus(json: String): Reply {
        val root = MiniJson.obj(MiniJson.obj(MiniJson.parse(json))?.get("subsonic-response")) ?: return Reply.Failed("")
        if (MiniJson.str(root["status"]) == "ok") return Reply.Ok
        return Reply.Failed(MiniJson.str(MiniJson.obj(root["error"])?.get("message")).orEmpty())
    }
    fun parseTracks(json: String): List<SubsonicTrack> {
        val root = MiniJson.obj(MiniJson.obj(MiniJson.parse(json))?.get("subsonic-response")) ?: return emptyList()
        val container = listOf("searchResult3", "randomSongs", "starred2", "playlist", "searchResult2", "starred")
            .firstNotNullOfOrNull { MiniJson.obj(root[it]) } ?: return emptyList()
        val entries = MiniJson.list(container["song"]) ?: MiniJson.list(container["entry"]) ?: return emptyList()
        return entries.mapNotNull { e ->
            val o = MiniJson.obj(e) ?: return@mapNotNull null
            val id = idOf(o["id"]) ?: return@mapNotNull null
            val title = MiniJson.str(o["title"])?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SubsonicTrack(
                id = id,
                title = title,
                artist = MiniJson.str(o["artist"]).orEmpty(),
                album = MiniJson.str(o["album"]).orEmpty(),
                durationSec = (o["duration"] as? Double)?.toInt() ?: 0,
                coverArt = idOf(o["coverArt"]),
            )
        }
    }
    private fun idOf(v: Any?): String? = when (v) {
        is String -> v.takeIf { it.isNotBlank() }
        is Double -> v.toLong().toString()
        else -> null
    }
    private fun fetch(address: String): String? = try {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Dopavin")
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
    fun ping(server: SubsonicServer): Reply {
        val body = fetch(url(server, "ping")) ?: return Reply.Unreachable
        return parseStatus(body)
    }
    fun search(server: SubsonicServer, query: String, count: Int = 40): List<SubsonicTrack> {
        val body = fetch(url(server, "search3", listOf("query" to query, "songCount" to count.toString(), "artistCount" to "0", "albumCount" to "0")))
        return body?.let { parseTracks(it) }.orEmpty()
    }
    fun random(server: SubsonicServer, count: Int = 50): List<SubsonicTrack> =
        fetch(url(server, "getRandomSongs", listOf("size" to count.toString())))?.let { parseTracks(it) }.orEmpty()
    fun starred(server: SubsonicServer): List<SubsonicTrack> =
        fetch(url(server, "getStarred2"))?.let { parseTracks(it) }.orEmpty()
}
