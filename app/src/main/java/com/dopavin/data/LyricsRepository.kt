package com.dopavin.data

import android.content.Context
import android.util.Log
import com.dopavin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed interface LyricsResult {
    data class Found(
        val synced: List<LyricLine>,
        val plain: String?,
        val source: String? = null,
        val kind: LyricsKind? = null,
    ) : LyricsResult
    data object Instrumental : LyricsResult
    data object NotFound : LyricsResult
    data object Disabled : LyricsResult

    /** The song may have lyrics but we could not load them. [kind] picks the friendly message the
     * screen shows, [detail] is the raw code for problem reports ("HTTP 503"). */
    data class Failed(val kind: Kind, val detail: String?) : LyricsResult {
        enum class Kind { ServerBusy, Offline, Timeout, Other }
    }
}

/**
 * Lyrics orchestration (F-141). Providers live in LyricsProvider.kt; this class
 *  - builds the query from the song,
 *  - serves a cached result first (per song AND provider choice, versioned, with expiry),
 *  - runs the "choose" race over all providers ([raceLyrics]) or one chosen provider,
 *  - caches only real answers: a found result, instrumental, or a clean "no lyrics" (7 days).
 *    Network errors, rate limits, timeouts and invalid answers are never cached.
 * Sends title, artist, album, duration (and the video id of online songs) - nothing else.
 * Logs provider, duration and outcome category only, never lyrics or personal data.
 */
class LyricsRepository(
    context: Context,
    private val providers: List<LyricsProvider> = listOf(
        LrcRedProvider(UrlConnectionHttp()),
        BetterLyricsProvider(UrlConnectionHttp()),
        BetterLyricsProvider(
            UrlConnectionHttp(),
            baseUrl = "https://lyrics-api.boidu.dev",
            path = "/qq/getLyrics",
            id = LyricsProviders.PORTATO,
            sendVideoId = false,
        ),
        LyricsPlusProvider(UrlConnectionHttp()),
        UnisonProvider(UrlConnectionHttp()),
        KuGouProvider(UrlConnectionHttp()),
        LrclibProvider(UrlConnectionHttp()),
    ),
    private val raceConfig: RaceConfig = RaceConfig(),
) {

    private val unknownTitle = context.applicationContext.getString(R.string.unknown_title)
    private val unknownArtist = context.applicationContext.getString(R.string.unknown_artist)
    private val unknownAlbum = context.applicationContext.getString(R.string.unknown_album)

    private val dir = File(context.applicationContext.filesDir, "lyrics").apply { mkdirs() }

    private val fallback = LyricsFallback(config = raceConfig)

    /** Own scope: a lookup keeps running (and lands in the cache) even when the screen that asked for it moved on. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flights = SingleFlight<String, LyricsResult>(scope)

    /**
     * [selection] is "choose" (race) or a provider id. [refresh] skips the cache read (retry, or a
     * manual provider switch), the new answer still replaces the cached one when it is a real result.
     * Two callers asking for the same song and choice at once share one search ([SingleFlight]).
     */
    suspend fun load(
        song: Song,
        allowNetwork: Boolean,
        selection: String = LyricsProviders.CHOOSE,
        refresh: Boolean = false,
    ): LyricsResult = withContext(Dispatchers.IO) {
        val choice = LyricsProviders.normalise(selection)
        File(dir, "${song.id}.json").delete() // pre-F-141 cache layout
        if (!refresh) readCache(song.id, choice)?.let { return@withContext it }
        if (!allowNetwork) return@withContext LyricsResult.Disabled
        val plan = planOf(song)
        if (plan.isEmpty()) return@withContext LyricsResult.NotFound
        // An expired lyrics copy is only a floor: if the new search finds nothing better (or fails), the old
        // one is kept, so refreshing the cache can never make the lyrics worse.
        val stale = if (refresh) null else readStale(song.id, choice)
        flights.run("${song.id}|$choice") {
            val started = System.currentTimeMillis()
            val result = search(song.id, plan, choice, refresh)
            Log.d(TAG, "lyrics choice=$choice queries=${plan.size} ms=${System.currentTimeMillis() - started} result=${result.javaClass.simpleName}")
            if (stale != null && result !is LyricsResult.Instrumental && rankOf(result) < rankOf(stale)) {
                writeKinds(song.id, choice, stale) // renews its timestamp
                stale
            } else {
                result
            }
        }
    }

    /** The searches for this song, best first (see [LyricsQueryPlan]); the song itself is never changed. */
    private fun planOf(song: Song): List<LyricsQuery> = LyricsQueryPlan.build(
        LyricsQueryPlan.Meta(
            title = song.title.takeUnless { it == unknownTitle }.orEmpty(),
            artist = song.artist.takeUnless { it == unknownArtist }.orEmpty(),
            album = song.album.takeUnless { it == unknownAlbum }.orEmpty(),
            durationSec = (song.durationMs / 1000L).toInt(),
            videoId = song.videoId,
        ),
    )

    /** Runs the fallback pipeline over every provider ("choose") or only the one the user picked. */
    private suspend fun search(songId: Long, plan: List<LyricsQuery>, choice: String, refresh: Boolean): LyricsResult {
        val chosen = if (choice == LyricsProviders.CHOOSE) providers else providers.filter { it.id == choice }
        if (chosen.isEmpty()) return LyricsResult.NotFound
        val r = fallback.run(plan, chosen, ignoreMisses = refresh)
        r.outcomes.forEach { (id, o) -> Log.d(TAG, "provider=$id outcome=${o.javaClass.simpleName}") }
        val all = choice == LyricsProviders.CHOOSE
        return when (r) {
            is RaceResult.Won -> found(songId, r.doc, alsoChoose = all)
            is RaceResult.Instrumental -> LyricsResult.Instrumental.also { writeKinds(songId, choice, it) }
            is RaceResult.NoLyrics -> LyricsResult.NotFound.also {
                if (r.outcomes.values.all { o -> o is ProviderOutcome.NotFound || o is ProviderOutcome.InvalidResponse }) {
                    writeKinds(songId, choice, it)
                }
            }
            is RaceResult.TimedOut -> LyricsResult.Failed(LyricsResult.Failed.Kind.Timeout, "timeout")
            is RaceResult.Failed -> failedFrom(r.outcomes.values)
        }
    }

    private fun failedFrom(outcomes: Collection<ProviderOutcome>): LyricsResult {
        outcomes.filterIsInstance<ProviderOutcome.RateLimited>().firstOrNull()?.let {
            return LyricsResult.Failed(LyricsResult.Failed.Kind.ServerBusy, "HTTP ${it.code}")
        }
        val net = outcomes.filterIsInstance<ProviderOutcome.NetworkError>().firstOrNull()
        return LyricsResult.Failed(LyricsResult.Failed.Kind.Offline, net?.detail)
    }

    private fun found(songId: Long, doc: LyricsDoc, alsoChoose: Boolean): LyricsResult.Found {
        val result = LyricsResult.Found(doc.lines, doc.plain, doc.source, doc.kind)
        writeKinds(songId, doc.source, result)
        if (alsoChoose) writeKinds(songId, LyricsProviders.CHOOSE, result)
        return result
    }

    // ---- Cache (per song and provider choice) ----

    private fun file(id: Long, key: String) = File(dir, "${id}_$key.json")

    /** Priority of a result: word-synced 3, line-synced 2, plain 1, anything else 0. */
    private fun rankOf(r: LyricsResult): Int =
        if (r is LyricsResult.Found) lyricsRank(r.kind ?: kindOf(r.synced, r.plain)) else 0

    /** A cached "found" answer whatever its age (null when there is none). */
    private fun readStale(id: Long, key: String): LyricsResult.Found? {
        val f = file(id, key)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText())
            if (json.optInt("v") != CACHE_VERSION || json.optString("kind") != "found") return null
            val lines = linesFromJson(json.optJSONArray("lines"))
            val plain = json.optString("plain", "").takeIf { it.isNotBlank() }
            LyricsResult.Found(lines, plain, json.optString("source").takeIf { it.isNotBlank() }, kindOf(lines, plain))
        } catch (e: Exception) {
            null
        }
    }

    private fun readCache(id: Long, key: String): LyricsResult? {
        val f = file(id, key)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText())
            if (json.optInt("v") != CACHE_VERSION) return null
            val age = System.currentTimeMillis() - json.optLong("at")
            when (json.optString("kind")) {
                "found" -> {
                    val lines = linesFromJson(json.optJSONArray("lines"))
                    val plain = json.optString("plain", "").takeIf { it.isNotBlank() }
                    val kind = kindOf(lines, plain)
                    // The best kind is kept long; line-synced and plain are looked at again sooner, in case a
                    // better (word-synced) version has appeared since.
                    val ttl = when (kind) {
                        LyricsKind.WordSynced -> FOUND_TTL_MS
                        LyricsKind.LineSynced -> LINE_TTL_MS
                        else -> PLAIN_TTL_MS
                    }
                    if (age < ttl) {
                        LyricsResult.Found(lines, plain, json.optString("source").takeIf { it.isNotBlank() }, kind)
                    } else {
                        null
                    }
                }
                "instrumental" -> LyricsResult.Instrumental
                "none" -> if (age < NOT_FOUND_TTL_MS && json.optInt("fb") == FALLBACK_GEN) LyricsResult.NotFound else null
                else -> null
            }
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    /** Only real answers are written; anything else is never cached. */
    private fun writeKinds(id: Long, key: String, result: LyricsResult) {
        val json = JSONObject().put("v", CACHE_VERSION).put("at", System.currentTimeMillis())
        when (result) {
            is LyricsResult.Found -> {
                json.put("kind", "found")
                json.put("source", result.source.orEmpty())
                json.put("lines", linesToJson(result.synced))
                json.put("plain", result.plain ?: "")
            }
            LyricsResult.Instrumental -> json.put("kind", "instrumental")
            LyricsResult.NotFound -> json.put("kind", "none").put("fb", FALLBACK_GEN) // older "none" entries came from a single query
            else -> return
        }
        runCatching { file(id, key).writeText(json.toString()) }
    }

    private fun linesToJson(lines: List<LyricLine>): JSONArray = JSONArray().apply {
        lines.forEach { line ->
            val words = JSONArray()
            line.words.forEach { w ->
                words.put(JSONArray().put(w.startMs).put(w.endMs).put(w.text).put(w.spaceAfter))
            }
            put(JSONArray().put(line.timeMs).put(line.endMs).put(line.text).put(words))
        }
    }

    private fun linesFromJson(arr: JSONArray?): List<LyricLine> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONArray(i) ?: return@mapNotNull null
            val wordsJson = a.optJSONArray(3)
            val words = if (wordsJson == null) emptyList() else (0 until wordsJson.length()).mapNotNull { j ->
                val w = wordsJson.optJSONArray(j) ?: return@mapNotNull null
                LyricWord(w.optLong(0), w.optLong(1), w.optString(2), w.optBoolean(3, true))
            }
            LyricLine(a.optLong(0), a.optString(2), words, a.optLong(1, -1L))
        }
    }

    private companion object {
        const val TAG = "DopavinLyrics"
        const val CACHE_VERSION = 3
        /** Generation of the "no lyrics" answer: 1 = the smart fallback searched; older negatives are ignored. */
        const val FALLBACK_GEN = 2 // 2: the query list no longer loses the cleaned title; older "not found" answers are searched again
        const val NOT_FOUND_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val FOUND_TTL_MS = 90L * 24 * 60 * 60 * 1000
        const val LINE_TTL_MS = 14L * 24 * 60 * 60 * 1000
        const val PLAIN_TTL_MS = 3L * 24 * 60 * 60 * 1000
    }
}

/** Per-song lyrics sync offset (ms), kept in its own small preference file. Positive = earlier. */
class LyricsOffsetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE)

    fun get(songId: Long): Long = prefs.getLong(songId.toString(), 0L)

    fun set(songId: Long, offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        prefs.edit().apply { if (clamped == 0L) remove(songId.toString()) else putLong(songId.toString(), clamped) }.apply()
    }

    companion object {
        const val MAX_OFFSET_MS = 10_000L
        const val STEP_MS = 100L
    }
}
