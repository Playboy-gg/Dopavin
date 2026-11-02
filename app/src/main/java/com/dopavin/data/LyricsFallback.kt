package com.dopavin.data
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
class LyricsFallback(
    private val misses: MissCache = MissCache(),
    private val config: RaceConfig = RaceConfig(),
    private val totalBudgetMs: Long = 25_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        plan: List<LyricsQuery>,
        providers: List<LyricsProvider>,
        ignoreMisses: Boolean = false,
    ): RaceResult {
        if (plan.isEmpty() || providers.isEmpty()) return RaceResult.NoLyrics(emptyMap())
        val eligible = providers.toMutableList()
        val outcomes = LinkedHashMap<String, ProviderOutcome>()
        val started = now()
        var failed = false
        for (query in plan) {
            if (eligible.isEmpty()) break
            if (now() - started > totalBudgetMs) return RaceResult.TimedOut(outcomes)
            val step = eligible.filter { p ->
                (query.artist.isNotBlank() || !LyricsProviders.needsArtist(p.id)) &&
                    (ignoreMisses || !misses.isMiss(p.id, query, now()))
            }
            if (step.isEmpty()) continue
            val gated = if (query.strict) step.map { StrictGate(it) } else step
            val result = raceLyrics(gated, query, config)
            for ((id, outcome) in result.outcomes) {
                val old = outcomes[id]
                if (old == null || isMissLike(old) || !isMissLike(outcome)) outcomes[id] = outcome
                when (outcome) {
                    ProviderOutcome.NotFound -> misses.remember(id, query, now())
                    is ProviderOutcome.NetworkError, is ProviderOutcome.RateLimited -> {
                        failed = true
                        eligible.removeAll { it.id == id }
                    }
                    else -> {}
                }
            }
            when (result) {
                is RaceResult.Won -> return RaceResult.Won(result.doc, outcomes)
                is RaceResult.Instrumental -> return RaceResult.Instrumental(outcomes)
                is RaceResult.TimedOut -> return RaceResult.TimedOut(outcomes)
                else -> {}
            }
        }
        return if (failed) RaceResult.Failed(outcomes) else RaceResult.NoLyrics(outcomes)
    }
    private fun isMissLike(o: ProviderOutcome) = o is ProviderOutcome.NotFound || o is ProviderOutcome.InvalidResponse
    companion object {
        const val STRICT_MIN_CONFIDENCE = 0.75f
    }
}
internal class StrictGate(private val inner: LyricsProvider) : LyricsProvider {
    override val id get() = inner.id
    override val capabilities get() = inner.capabilities
    override suspend fun fetch(query: LyricsQuery): ProviderOutcome {
        val o = inner.fetch(query)
        return if (o is ProviderOutcome.Success && o.doc.confidence < LyricsFallback.STRICT_MIN_CONFIDENCE) {
            ProviderOutcome.NotFound
        } else {
            o
        }
    }
}
class MissCache(val ttlMs: Long = 6L * 60 * 60 * 1000, private val max: Int = 400) {
    private val map = LinkedHashMap<String, Long>()
    private fun key(provider: String, q: LyricsQuery) =
        provider + "|" + q.title.trim().lowercase() + "|" + q.artist.trim().lowercase() + "|" + q.album.trim().lowercase()
    @Synchronized
    fun isMiss(provider: String, q: LyricsQuery, nowMs: Long): Boolean {
        val k = key(provider, q)
        val at = map[k] ?: return false
        if (nowMs - at > ttlMs) { map.remove(k); return false }
        return true
    }
    @Synchronized
    fun remember(provider: String, q: LyricsQuery, nowMs: Long) {
        val k = key(provider, q)
        map.remove(k)
        map[k] = nowMs
        while (map.size > max) map.remove(map.keys.first())
    }
}
class SingleFlight<K : Any, V>(private val scope: CoroutineScope) {
    private val running = ConcurrentHashMap<K, Deferred<V>>()
    suspend fun run(key: K, block: suspend () -> V): V {
        val mine = CompletableDeferred<V>()
        val existing = running.putIfAbsent(key, mine)
        if (existing != null) return existing.await()
        scope.async {
            try {
                mine.complete(block())
            } catch (e: CancellationException) {
                mine.cancel(e)
                throw e
            } catch (e: Throwable) {
                mine.completeExceptionally(e)
            } finally {
                running.remove(key, mine)
            }
        }
        return mine.await()
    }
}
