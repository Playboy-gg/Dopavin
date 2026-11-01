package com.dopavin.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * F-218: scrobbling to ListenBrainz (https://listenbrainz.org), the open scrobbling service. The owner pastes the
 * user token from their ListenBrainz profile page; there is no password and no account inside Dopavin.
 * Payload building and the "does this count as a listen" rule are plain Kotlin and unit-tested; the HTTP calls
 * are not run in the sandbox (no route to the service).
 */
object ListenBrainz {
    private const val API = "https://api.listenbrainz.org/1"

    /** A listen counts after 4 minutes or half of the track, whichever comes first; tracks under 30 s never count. */
    fun shouldScrobble(playedMs: Long, durationMs: Long): Boolean {
        if (durationMs in 1 until 30_000L) return false
        if (durationMs <= 0L) return playedMs >= 60_000L
        return playedMs >= minOf(durationMs / 2, 240_000L)
    }

    /** YouTube Music artist channels read "Name - Topic"; the service wants the plain name. */
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

    /** One listen object. [listenedAtSec] is null for "now playing". */
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

    /** [type] is "single", "import" (several finished listens) or "playing_now". */
    fun body(type: String, items: List<String>): String =
        "{\"listen_type\":\"$type\",\"payload\":[${items.joinToString(",")}]}"

    enum class Result { Ok, BadToken, Retry }

    sealed interface Check {
        data class Valid(val user: String) : Check
        data object Invalid : Check
        data object Unreachable : Check
    }

    /** Blocking; call off the main thread. */
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
                // 400 = the service rejected this listen itself; retrying the same data cannot help.
                400 -> Result.Ok
                else -> Result.Retry
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Result.Retry
    }

    /** Blocking; asks the service whether [token] is valid and whose it is. */
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
