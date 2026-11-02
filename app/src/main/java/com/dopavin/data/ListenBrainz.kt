package com.dopavin.data
import java.net.HttpURLConnection
import java.net.URL
object ListenBrainz {
    private const val API = "https://api.listenbrainz.org/1"
    fun shouldScrobble(playedMs: Long, durationMs: Long): Boolean {
        if (durationMs in 1 until 30_000L) return false
        if (durationMs <= 0L) return playedMs >= 60_000L
        return playedMs >= minOf(durationMs / 2, 240_000L)
    }
    fun cleanArtist(artist: String): String = artist.trim().removeSuffix(" - Topic").trim()
    fun escape(s: String): String = buildString {
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
    }
    fun listenItem(artist: String, title: String, album: String, listenedAtSec: Long?, durationMs: Long): String = buildString {
        append('{')
        if (listenedAtSec != null) append("\"listened_at\":").append(listenedAtSec).append(',')
        append("\"track_metadata\":{")
        append("\"artist_name\":\"").append(escape(cleanArtist(artist))).append("\",")
        append("\"track_name\":\"").append(escape(title.trim())).append('"')
        if (album.isNotBlank()) append(",\"release_name\":\"").append(escape(album.trim())).append('"')
        append(",\"additional_info\":{\"media_player\":\"Dopavin\",\"submission_client\":\"Dopavin\"")
        if (durationMs > 0) append(",\"duration_ms\":").append(durationMs)
        append("}}}")
    }
    fun body(type: String, items: List<String>): String =
        "{\"listen_type\":\"$type\",\"payload\":[${items.joinToString(",")}]}"
    enum class Result { Ok, BadToken, Retry }
    sealed interface Check {
        data class Valid(val user: String) : Check
        data object Invalid : Check
        data object Unreachable : Check
    }
    fun submit(token: String, body: String): Result = try {
        val conn = URL("$API/submit-listens").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Token $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Dopavin")
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                200 -> Result.Ok
                401 -> Result.BadToken
                400 -> Result.Ok
                else -> Result.Retry
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Result.Retry
    }
    fun validate(token: String): Check = try {
        val conn = URL("$API/validate-token").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "Token $token")
        conn.setRequestProperty("User-Agent", "Dopavin")
        try {
            if (conn.responseCode == 200) {
                val root = MiniJson.obj(MiniJson.parse(conn.inputStream.bufferedReader().use { it.readText() }))
                if (root?.get("valid") == true) Check.Valid(MiniJson.str(root["user_name"]).orEmpty()) else Check.Invalid
            } else if (conn.responseCode == 401) {
                Check.Invalid
            } else {
                Check.Unreachable
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Check.Unreachable
    }
}
