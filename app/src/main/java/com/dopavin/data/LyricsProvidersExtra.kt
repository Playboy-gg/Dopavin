package com.dopavin.data
import java.util.Base64
import kotlin.math.abs
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
class LyricsPlusProvider(
    private val http: LyricsHttp,
    private val mirrors: List<String> = DEFAULT_MIRRORS,
    private val timeoutMs: Int = 4_000,
    private val maxRequests: Int = 4,
) : LyricsProvider {
    override val id = LyricsProviders.LYRICSPLUS
    override val capabilities = LyricsProviders.capabilities(id)
    override suspend fun fetch(query: LyricsQuery): ProviderOutcome = guarded {
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
class LrcRedProvider(
    private val http: LyricsHttp,
    private val timeoutMs: Int = 5_000,
) : LyricsProvider {
    override val id = LyricsProviders.LRCRED
    override val capabilities = LyricsProviders.capabilities(id)
    override suspend fun fetch(query: LyricsQuery): ProviderOutcome = guarded {
        if (query.title.isBlank() || query.artist.isBlank()) return@guarded ProviderOutcome.NotFound
        var outcome: ProviderOutcome = ProviderOutcome.NotFound
        for (step in 0..1) {
            val url = (if (step == 0) biniDocument(query) else searchDocument(query)) ?: continue
            val reply = http.get(url, timeoutMs)
            if (reply.code == 429) return@guarded ProviderOutcome.RateLimited(429)
            if (reply.code !in 200..299) continue
            val o = docFromText(id, reply.body, CONFIDENCE, query.durationSec)
            if (o is ProviderOutcome.Success) return@guarded o
            outcome = o
        }
        outcome
    }
    private suspend fun biniDocument(q: LyricsQuery): String? {
        val url = buildString {
            append(BINI).append("/?track=").append(enc(q.title)).append("&artist=").append(enc(q.artist))
            if (q.album.isNotBlank()) append("&album=").append(enc(q.album))
            if (q.durationSec > 0) append("&duration=").append(q.durationSec)
        }
        val reply = http.get(url, timeoutMs)
        if (reply.code !in 200..299) return null
        return pickBini(q, reply.body)
    }
    private suspend fun searchDocument(q: LyricsQuery): String? {
        val reply = http.get("$LRC_RED/search.json?q=" + enc("${q.title} ${q.artist}".trim()), timeoutMs)
        if (reply.code !in 200..299) return null
        return pickSearch(q, reply.body)
    }
    companion object {
        private const val BINI = "https://lyrics-api.binimum.org"
        private const val LRC_RED = "https://lrc.red"
        private const val CONFIDENCE = 0.9f
        private const val DURATION_TOLERANCE_S = 3.0
        private val isrcShape = Regex("[A-Z]{2}[A-Z0-9]{3}\\d{7}")
        internal fun documentUrl(isrc: String?): String? {
            val v = isrc?.trim()?.uppercase() ?: return null
            return v.takeIf { isrcShape.matches(it) }?.let { "$LRC_RED/s/$it.ttml" }
        }
        internal fun safeLrcRed(url: String?): String? {
            val u = url?.trim() ?: return null
            return u.takeIf { it.startsWith("https://lrc.red/") || it.startsWith("https://www.lrc.red/") }
        }
        private fun fits(q: LyricsQuery, title: String?, artist: String?, duration: Double?): Boolean {
            if (LyricsMatcher.score(q, title, artist, duration) == null) return false
            val wanted = TrackMatch.versions(q.rawTitle.ifBlank { q.title })
            return TrackMatch.versions(title.orEmpty()) == wanted
        }
        internal fun pickBini(q: LyricsQuery, body: String): String? {
            val results = MiniJson.list(MiniJson.obj(MiniJson.parse(body))?.get("results")) ?: return null
            for (r in results) {
                val o = MiniJson.obj(r) ?: continue
                if (!fits(q, MiniJson.str(o["track_name"]), MiniJson.str(o["artist_name"]), o["duration"] as? Double)) continue
                safeLrcRed(MiniJson.str(o["lyricsUrl"]))?.let { return it }
                documentUrl(MiniJson.str(o["isrc"]))?.let { return it }
            }
            return null
        }
        internal fun pickSearch(q: LyricsQuery, body: String): String? {
            val hits = MiniJson.list(MiniJson.obj(MiniJson.parse(body))?.get("hits")) ?: return null
            var best: Pair<String, Double>? = null
            for (h in hits) {
                val o = MiniJson.obj(h) ?: continue
                val duration = o["duration"] as? Double
                if (!fits(q, MiniJson.str(o["title"]), MiniJson.str(o["artist"]), duration)) continue
                val url = documentUrl(MiniJson.str(o["isrc"])) ?: continue
                val gap = if (q.durationSec > 0 && duration != null) abs(duration - q.durationSec) else 0.0
                if (gap > DURATION_TOLERANCE_S) continue
                if (best == null || gap < best.second) best = url to gap
            }
            return best?.first
        }
    }
}
