package com.dopavin.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Smart lyrics fallback: runs the queries of a [LyricsQueryPlan] one after another until one wins.
 * Pure Kotlin on top of [raceLyrics]; providers are injected, so tests use fakes (no network).
 *
 * - Each step races only the providers that can still help: a provider that failed (network error,
 *   rate limit) is dropped for the rest of the song, so an outage or a limit is never hammered with
 *   variants; a provider that needs an artist skips artist-less queries.
 * - A provider that answered "no lyrics" for a query is remembered for [MissCache.ttlMs] and not asked
 *   the same thing again (retry with `ignoreMisses` skips that memory).
 * - Artist-less queries ([LyricsQuery.strict]) only accept a close title match ([STRICT_MIN_CONFIDENCE]).
 * - The whole search has a time budget; when it runs out the answer is TimedOut (not cached).
 */
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
                // Keep the most informative outcome per provider: a failure beats a plain miss.
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
        /** An artist-less hit needs exact title (0.5) and a known duration that fits (0.1-0.2) plus the neutral artist 0.15. */
        const val STRICT_MIN_CONFIDENCE = 0.75f
    }
}

/** Turns a weak answer of an artist-less query into "not found", so a similar title never wins by luck. */
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

/** Remembers, for a limited time, which provider had nothing for which query. In memory only. */
class MissCache(val ttlMs: Long = 6L * 60 * 60 * 1000, private val max: Int = 400) {
    private val map = LinkedHashMap<String, Long>()

    /** What was really asked: the strings as sent (a provider may treat "Song (Live)" and "Song" differently). */
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

/**
 * One running job per key: a second caller for the same key waits for the first one instead of
 * starting its own request. The work runs in [scope], so a caller that goes away (song changed)
 * does not cancel it for the others, and the answer still lands in the cache.
 */
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
