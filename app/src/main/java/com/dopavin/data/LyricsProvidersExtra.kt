package com.dopavin.data

import java.util.Base64
import kotlin.math.abs

/** Sniffs a lyrics text: TTML xml, LRC (maybe enhanced) or plain. Never invents timing. */
internal fun docFromText(source: String, text: String, confidence: Float, durationSec: Int): ProviderOutcome {
    val body = text.removePrefix("﻿").trim()
    if (body.isEmpty()) return ProviderOutcome.NotFound
    val lines = when {
        body.startsWith("<") -> parseTtml(body)
        else -> parseLrc(body)
    }
    if (lines.isNotEmpty()) {
        if (!LyricsMatcher.timingFits(lines, durationSec)) return ProviderOutcome.InvalidResponse("timing")
        return ProviderOutcome.Success(LyricsDoc(source, lines, null, confidence))
    }
    if (body.startsWith("<")) return ProviderOutcome.InvalidResponse("unreadable ttml")
    return ProviderOutcome.Success(LyricsDoc(source, emptyList(), body, confidence * PLAIN_FACTOR))
}

private const val PLAIN_FACTOR = 0.8f

/**
 * LyricsPlus (a.k.a. YouLyPlus): community mirrors answering `v1/ttml/get` (word-timed TTML) and
 * `v2/lyrics/get` (JSON with line and syllable times in ms). Mirrors are tried in order; at most
 * [maxRequests] requests per song so a dead mirror cannot eat the race time budget.
 */
class LyricsPlusProvider(
    private val http: LyricsHttp,
    private val mirrors: List<String> = DEFAULT_MIRRORS,
    private val timeoutMs: Int = 4_000,
    private val maxRequests: Int = 4,
) : LyricsProvider {
    override val id = LyricsProviders.LYRICSPLUS
    override val capabilities = LyricsProviders.capabilities(id)

    override suspend fun fetch(query: LyricsQuery): ProviderOutcome = guarded {
        // The service needs both title and artist.
        if (query.title.isBlank() || query.artist.isBlank()) return@guarded ProviderOutcome.NotFound
        val qs = buildString {
            append("?title=").append(enc(query.title)).append("&artist=").append(enc(query.artist))
            if (query.album.isNotBlank()) append("&album=").append(enc(query.album))
            if (query.durationSec > 0) append("&duration=").append(query.durationSec)
        }
        var requests = 0
        var network: String? = null
        var busy: Int? = null
        for (path in listOf("v1/ttml/get", "v2/lyrics/get")) {
            for (base in mirrors) {
                if (requests >= maxRequests) break
                requests++
                val reply = try {
                    http.get(base.trimEnd('/') + "/" + path + qs, timeoutMs)
                } catch (e: java.io.IOException) {
                    network = e.javaClass.simpleName
                    continue
                }
                if (reply.code == 429) { busy = 429; continue }
                if (reply.code !in 200..299) continue
                val outcome = if (path.startsWith("v1")) fromTtml(query, reply.body) else fromJson(query, reply.body)
                if (outcome is ProviderOutcome.Success) return@guarded outcome
            }
        }
        val b = busy
        val n = network
        when {
            b != null -> ProviderOutcome.RateLimited(b)
            n != null && requests > 0 -> ProviderOutcome.NetworkError(n)
            else -> ProviderOutcome.NotFound
        }
    }

    internal fun fromTtml(query: LyricsQuery, body: String): ProviderOutcome {
        val raw = body.removePrefix("﻿").trim()
        val ttml = if (raw.startsWith("<")) raw else MiniJson.str(MiniJson.obj(MiniJson.parse(body))?.get("ttml")).orEmpty().trim()
        if (!ttml.startsWith("<")) return ProviderOutcome.NotFound
        val lines = parseTtml(ttml)
        if (lines.isEmpty()) return ProviderOutcome.InvalidResponse("no lines")
        if (!LyricsMatcher.timingFits(lines, query.durationSec)) return ProviderOutcome.InvalidResponse("timing")
        return ProviderOutcome.Success(LyricsDoc(id, lines, null, CONFIDENCE))
    }

    internal fun fromJson(query: LyricsQuery, body: String): ProviderOutcome {
        val json = MiniJson.obj(MiniJson.parse(body)) ?: return ProviderOutcome.InvalidResponse("not json")
        val items = MiniJson.list(json["lyrics"])?.mapNotNull { MiniJson.obj(it) }.orEmpty()
        if (items.isEmpty()) return ProviderOutcome.NotFound
        val wordType = MiniJson.str(json["type"]).equals("Word", ignoreCase = true)
        val timed = items.filter { it["time"] is Double }
        if (timed.isEmpty()) {
            val plain = items.mapNotNull { MiniJson.str(it["text"])?.trim() }.joinToString("\n").trim()
            if (plain.isEmpty()) return ProviderOutcome.NotFound
            return ProviderOutcome.Success(LyricsDoc(id, emptyList(), plain, CONFIDENCE * PLAIN_FACTOR))
        }
        val lines = timed.map { item ->
            val start = (item["time"] as Double).toLong()
            val dur = (item["duration"] as? Double)?.toLong() ?: -1L
            val syl = if (wordType) {
                MiniJson.list(item["syllabus"])?.mapNotNull { MiniJson.obj(it) }.orEmpty().mapNotNull { s ->
                    val raw = MiniJson.str(s["text"]) ?: return@mapNotNull null
                    val t = (s["time"] as? Double)?.toLong() ?: return@mapNotNull null
                    val d = (s["duration"] as? Double)?.toLong() ?: 0L
                    if (raw.isBlank()) null else LyricWord(t, t + d, raw.trim(), raw.last().isWhitespace())
                }
            } else {
                emptyList()
            }
            val text = if (syl.isNotEmpty()) {
                syl.joinToString("") { it.text + if (it.spaceAfter) " " else "" }.trim()
            } else {
                MiniJson.str(item["text"]).orEmpty().trim()
            }
            LyricLine(start, text, syl, if (dur > 0) start + dur else -1L)
        }.sortedBy { it.timeMs }
        if (!LyricsMatcher.timingFits(lines, query.durationSec)) return ProviderOutcome.InvalidResponse("timing")
        return ProviderOutcome.Success(LyricsDoc(id, lines, null, CONFIDENCE))
    }

    companion object {
        const val CONFIDENCE = 0.75f
        val DEFAULT_MIRRORS = listOf(
            "https://lyricsplus.binimum.org/",
            "https://lyricsplus.prjktla.my.id/",
            "https://lyricsplus.atomix.one/",
            "https://lyricsplus-seven.vercel.app/",
        )
    }
}

/**
 * Unison (https://unison.boidu.dev, community lyrics with votes): `GET /lyrics?song&artist&album&duration`
 * answers `{success, data{song, artist, lyrics, format, syncType}}`. The text is sniffed (TTML, LRC or
 * plain) rather than trusting the `format` label, and the record's song/artist must match the request.
 */
class UnisonProvider(
    private val http: LyricsHttp,
    private val baseUrl: String = "https://unison.boidu.dev",
    private val timeoutMs: Int = 6_000,
) : LyricsProvider {
    override val id = LyricsProviders.UNISON
    override val capabilities = LyricsProviders.capabilities(id)

    override suspend fun fetch(query: LyricsQuery): ProviderOutcome = guarded {
        if (query.title.isBlank() || query.artist.isBlank()) return@guarded ProviderOutcome.NotFound
        val url = buildString {
            append(baseUrl).append("/lyrics?song=").append(enc(query.title)).append("&artist=").append(enc(query.artist))
            if (query.album.isNotBlank()) append("&album=").append(enc(query.album))
            if (query.durationSec > 0) append("&duration=").append(query.durationSec)
        }
        val reply = http.get(url, timeoutMs)
        when {
            reply.code == 429 -> ProviderOutcome.RateLimited(429)
            reply.code == 404 -> ProviderOutcome.NotFound
            reply.code !in 200..299 -> ProviderOutcome.NetworkError("HTTP ${reply.code}")
            else -> parse(query, reply.body)
        }
    }

    internal fun parse(query: LyricsQuery, body: String): ProviderOutcome {
        val json = MiniJson.obj(MiniJson.parse(body)) ?: return ProviderOutcome.InvalidResponse("not json")
        if (json["success"] != true) return ProviderOutcome.NotFound
        val data = MiniJson.obj(json["data"]) ?: return ProviderOutcome.NotFound
        val text = MiniJson.str(data["lyrics"])?.takeIf { it.isNotBlank() } ?: return ProviderOutcome.NotFound
        val confidence = LyricsMatcher.score(query, MiniJson.str(data["song"]), MiniJson.str(data["artist"]), null)
            ?: return ProviderOutcome.InvalidResponse("wrong song")
        return docFromText(id, text, confidence, query.durationSec)
    }
}

/**
 * KuGou (public lyrics endpoints used by the KuGou desktop client): search the song, pick hits whose
 * length is within [DURATION_TOLERANCE_S] of the request, look up lyric candidates by hash, then
 * download (base64 LRC). Line-synced only. Credit lines ("作词 : ...") at the head or tail are dropped.
 */
class KuGouProvider(
    private val http: LyricsHttp,
    private val timeoutMs: Int = 5_000,
    private val maxSongs: Int = 3,
) : LyricsProvider {
    override val id = LyricsProviders.KUGOU
    override val capabilities = LyricsProviders.capabilities(id)

    override suspend fun fetch(query: LyricsQuery): ProviderOutcome = guarded {
        if (query.title.isBlank()) return@guarded ProviderOutcome.NotFound
        val keyword = enc("${cleanTitle(query.title)} - ${cleanArtist(query.artist)}".trim().trimEnd('-').trim())
        val search = http.get(
            "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=$keyword",
            timeoutMs,
        )
        if (search.code == 429) return@guarded ProviderOutcome.RateLimited(429)
        if (search.code !in 200..299) return@guarded ProviderOutcome.NetworkError("HTTP ${search.code}")
        val songs = MiniJson.list(MiniJson.obj(MiniJson.obj(MiniJson.parse(search.body))?.get("data"))?.get("info"))
            ?.mapNotNull { MiniJson.obj(it) }.orEmpty()
        var tried = 0
        for (song in songs) {
            val hash = MiniJson.str(song["hash"]) ?: continue
            val dur = (song["duration"] as? Double)
            if (query.durationSec > 0 && dur != null && abs(dur - query.durationSec) > DURATION_TOLERANCE_S) continue
            if (tried++ >= maxSongs) break
            val cand = candidate("hash=${enc(hash)}") ?: continue
            download(query, cand)?.let { return@guarded it }
        }
        // Fallback: lyric search by keyword (+ duration in ms).
        val dur = if (query.durationSec > 0) "&duration=${query.durationSec * 1000}" else ""
        val cand = candidate("keyword=$keyword$dur")
        cand?.let { download(query, it) } ?: ProviderOutcome.NotFound
    }

    private suspend fun candidate(params: String): Pair<String, String>? {
        val r = http.get("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&$params", timeoutMs)
        if (r.code !in 200..299) return null
        val c = MiniJson.list(MiniJson.obj(MiniJson.parse(r.body))?.get("candidates"))
            ?.firstNotNullOfOrNull { MiniJson.obj(it) } ?: return null
        val id = (c["id"] as? Double)?.toLong()?.toString() ?: MiniJson.str(c["id"]) ?: return null
        val key = MiniJson.str(c["accesskey"]) ?: return null
        return id to key
    }

    private suspend fun download(query: LyricsQuery, cand: Pair<String, String>): ProviderOutcome? {
        val r = http.get(
            "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=${enc(cand.first)}&accesskey=${enc(cand.second)}",
            timeoutMs,
        )
        if (r.code !in 200..299) return null
        return decode(query, r.body)
    }

    internal fun decode(query: LyricsQuery, body: String): ProviderOutcome? {
        val content = MiniJson.str(MiniJson.obj(MiniJson.parse(body))?.get("content")) ?: return null
        val text = try {
            String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return ProviderOutcome.InvalidResponse("base64")
        }
        val lines = parseLrc(trimCredits(text.replace("&apos;", "'")))
        if (lines.isEmpty()) return null
        if (!LyricsMatcher.timingFits(lines, query.durationSec)) return ProviderOutcome.InvalidResponse("timing")
        return ProviderOutcome.Success(LyricsDoc(id, lines, null, CONFIDENCE))
    }

    internal fun trimCredits(text: String): String {
        val all = text.lines().filter { ACCEPTED.matches(it) }
        var head = 0
        for (i in minOf(HEAD_LIMIT, all.lastIndex) downTo 0) {
            if (BANNED.matches(all[i])) { head = i + 1; break }
        }
        val rest = all.drop(head)
        var tail = 0
        for (i in minOf(rest.size - HEAD_LIMIT, rest.lastIndex) downTo 0) {
            if (BANNED.matches(rest[rest.lastIndex - i])) { tail = i + 1; break }
        }
        return rest.dropLast(tail).joinToString("\n")
    }

    internal fun cleanTitle(s: String) = s.replace(Regex("""\(.*\)|（.*）|「.*」|『.*』|<.*>|《.*》"""), "").trim()
    internal fun cleanArtist(s: String) = s.replace(", ", "、").replace(" & ", "、").replace(Regex("""\(.*\)|（.*）"""), "").trim()

    private companion object {
        const val CONFIDENCE = 0.55f
        const val DURATION_TOLERANCE_S = 8.0
        const val HEAD_LIMIT = 30
        val ACCEPTED = Regex("""\[(\d\d):(\d\d)\.(\d{2,3})\].*""")
        val BANNED = Regex(""".+].+[:：].+""")
    }
}
