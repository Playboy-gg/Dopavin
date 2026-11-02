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
    data class Failed(val kind: Kind, val detail: String?) : LyricsResult {
        enum class Kind { ServerBusy, Offline, Timeout, Other }
    }
}
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flights = SingleFlight<String, LyricsResult>(scope)
    suspend fun load(
        song: Song,
        allowNetwork: Boolean,
        selection: String = LyricsProviders.CHOOSE,
        refresh: Boolean = false,
    ): LyricsResult = withContext(Dispatchers.IO) {
        val choice = LyricsProviders.normalise(selection)
        File(dir, "${song.id}.json").delete()
        if (!refresh) readCache(song.id, choice)?.let { return@withContext it }
        if (!allowNetwork) return@withContext LyricsResult.Disabled
        val plan = planOf(song)
        if (plan.isEmpty()) return@withContext LyricsResult.NotFound
        flights.run("${song.id}|$choice") {
            val started = System.currentTimeMillis()
            val result = search(song.id, plan, choice, refresh)
            result
        }
    }
    private fun planOf(song: Song): List<LyricsQuery> = LyricsQueryPlan.build(
        LyricsQueryPlan.Meta(
            title = song.title.takeUnless { it == unknownTitle }.orEmpty(),
            artist = song.artist.takeUnless { it == unknownArtist }.orEmpty(),
            album = song.album.takeUnless { it == unknownAlbum }.orEmpty(),
            durationSec = (song.durationMs / 1000L).toInt(),
            videoId = song.videoId,
        ),
    )
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
    private fun file(id: Long, key: String) = File(dir, "${id}_$key.json")
    private fun readCache(id: Long, key: String): LyricsResult? {
        val f = file(id, key)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText())
            if (json.optInt("v") != CACHE_VERSION) return null
            val age = System.currentTimeMillis() - json.optLong("at")
            when (json.optString("kind")) {
                "found" -> if (age < FOUND_TTL_MS) {
                    val lines = linesFromJson(json.optJSONArray("lines"))
                    val plain = json.optString("plain", "").takeIf { it.isNotBlank() }
                    LyricsResult.Found(lines, plain, json.optString("source").takeIf { it.isNotBlank() }, kindOf(lines, plain))
                } else {
                    null
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
            LyricsResult.NotFound -> json.put("kind", "none").put("fb", FALLBACK_GEN)
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
        const val FALLBACK_GEN = 1
        const val NOT_FOUND_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val FOUND_TTL_MS = 90L * 24 * 60 * 60 * 1000
    }
}
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
