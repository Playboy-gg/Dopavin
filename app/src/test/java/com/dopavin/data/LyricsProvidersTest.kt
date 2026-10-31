package com.dopavin.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeHttp(private val handler: (String) -> HttpReply) : LyricsHttp {
    val urls = ArrayList<String>()
    override suspend fun get(url: String, timeoutMs: Int): HttpReply {
        urls += url
        return handler(url)
    }
}

private val Q = LyricsQuery("Blinding Lights", "The Weeknd", "After Hours", 200, "abc")

class LyricsProvidersTest {

    @Test
    fun `mini json reads objects arrays and rejects garbage`() {
        val v = MiniJson.obj(MiniJson.parse("""{"a":[1,2,{"b":"x\nA"}],"c":true,"d":null}"""))!!
        assertEquals(true, v["c"])
        assertNull(v["d"])
        assertEquals("x\nA", MiniJson.str(MiniJson.obj(MiniJson.list(v["a"])!![2])!!["b"]))
        assertNull(MiniJson.parse("{bad"))
        assertNull(MiniJson.parse("""{"a":1} trailing"""))
        assertNull(MiniJson.parse(""))
    }

    @Test
    fun `better lyrics parses ttml with word timing`() = runBlocking {
        val body = """{"ttml":"<tt><body><p begin=\"10.0\" end=\"12.0\"><span begin=\"10.0\" end=\"11.0\">Hi</span> <span begin=\"11.0\" end=\"12.0\">there</span></p></body></tt>"}"""
        val p = BetterLyricsProvider(FakeHttp { HttpReply(200, body) })
        val o = p.fetch(Q) as ProviderOutcome.Success
        assertEquals(LyricsKind.WordSynced, o.doc.kind)
        assertEquals("better", o.doc.source)
    }

    @Test
    fun `better lyrics maps status codes`() = runBlocking {
        fun outcome(code: Int) = runBlocking { BetterLyricsProvider(FakeHttp { HttpReply(code, "") }, retryDelayMs = 1).fetch(Q) }
        assertEquals(ProviderOutcome.NotFound, outcome(401))
        assertEquals(ProviderOutcome.NotFound, outcome(422))
        assertTrue(outcome(429) is ProviderOutcome.RateLimited)
        assertTrue(outcome(500) is ProviderOutcome.NetworkError)
    }

    @Test
    fun `better lyrics rejects garbage and timing that cannot fit the song`() = runBlocking {
        val p = BetterLyricsProvider(FakeHttp { HttpReply(200, "<html>oops") })
        assertTrue(p.fetch(Q) is ProviderOutcome.InvalidResponse)
        val late = """{"ttml":"<tt><body><p begin=\"900.0\" end=\"901.0\">x</p></body></tt>"}"""
        assertTrue(BetterLyricsProvider(FakeHttp { HttpReply(200, late) }).fetch(Q) is ProviderOutcome.InvalidResponse)
    }

    @Test
    fun `network failure becomes a value not a crash`() = runBlocking {
        val p = BetterLyricsProvider(FakeHttp { throw IOException("down") })
        assertTrue(p.fetch(Q) is ProviderOutcome.NetworkError)
    }

    private fun record(track: String, artist: String, dur: Int, synced: String? = "[00:10.00]Hello", plain: String? = "Hello") =
        buildString {
            append("""{"trackName":"$track","artistName":"$artist","duration":$dur""")
            append(if (synced != null) ""","syncedLyrics":"$synced"""" else ""","syncedLyrics":null""")
            append(if (plain != null) ""","plainLyrics":"$plain"""" else ""","plainLyrics":null""")
            append("""}""")
        }

    @Test
    fun `lrclib exact hit gives line synced lyrics`() = runBlocking {
        val p = LrclibProvider(FakeHttp { HttpReply(200, record("Blinding Lights", "The Weeknd", 200)) })
        val o = p.fetch(Q) as ProviderOutcome.Success
        assertEquals(LyricsKind.LineSynced, o.doc.kind)
        assertTrue(o.doc.confidence > 0.8f)
    }

    @Test
    fun `lrclib wrong song is rejected`() = runBlocking {
        val http = FakeHttp { url ->
            if (url.contains("/get?")) HttpReply(200, record("Another Tune", "Someone Else", 200)) else HttpReply(200, "[]")
        }
        assertEquals(ProviderOutcome.NotFound, LrclibProvider(http).fetch(Q))
    }

    @Test
    fun `lrclib search picks the matching synced hit over a plain or off-duration one`() = runBlocking {
        val list = "[" + listOf(
            record("Blinding Lights", "The Weeknd", 260),                       // wrong duration
            record("Blinding Lights", "The Weeknd", 200, synced = null),          // plain only
            record("Blinding Lights", "The Weeknd", 201),                        // synced, right
        ).joinToString(",") + "]"
        val http = FakeHttp { url -> if (url.contains("/get?")) HttpReply(404, "") else HttpReply(200, list) }
        val o = LrclibProvider(http).fetch(Q) as ProviderOutcome.Success
        assertEquals(LyricsKind.LineSynced, o.doc.kind)
    }

    @Test
    fun `lrclib server busy is rate limited and never not found`() = runBlocking {
        val p = LrclibProvider(FakeHttp { HttpReply(503, "") }, retryDelayMs = 1)
        assertTrue(p.fetch(Q) is ProviderOutcome.RateLimited)
    }

    @Test
    fun `lrclib instrumental flag`() = runBlocking {
        val body = """{"trackName":"Blinding Lights","artistName":"The Weeknd","duration":200,"instrumental":true}"""
        assertTrue(LrclibProvider(FakeHttp { HttpReply(200, body) }).fetch(Q) is ProviderOutcome.Instrumental)
    }

    @Test
    fun `lrclib enhanced lrc keeps word timing`() = runBlocking {
        val rec = record("Blinding Lights", "The Weeknd", 200, synced = "[00:10.00]<00:10.00>Hel <00:10.50>lo")
        val o = LrclibProvider(FakeHttp { HttpReply(200, rec) }).fetch(Q) as ProviderOutcome.Success
        assertEquals(LyricsKind.WordSynced, o.doc.kind)
    }

    @Test
    fun `matcher compares title artist and duration`() {
        assertTrue(LyricsMatcher.score(Q, "Blinding Lights (Remastered)", "The Weeknd", 201.0)!! > 0.5f)
        assertNull(LyricsMatcher.score(Q, "Save Your Tears", "The Weeknd", 200.0))
        assertNull(LyricsMatcher.score(Q, "Blinding Lights", "Other Band", 200.0))
        assertNull(LyricsMatcher.score(Q, "Blinding Lights", "The Weeknd", 400.0))
    }

    @Test
    fun `selection setting normalises unknown values to choose`() {
        assertEquals("choose", LyricsProviders.normalise("lrc.red"))
        assertEquals("choose", LyricsProviders.normalise(null))
        assertEquals("lrclib", LyricsProviders.normalise("lrclib"))
    }
}
