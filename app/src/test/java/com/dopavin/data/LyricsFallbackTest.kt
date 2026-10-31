package com.dopavin.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** FbFake provider: answers from a function of the query and records every call. */
private class FbFake(
    override val id: String,
    private val answer: (LyricsQuery) -> ProviderOutcome,
) : LyricsProvider {
    override val capabilities = setOf(LyricsKind.LineSynced)
    val calls = ArrayList<LyricsQuery>()
    override suspend fun fetch(query: LyricsQuery): ProviderOutcome {
        synchronized(calls) { calls += query }
        return answer(query)
    }
}

private fun doc(source: String, conf: Float = 0.9f, kind: LyricsKind = LyricsKind.LineSynced) = LyricsDoc(
    source,
    if (kind == LyricsKind.Plain) emptyList() else listOf(LyricLine(1000, "la la")),
    if (kind == LyricsKind.Plain) "la la" else null,
    conf,
)

private fun q(title: String, artist: String = "A", strict: Boolean = false) =
    LyricsQuery(title, artist, "", 200, null, strict = strict)

class LyricsFallbackTest {
    private val plan = listOf(q("Exact Title (Official Video)"), q("Exact Title"), q("Exact Title", "", strict = true))

    @Test fun exactQueryWinsAtOnce() = runBlocking {
        val p = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.Success(doc(LyricsProviders.LRCLIB)) }
        val r = LyricsFallback().run(plan, listOf(p))
        assertTrue(r is RaceResult.Won)
        assertEquals(1, p.calls.size)
    }

    @Test fun cleanedTitleWinsAfterExactFails() = runBlocking {
        val p = FbFake(LyricsProviders.LRCLIB) { query ->
            if (query.title == "Exact Title") ProviderOutcome.Success(doc(LyricsProviders.LRCLIB)) else ProviderOutcome.NotFound
        }
        val r = LyricsFallback().run(plan, listOf(p))
        assertTrue(r is RaceResult.Won)
        assertEquals(listOf("Exact Title (Official Video)", "Exact Title"), p.calls.map { it.title })
    }

    @Test fun artistLessQueryFindsItWhenTitleIsClose() = runBlocking {
        val p = FbFake(LyricsProviders.LRCLIB) { query ->
            if (query.artist.isBlank()) ProviderOutcome.Success(doc(LyricsProviders.LRCLIB, 0.85f)) else ProviderOutcome.NotFound
        }
        assertTrue(LyricsFallback().run(plan, listOf(p)) is RaceResult.Won)
    }

    @Test fun weakArtistLessHitIsRejected() = runBlocking {
        val p = FbFake(LyricsProviders.LRCLIB) { query ->
            if (query.strict) ProviderOutcome.Success(doc(LyricsProviders.LRCLIB, 0.6f)) else ProviderOutcome.NotFound
        }
        val r = LyricsFallback().run(plan, listOf(p))
        assertTrue(r is RaceResult.NoLyrics)
    }

    @Test fun artistNeedingProvidersSkipArtistLessQueries() = runBlocking {
        val u = FbFake(LyricsProviders.UNISON) { ProviderOutcome.NotFound }
        val l = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.NotFound }
        LyricsFallback().run(plan, listOf(u, l))
        assertTrue(u.calls.all { it.artist.isNotBlank() })
        assertEquals(3, l.calls.size)
    }

    @Test fun multipleProvidersAnotherOneAnswersLater() = runBlocking {
        val a = FbFake(LyricsProviders.BETTER) { ProviderOutcome.NotFound }
        val b = FbFake(LyricsProviders.LRCLIB) { query ->
            if (query.title == "Exact Title" && query.artist == "A") ProviderOutcome.Success(doc(LyricsProviders.LRCLIB)) else ProviderOutcome.NotFound
        }
        val r = LyricsFallback().run(plan, listOf(a, b)) as RaceResult.Won
        assertEquals(LyricsProviders.LRCLIB, r.doc.source)
    }

    @Test fun networkErrorStopsFurtherVariants() = runBlocking {
        val p = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.NetworkError("UnknownHostException") }
        val r = LyricsFallback().run(plan, listOf(p))
        assertTrue(r is RaceResult.Failed)
        assertEquals(1, p.calls.size)
    }

    @Test fun rateLimitedProviderIsLeftAloneButOthersContinue() = runBlocking {
        val busy = FbFake(LyricsProviders.BETTER) { ProviderOutcome.RateLimited(429) }
        val ok = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.NotFound }
        val r = LyricsFallback().run(plan, listOf(busy, ok))
        assertEquals(1, busy.calls.size)
        assertEquals(3, ok.calls.size)
        assertTrue(r is RaceResult.Failed) // something failed: not a clean "no lyrics", so it is not cached
    }

    @Test fun allProvidersEmptyGivesNoLyrics() = runBlocking {
        val a = FbFake(LyricsProviders.BETTER) { ProviderOutcome.NotFound }
        val b = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.InvalidResponse("wrong song") }
        assertTrue(LyricsFallback().run(plan, listOf(a, b)) is RaceResult.NoLyrics)
    }

    @Test fun missesAreNotAskedAgainUntilRetry() = runBlocking {
        val p = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.NotFound }
        val fb = LyricsFallback()
        fb.run(plan, listOf(p))
        val first = p.calls.size
        fb.run(plan, listOf(p))
        assertEquals(first, p.calls.size)
        fb.run(plan, listOf(p), ignoreMisses = true)
        assertEquals(first * 2, p.calls.size)
    }

    @Test fun missExpires() = runBlocking {
        var t = 0L
        val p = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.NotFound }
        val fb = LyricsFallback(misses = MissCache(ttlMs = 1000), now = { t })
        fb.run(plan, listOf(p))
        val first = p.calls.size
        t = 5_000
        fb.run(plan, listOf(p))
        assertEquals(first * 2, p.calls.size)
    }

    @Test fun syncedBeatsPlainInsideAStep() = runBlocking {
        val plain = FbFake(LyricsProviders.LRCLIB) { ProviderOutcome.Success(doc(LyricsProviders.LRCLIB, 0.9f, LyricsKind.Plain)) }
        val synced = FbFake(LyricsProviders.BETTER) { ProviderOutcome.Success(doc(LyricsProviders.BETTER)) }
        val r = LyricsFallback().run(plan, listOf(plain, synced)) as RaceResult.Won
        assertEquals(LyricsProviders.BETTER, r.doc.source)
    }

    @Test fun singleFlightRunsOncePerKeyAndSurvivesACancelledWaiter() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sf = SingleFlight<String, Int>(scope)
        val runs = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val work: suspend () -> Int = { runs.incrementAndGet(); gate.await(); 7 }
        val first = async { sf.run("song", work) }
        delay(50)
        val second = async { sf.run("song", work) }
        delay(50)
        first.cancel()
        gate.complete(Unit)
        assertEquals(7, second.await())
        assertEquals(1, runs.get())
        assertEquals(8, sf.run("song") { 8 }) // finished entries are not reused
        assertEquals(listOf(1, 1), listOf(sf.run("a") { 1 }, sf.run("b") { 1 }))
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
    }

    @Test fun differentKeysRunSeparately() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sf = SingleFlight<String, Int>(scope)
        val runs = AtomicInteger()
        val all = listOf("a", "b", "c").map { k -> async { sf.run(k) { runs.incrementAndGet(); delay(30); 1 } } }.awaitAll()
        assertEquals(3, runs.get())
        assertFalse(all.isEmpty())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
    }
}
