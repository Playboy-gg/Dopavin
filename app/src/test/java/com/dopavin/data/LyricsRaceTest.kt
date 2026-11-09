package com.dopavin.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private val Q = LyricsQuery("Song", "Artist", "", 200, null)

private fun doc(source: String, timed: Boolean) = LyricsDoc(
    source,
    if (timed) listOf(LyricLine(1000, "line")) else emptyList(),
    if (timed) null else "plain text",
    0.9f,
)

private class Fake(
    override val id: String,
    private val delayMs: Long,
    private val outcome: () -> ProviderOutcome,
) : LyricsProvider {
    override val capabilities = emptySet<LyricsKind>()
    val started = AtomicInteger()
    val cancelled = AtomicInteger()
    override suspend fun fetch(query: LyricsQuery): ProviderOutcome {
        started.incrementAndGet()
        try {
            delay(delayMs)
        } catch (e: CancellationException) {
            cancelled.incrementAndGet()
            throw e
        }
        return outcome()
    }
}

private fun wordDoc(source: String) = LyricsDoc(
    source,
    listOf(LyricLine(1000, "w", listOf(LyricWord(1000, 1500, "w")))),
    null,
    0.9f,
)

/** A provider that says which kinds it can deliver, like the real ones. */
private class CapRaceFake(
    override val id: String,
    override val capabilities: Set<LyricsKind>,
    private val delayMs: Long,
    private val outcome: () -> ProviderOutcome,
) : LyricsProvider {
    override suspend fun fetch(query: LyricsQuery): ProviderOutcome {
        delay(delayMs)
        return outcome()
    }
}

class LyricsRaceTest {

    private val cfg = RaceConfig(timeoutMs = 2_000, upgradeGraceMs = 300)

    @Test
    fun `first valid result wins and slower request is cancelled`() = runBlocking {
        val fast = Fake("fast", 20) { ProviderOutcome.Success(doc("fast", true)) }
        val slow = Fake("slow", 1_500) { ProviderOutcome.Success(doc("slow", true)) }
        val r = raceLyrics(listOf(slow, fast), Q, cfg)
        assertTrue(r is RaceResult.Won)
        assertEquals("fast", (r as RaceResult.Won).doc.source)
        delay(100)
        assertEquals(1, slow.cancelled.get())
    }

    @Test
    fun `providers run in parallel not one after another`() = runBlocking {
        val a = Fake("a", 300) { ProviderOutcome.NotFound }
        val b = Fake("b", 300) { ProviderOutcome.NotFound }
        val t0 = System.nanoTime()
        raceLyrics(listOf(a, b), Q, cfg)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $ms ms", ms < 550)
        assertEquals(1, a.started.get() + b.started.get() - 1)
    }

    @Test
    fun `fast invalid answer does not beat a slower valid one`() = runBlocking {
        val bad = Fake("bad", 10) { ProviderOutcome.InvalidResponse("wrong song") }
        val empty = Fake("empty", 20) { ProviderOutcome.NotFound }
        val good = Fake("good", 200) { ProviderOutcome.Success(doc("good", true)) }
        val r = raceLyrics(listOf(bad, empty, good), Q, cfg)
        assertEquals("good", (r as RaceResult.Won).doc.source)
    }

    @Test
    fun `a success with no lines or text is not a winner`() = runBlocking {
        val hollow = Fake("hollow", 10) { ProviderOutcome.Success(LyricsDoc("hollow", emptyList(), null, 1f)) }
        val good = Fake("good", 100) { ProviderOutcome.Success(doc("good", true)) }
        assertEquals("good", (raceLyrics(listOf(hollow, good), Q, cfg) as RaceResult.Won).doc.source)
    }

    @Test
    fun `provider failure or crash does not fail the others`() = runBlocking {
        val boom = Fake("boom", 10) { throw IllegalStateException("x") }
        val net = Fake("net", 10) { ProviderOutcome.NetworkError("io") }
        val good = Fake("good", 120) { ProviderOutcome.Success(doc("good", true)) }
        assertTrue(raceLyrics(listOf(boom, net, good), Q, cfg) is RaceResult.Won)
    }

    @Test
    fun `plain winner is upgraded by a synced result inside the grace period`() = runBlocking {
        val plain = Fake("plain", 20) { ProviderOutcome.Success(doc("plain", false)) }
        val synced = Fake("synced", 150) { ProviderOutcome.Success(doc("synced", true)) }
        val r = raceLyrics(listOf(plain, synced), Q, cfg) as RaceResult.Won
        assertEquals("synced", r.doc.source)
    }

    @Test
    fun `plain winner stays when synced is too slow`() = runBlocking {
        val plain = Fake("plain", 20) { ProviderOutcome.Success(doc("plain", false)) }
        val synced = Fake("synced", 1_000) { ProviderOutcome.Success(doc("synced", true)) }
        val r = raceLyrics(listOf(plain, synced), Q, cfg) as RaceResult.Won
        assertEquals("plain", r.doc.source)
    }

    @Test
    fun `by default synced beats plain even when it arrives much later`() = runBlocking {
        val plain = Fake("plain", 10) { ProviderOutcome.Success(doc("plain", false)) }
        val synced = Fake("synced", 1_800) { ProviderOutcome.Success(doc("synced", true)) }
        val r = raceLyrics(listOf(plain, synced), Q, RaceConfig(timeoutMs = 4_000)) as RaceResult.Won
        assertEquals("synced", r.doc.source)
    }

    @Test
    fun `plain is the last resort when every other provider has nothing`() = runBlocking {
        val plain = Fake("plain", 10) { ProviderOutcome.Success(doc("plain", false)) }
        val none = Fake("none", 200) { ProviderOutcome.NotFound }
        val bad = Fake("bad", 100) { ProviderOutcome.NetworkError("x") }
        val r = raceLyrics(listOf(plain, none, bad), Q, RaceConfig(timeoutMs = 2_000)) as RaceResult.Won
        assertEquals("plain", r.doc.source)
    }

    @Test
    fun `a slow plain-only race still returns plain at the timeout`() = runBlocking {
        val plain = Fake("plain", 10) { ProviderOutcome.Success(doc("plain", false)) }
        val hang = Fake("hang", 5_000) { ProviderOutcome.NotFound }
        val r = raceLyrics(listOf(plain, hang), Q, RaceConfig(timeoutMs = 400)) as RaceResult.Won
        assertEquals("plain", r.doc.source)
    }

    @Test
    fun `all providers empty gives no lyrics and failures give failed`() = runBlocking {
        val none = raceLyrics(listOf(Fake("a", 5) { ProviderOutcome.NotFound }, Fake("b", 5) { ProviderOutcome.NotFound }), Q, cfg)
        assertTrue(none is RaceResult.NoLyrics)
        val failed = raceLyrics(listOf(Fake("a", 5) { ProviderOutcome.NotFound }, Fake("b", 5) { ProviderOutcome.RateLimited(503) }), Q, cfg)
        assertTrue(failed is RaceResult.Failed)
    }

    @Test
    fun `instrumental is reported when nobody has lyrics`() = runBlocking {
        val r = raceLyrics(listOf(Fake("a", 5) { ProviderOutcome.Instrumental("a") }, Fake("b", 5) { ProviderOutcome.NotFound }), Q, cfg)
        assertTrue(r is RaceResult.Instrumental)
    }

    @Test
    fun `timeout gives a clear timed out result`() = runBlocking {
        val r = raceLyrics(listOf(Fake("slow", 5_000) { ProviderOutcome.NotFound }), Q, RaceConfig(timeoutMs = 150))
        assertTrue(r is RaceResult.TimedOut)
    }

    @Test
    fun `cancelling the caller cancels every request`() = runBlocking {
        val p = Fake("p", 5_000) { ProviderOutcome.NotFound }
        val job = launch(Dispatchers.Default) { raceLyrics(listOf(p), Q, RaceConfig(timeoutMs = 10_000)) }
        delay(100)
        job.cancelAndJoin()
        delay(100)
        assertEquals(1, p.cancelled.get())
    }

    @Test
    fun `stale race result is dropped by the caller when the song changed`() = runBlocking {
        // The ViewModel keeps a request token; this models its rule: only the latest request may publish.
        var latest = 0
        var published: String? = null
        suspend fun request(token: Int, source: String, ms: Long) {
            latest = token
            val r = raceLyrics(listOf(Fake(source, ms) { ProviderOutcome.Success(doc(source, true)) }), Q, cfg)
            if (token == latest && r is RaceResult.Won) published = r.doc.source
        }
        val first = async(Dispatchers.Default) { request(1, "old", 200) }
        delay(30)
        val second = async(Dispatchers.Default) { request(2, "new", 20) }
        first.await(); second.await()
        assertEquals("new", published)
    }

    @Test
    fun `rapid repeated races do not interfere`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                raceLyrics(listOf(Fake("p$i", (i % 5) * 5L) { ProviderOutcome.Success(doc("p$i", true)) }), Q, cfg)
            }
        }.map { it.await() }
        assertTrue(results.all { it is RaceResult.Won })
    }

    @Test
    fun `word synced beats an earlier line synced`() = runBlocking {
        val line = CapRaceFake("line", setOf(LyricsKind.LineSynced), 20) { ProviderOutcome.Success(doc("line", true)) }
        val word = CapRaceFake("word", setOf(LyricsKind.WordSynced, LyricsKind.LineSynced), 200) { ProviderOutcome.Success(wordDoc("word")) }
        val r = raceLyrics(listOf(line, word), Q, RaceConfig(timeoutMs = 2_000, upgradeGraceMs = 300, wordGraceMs = 1_000)) as RaceResult.Won
        assertEquals("word", r.doc.source)
        assertEquals(LyricsKind.WordSynced, r.doc.kind)
    }

    @Test
    fun `line synced stays when the word synced rival is too slow`() = runBlocking {
        val line = CapRaceFake("line", setOf(LyricsKind.LineSynced), 20) { ProviderOutcome.Success(doc("line", true)) }
        val word = CapRaceFake("word", setOf(LyricsKind.WordSynced), 1_500) { ProviderOutcome.Success(wordDoc("word")) }
        val r = raceLyrics(listOf(line, word), Q, RaceConfig(timeoutMs = 3_000, upgradeGraceMs = 300, wordGraceMs = 300)) as RaceResult.Won
        assertEquals("line", r.doc.source)
    }

    @Test
    fun `line synced wins at once when no word capable provider is left`() = runBlocking {
        val line = CapRaceFake("line", setOf(LyricsKind.LineSynced), 20) { ProviderOutcome.Success(doc("line", true)) }
        val plainOnly = CapRaceFake("slow", setOf(LyricsKind.Plain), 1_500) { ProviderOutcome.Success(doc("slow", false)) }
        val t0 = System.nanoTime()
        val r = raceLyrics(listOf(line, plainOnly), Q, RaceConfig(timeoutMs = 3_000, wordGraceMs = 2_000)) as RaceResult.Won
        assertEquals("line", r.doc.source)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 800)
    }

    @Test
    fun `plain then line then word ends on word`() = runBlocking {
        val plain = CapRaceFake("plain", setOf(LyricsKind.Plain), 10) { ProviderOutcome.Success(doc("plain", false)) }
        val line = CapRaceFake("line", setOf(LyricsKind.LineSynced), 80) { ProviderOutcome.Success(doc("line", true)) }
        val word = CapRaceFake("word", setOf(LyricsKind.WordSynced), 160) { ProviderOutcome.Success(wordDoc("word")) }
        val r = raceLyrics(listOf(plain, line, word), Q, RaceConfig(timeoutMs = 2_000, upgradeGraceMs = 1_000, wordGraceMs = 1_000)) as RaceResult.Won
        assertEquals("word", r.doc.source)
    }
}
