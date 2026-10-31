package com.dopavin.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What a provider is asked for. Plain data, no Android types, so providers are unit-testable. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val videoId: String?,
    /** The title as the song carries it (brackets and all), kept to tell versions apart (remix, live ...). Blank = same as [title]. */
    val rawTitle: String = "",
    /** The artist was dropped to widen the search, so a hit must match the title closely (see [LyricsFallback]). */
    val strict: Boolean = false,
)

/** Lyrics a provider found, with how sure we are that they belong to the requested song. */
data class LyricsDoc(
    val source: String,
    val lines: List<LyricLine>,
    val plain: String?,
    val confidence: Float,
    val instrumental: Boolean = false,
) {
    val kind: LyricsKind? get() = kindOf(lines, plain)
    val usable: Boolean get() = kind != null
}

/** Everything one provider can answer; failures are values, only cancellation is an exception. */
sealed interface ProviderOutcome {
    data class Success(val doc: LyricsDoc) : ProviderOutcome
    data class Instrumental(val source: String) : ProviderOutcome
    data object NotFound : ProviderOutcome
    data class NetworkError(val detail: String?) : ProviderOutcome
    data class RateLimited(val code: Int) : ProviderOutcome
    data class InvalidResponse(val detail: String?) : ProviderOutcome
    data object Unsupported : ProviderOutcome
}

/** One lyrics source. [fetch] never throws except for coroutine cancellation. */
interface LyricsProvider {
    val id: String
    val capabilities: Set<LyricsKind>
    suspend fun fetch(query: LyricsQuery): ProviderOutcome
}

/** The providers Dopavin really has, with what each one can deliver. */
object LyricsProviders {
    const val CHOOSE = "choose"
    const val BETTER = "better"
    const val LRCLIB = "lrclib"
    const val PORTATO = "portato"
    const val LYRICSPLUS = "lyricsplus"
    const val UNISON = "unison"
    const val KUGOU = "kugou"

    val all: List<String> = listOf(BETTER, PORTATO, LYRICSPLUS, UNISON, KUGOU, LRCLIB)

    fun capabilities(id: String): Set<LyricsKind> = when (id) {
        BETTER -> setOf(LyricsKind.WordSynced, LyricsKind.LineSynced)
        LRCLIB -> setOf(LyricsKind.LineSynced, LyricsKind.Plain)
        PORTATO -> setOf(LyricsKind.WordSynced, LyricsKind.LineSynced)
        LYRICSPLUS -> setOf(LyricsKind.WordSynced, LyricsKind.LineSynced)
        UNISON -> setOf(LyricsKind.LineSynced, LyricsKind.Plain)
        KUGOU -> setOf(LyricsKind.LineSynced)
        else -> emptySet()
    }

    /** Providers that answer nothing without an artist, so artist-less fallback queries skip them. */
    fun needsArtist(id: String): Boolean = id == LYRICSPLUS || id == UNISON

    /** Stored selections that no longer exist fall back to the race. */
    fun normalise(selection: String?): String = if (selection != null && selection in all) selection else CHOOSE
}

/** HTTP seam so providers can be tested with fake replies. */
data class HttpReply(val code: Int, val body: String)

interface LyricsHttp {
    /** Throws [IOException] when the network fails; HTTP error codes are returned, not thrown. */
    suspend fun get(url: String, timeoutMs: Int): HttpReply
}

class UrlConnectionHttp : LyricsHttp {
    override suspend fun get(url: String, timeoutMs: Int): HttpReply = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "Dopavin/1.0 (Android music player)")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            HttpReply(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}

internal fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

/** Runs [block] mapping IO failures to a [ProviderOutcome.NetworkError]. Cancellation passes through. */
internal suspend inline fun guarded(block: () -> ProviderOutcome): ProviderOutcome = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    ProviderOutcome.NetworkError(e.javaClass.simpleName)
} catch (e: Exception) {
    ProviderOutcome.InvalidResponse(e.javaClass.simpleName)
}

/**
 * Better Lyrics (https://api.betterlyrics.org, the Better Lyrics extension's public API): answers
 * JSON with a `ttml` field that carries word/syllable timing. Per its docs a keyless cache miss
 * replies 401 and a request missing song and artist replies 422 - both are "no lyrics here".
 */
class BetterLyricsProvider(
    private val http: LyricsHttp,
    private val baseUrl: String = "https://api.betterlyrics.org",
    private val timeoutMs: Int = 6_000,
    private val retryDelayMs: Long = 1_500,
    override val id: String = LyricsProviders.BETTER,
    private val path: String = "/getLyrics",
    private val sendVideoId: Boolean = true,
) : LyricsProvider {
    override val capabilities = LyricsProviders.capabilities(id)

    override suspend fun fetch(query: LyricsQuery): ProviderOutcome = guarded {
        if (query.title.isBlank()) return@guarded ProviderOutcome.NotFound
        val url = buildString {
            append(baseUrl).append(path).append("?s=").append(enc(query.title))
            append("&a=").append(enc(query.artist))
            append("&al=").append(enc(query.album))
            append("&d=").append(query.durationSec)
            if (sendVideoId && query.videoId != null) append("&v=").append(enc(query.videoId))
        }
        var reply = http.get(url, timeoutMs)
        if (reply.code == 429) {
            delay(retryDelayMs)
            reply = http.get(url, timeoutMs)
        }
        when {
            reply.code == 429 -> ProviderOutcome.RateLimited(429)
            reply.code == 401 || reply.code == 404 || reply.code == 422 -> ProviderOutcome.NotFound
            reply.code !in 200..299 -> ProviderOutcome.NetworkError("HTTP ${reply.code}")
            else -> parse(query, reply.body)
        }
    }

    internal fun parse(query: LyricsQuery, body: String): ProviderOutcome {
        val raw = body.removePrefix("\uFEFF").trim()
        val ttml = if (raw.startsWith("<")) {
            raw
        } else {
            val json = MiniJson.obj(MiniJson.parse(body)) ?: return ProviderOutcome.InvalidResponse("not json")
            MiniJson.str(json["ttml"]).orEmpty()
        }
        if (ttml.isBlank()) return ProviderOutcome.NotFound
        val lines = parseTtml(ttml)
        if (lines.isEmpty()) return ProviderOutcome.InvalidResponse("no lines")
        if (!LyricsMatcher.timingFits(lines, query.durationSec)) return ProviderOutcome.InvalidResponse("timing")
        // The service matches by song/artist/duration itself and returns no metadata to compare.
        return ProviderOutcome.Success(LyricsDoc(id, lines, null, BETTER_CONFIDENCE))
    }

    private companion object {
        const val BETTER_CONFIDENCE = 0.8f
    }
}

/**
 * LRCLIB (https://lrclib.net/api, free, no key): exact `get` lookups then loose `search`, every hit
 * checked against the requested song (title, artist, duration) before it can win. Synced lyrics
 * (LRC, sometimes enhanced with word timing) are preferred over plain text. It answers 503 under
 * load; one retry, then the outcome is [ProviderOutcome.RateLimited] (never "not found").
 */
class LrclibProvider(
    private val http: LyricsHttp,
    private val baseUrl: String = "https://lrclib.net/api",
    private val timeoutMs: Int = 12_000,
    private val retryDelayMs: Long = 600,
) : LyricsProvider {
    override val id = LyricsProviders.LRCLIB
    override val capabilities = LyricsProviders.capabilities(id)

    private sealed interface Step {
        data class Hit(val body: String) : Step
        data object Miss : Step
        data class Busy(val code: Int) : Step
    }

    private suspend fun call(url: String): Step {
        var reply = http.get(url, timeoutMs)
        if (reply.code == 429 || reply.code in 500..599) {
            delay(retryDelayMs)
            reply = http.get(url, timeoutMs)
        }
        return when {
            reply.code in 200..299 -> Step.Hit(reply.body)
            reply.code == 404 -> Step.Miss
            else -> Step.Busy(reply.code)
        }
    }

    override suspend fun fetch(query: LyricsQuery): ProviderOutcome = guarded {
        if (query.title.isBlank()) return@guarded ProviderOutcome.NotFound
        var busy: Int? = null
        fun consider(json: Map<String, Any?>): ProviderOutcome? =
            toOutcome(query, json).takeIf { it is ProviderOutcome.Success || it is ProviderOutcome.Instrumental }

        if (query.artist.isNotBlank()) {
            val withAlbum = buildString {
                append(baseUrl).append("/get?track_name=").append(enc(query.title))
                append("&artist_name=").append(enc(query.artist))
                if (query.album.isNotBlank()) append("&album_name=").append(enc(query.album))
                append("&duration=").append(query.durationSec)
            }
            when (val r = call(withAlbum)) {
                is Step.Hit -> MiniJson.obj(MiniJson.parse(r.body))?.let(::consider)?.let { return@guarded it }
                is Step.Busy -> busy = r.code
                Step.Miss -> {}
            }
            if (query.album.isNotBlank()) {
                val noAlbum = "$baseUrl/get?track_name=${enc(query.title)}&artist_name=${enc(query.artist)}&duration=${query.durationSec}"
                when (val r = call(noAlbum)) {
                    is Step.Hit -> MiniJson.obj(MiniJson.parse(r.body))?.let(::consider)?.let { return@guarded it }
                    is Step.Busy -> if (busy == null) busy = r.code
                    Step.Miss -> {}
                }
            }
        }
        val searches = buildList {
            if (query.artist.isNotBlank()) add("$baseUrl/search?track_name=${enc(query.title)}&artist_name=${enc(query.artist)}")
            add("$baseUrl/search?q=" + enc("${query.title} ${query.artist}".trim()))
        }
        for (url in searches) {
            val body = when (val r = call(url)) {
                is Step.Hit -> r.body
                is Step.Busy -> { if (busy == null) busy = r.code; continue }
                Step.Miss -> continue
            }
            val hits = MiniJson.list(MiniJson.parse(body)) ?: continue
            val candidates = hits.mapNotNull { MiniJson.obj(it) }
                .mapNotNull { json -> (toOutcome(query, json) as? ProviderOutcome.Success)?.doc }
            // Best match first; a synced hit beats a plain one with similar confidence.
            val best = candidates.maxWithOrNull(
                compareBy<LyricsDoc>({ rank(it.kind) }, { it.confidence }),
            )
            if (best != null) return@guarded ProviderOutcome.Success(best)
        }
        val code = busy
        if (code != null) ProviderOutcome.RateLimited(code) else ProviderOutcome.NotFound
    }

    private fun rank(kind: LyricsKind?) = when (kind) {
        LyricsKind.WordSynced -> 3
        LyricsKind.LineSynced -> 2
        LyricsKind.Plain -> 1
        null -> 0
    }

    /** One LRCLIB record to an outcome; a record of another song is [ProviderOutcome.InvalidResponse]. */
    internal fun toOutcome(query: LyricsQuery, json: Map<String, Any?>): ProviderOutcome {
        val confidence = LyricsMatcher.score(
            query,
            MiniJson.str(json["trackName"]) ?: MiniJson.str(json["name"]),
            MiniJson.str(json["artistName"]),
            (json["duration"] as? Double),
        ) ?: return ProviderOutcome.InvalidResponse("wrong song")
        if (json["instrumental"] == true) return ProviderOutcome.Instrumental(id)
        val synced = MiniJson.str(json["syncedLyrics"])?.takeIf { it.isNotBlank() }
        val plain = MiniJson.str(json["plainLyrics"])?.takeIf { it.isNotBlank() }
        val lines = synced?.let(::parseLrc).orEmpty()
        if (lines.isEmpty() && plain == null) return ProviderOutcome.NotFound
        return ProviderOutcome.Success(LyricsDoc(id, lines, plain, confidence))
    }
}

/** Decides whether a provider's answer is about the song that was asked for. */
object LyricsMatcher {
    private val noise = Regex("""[\(\[][^)\]]*[\)\]]""")
    private val feat = Regex("""(?i)\s+(feat\.?|ft\.?)\s+.*$""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    fun normalise(s: String): String =
        s.replace(noise, " ").replace(feat, "").lowercase().replace(nonWord, " ").trim()

    private val VERSION_WORDS = Regex(
        """(?i)\b(remix|live|acoustic|instrumental|karaoke|cover|unplugged|mashup|reprise|slowed|reverb|sped\s*up|lofi|lo-fi|remaster(?:ed)?|demo)\b""",
    )
    private val HARD_VERSIONS = setOf("remix", "instrumental", "karaoke", "cover", "mashup", "slowed", "reverb", "sped up", "spedup", "lofi", "lo-fi")

    /** Version words in a raw title ("Song (Live)", "Song - Remix"), lower-cased. */
    fun versionTags(raw: String): Set<String> =
        VERSION_WORDS.findAll(raw).map { it.value.lowercase().replace(Regex("\\s+"), " ").let { v -> if (v.startsWith("remaster")) "remaster" else v } }.toSet()

    /** "love" inside "love story" is another song; a longer shared title ("tum hi ho" in "tum hi ho aashiqui 2") is the same one. */
    private fun containmentIsClose(a: String, b: String): Boolean {
        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        val shortWords = short.split(' ').size
        return shortWords >= 2 && short.length * 10 >= long.length * 4
    }

    private fun tokens(s: String): Set<String> = normalise(s).split(' ').filter { it.length > 1 }.toSet()

    /**
     * Confidence 0..1 that a record (title, artist, duration in seconds; any may be null when the
     * source does not say) is the requested song, or null when it clearly is not.
     */
    fun score(q: LyricsQuery, title: String?, artist: String?, durationSec: Double?): Float? {
        var score = 0f
        if (title != null) {
            val a = normalise(q.title)
            val b = normalise(title)
            score += when {
                a.isEmpty() || b.isEmpty() -> return null
                a == b -> 0.5f
                (a.contains(b) || b.contains(a)) && minOf(a.length, b.length) >= 3 && containmentIsClose(a, b) -> 0.35f
                else -> return null
            }
            // A remix, karaoke or cover is another recording: its lyrics must not stand in for the original, and the other way round.
            val versions = versionTags(q.rawTitle.ifBlank { q.title })
            val theirs = versionTags(title)
            if (theirs.any { it in HARD_VERSIONS && it !in versions }) return null
            if (versions.any { it in HARD_VERSIONS && it !in theirs }) return null
            if (theirs != versions) score -= 0.05f
        } else {
            score += 0.25f
        }
        if (artist != null && q.artist.isNotBlank()) {
            score += if (tokens(q.artist).intersect(tokens(artist)).isNotEmpty() ||
                normalise(q.artist) == normalise(artist)
            ) {
                0.3f
            } else {
                return null
            }
        } else {
            score += 0.15f
        }
        if (durationSec != null && q.durationSec > 0 && durationSec > 0) {
            val diff = kotlin.math.abs(durationSec - q.durationSec)
            if (diff > DURATION_REJECT_S) return null
            score += if (diff <= 3) 0.2f else 0.1f
        } else {
            score += 0.1f
        }
        return score.coerceIn(0f, 1f)
    }

    /** Timed lyrics cannot run (much) longer than the song; otherwise they belong to another one. */
    fun timingFits(lines: List<LyricLine>, durationSec: Int): Boolean {
        if (durationSec <= 0) return true
        val first = lines.firstOrNull()?.timeMs ?: return false
        return first <= durationSec * 1000L && lines.last().timeMs <= durationSec * 1000L + TIMING_SLACK_MS
    }

    private const val DURATION_REJECT_S = 8.0
    private const val TIMING_SLACK_MS = 15_000L
}
