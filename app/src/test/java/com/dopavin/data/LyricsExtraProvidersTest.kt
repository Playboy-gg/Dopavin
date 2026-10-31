package com.dopavin.data

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Http(private val handler: (String) -> HttpReply) : LyricsHttp {
    val urls = ArrayList<String>()
    override suspend fun get(url: String, timeoutMs: Int): HttpReply {
        urls += url
        return handler(url)
    }
}

private val QQ = LyricsQuery("Blinding Lights", "The Weeknd", "After Hours", 200, "abc")
private const val TTML = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1.0" end="3.0">Hello world</p></div></body></tt>"""
private const val LRC = "[00:01.00]Hello\n[00:03.50]World"

class LyricsExtraProvidersTest {

    @Test
    fun `portato uses qq path without video id and accepts raw ttml`() = runBlocking {
        val http = Http { HttpReply(200, TTML) }
        val p = BetterLyricsProvider(http, baseUrl = "https://x", path = "/qq/getLyrics", id = LyricsProviders.PORTATO, sendVideoId = false)
        val o = p.fetch(QQ) as ProviderOutcome.Success
        assertEquals("portato", o.doc.source)
        assertTrue(http.urls[0].startsWith("https://x/qq/getLyrics?s="))
        assertTrue("&v=" !in http.urls[0])
    }

    @Test
    fun `lyricsplus falls over to next mirror and reads ttml`() = runBlocking {
        val http = Http { if (it.startsWith("https://a/")) HttpReply(500, "") else HttpReply(200, "{\"ttml\":\"" + TTML.replace("\"", "\\\"") + "\"}") }
        val o = LyricsPlusProvider(http, listOf("https://a/", "https://b/")).fetch(QQ) as ProviderOutcome.Success
        assertEquals("lyricsplus", o.doc.source)
        assertTrue(http.urls[0].startsWith("https://a/v1/ttml/get?title="))
        assertTrue(http.urls[1].startsWith("https://b/v1/ttml/get"))
    }

    @Test
    fun `lyricsplus v2 json gives word timing`() {
        val body = """{"type":"Word","lyrics":[{"time":1000,"duration":2000,"text":"Hi you","syllabus":[{"time":1000,"duration":500,"text":"Hi "},{"time":1500,"duration":500,"text":"you"}]}]}"""
        val o = LyricsPlusProvider(Http { HttpReply(200, "") }).fromJson(QQ, body) as ProviderOutcome.Success
        assertEquals(LyricsKind.WordSynced, o.doc.kind)
        assertEquals(2, o.doc.lines[0].words.size)
        assertEquals("Hi you", o.doc.lines[0].text)
    }

    @Test
    fun `lyricsplus v2 line-only and empty`() {
        val p = LyricsPlusProvider(Http { HttpReply(200, "") })
        val o = p.fromJson(QQ, """{"type":"Line","lyrics":[{"time":500,"duration":900,"text":"A"}]}""") as ProviderOutcome.Success
        assertEquals(LyricsKind.LineSynced, o.doc.kind)
        assertEquals(ProviderOutcome.NotFound, p.fromJson(QQ, """{"lyrics":[]}"""))
    }

    @Test
    fun `lyricsplus reports network error only when every mirror failed`() = runBlocking {
        val p = LyricsPlusProvider(Http { throw IOException("x") }, listOf("https://a/"))
        assertTrue(p.fetch(QQ) is ProviderOutcome.NetworkError)
        val miss = LyricsPlusProvider(Http { HttpReply(404, "") }, listOf("https://a/"))
        assertEquals(ProviderOutcome.NotFound, miss.fetch(QQ))
        assertEquals(ProviderOutcome.NotFound, LyricsPlusProvider(Http { HttpReply(200, "") }).fetch(QQ.copy(artist = "")))
    }

    @Test
    fun `unison parses lrc ttml and plain by content`() {
        fun body(lyrics: String) = """{"success":true,"data":{"song":"Blinding Lights","artist":"The Weeknd","lyrics":${quote(lyrics)},"format":"x","syncType":"y"}}"""
        val p = UnisonProvider(Http { HttpReply(200, "") })
        assertEquals(LyricsKind.LineSynced, (p.parse(QQ, body(LRC)) as ProviderOutcome.Success).doc.kind)
        assertEquals(LyricsKind.LineSynced, (p.parse(QQ, body(TTML)) as ProviderOutcome.Success).doc.kind)
        assertEquals(LyricsKind.Plain, (p.parse(QQ, body("just words\nmore")) as ProviderOutcome.Success).doc.kind)
    }

    @Test
    fun `unison rejects other song and failure flags`() = runBlocking {
        val p = UnisonProvider(Http { HttpReply(200, "") })
        val other = """{"success":true,"data":{"song":"Another Tune","artist":"Nobody","lyrics":"x y"}}"""
        assertTrue(p.parse(QQ, other) is ProviderOutcome.InvalidResponse)
        assertEquals(ProviderOutcome.NotFound, p.parse(QQ, """{"success":false}"""))
        assertEquals(ProviderOutcome.NotFound, UnisonProvider(Http { HttpReply(404, "") }).fetch(QQ))
        assertTrue(UnisonProvider(Http { HttpReply(429, "") }).fetch(QQ) is ProviderOutcome.RateLimited)
    }

    @Test
    fun `kugou walks search hash download and decodes base64 lrc`() = runBlocking {
        val enc = Base64.getEncoder().encodeToString(("[00:00.50]作词 : Someone\n$LRC").toByteArray())
        val http = Http { url ->
            when {
                "mobileservice" in url -> HttpReply(200, """{"data":{"info":[{"duration":400,"hash":"far"},{"duration":201,"hash":"H1"}]}}""")
                "lyrics.kugou.com/search" in url && "hash=H1" in url -> HttpReply(200, """{"candidates":[{"id":7,"accesskey":"K"}]}""")
                "lyrics.kugou.com/download" in url && "id=7" in url && "accesskey=K" in url -> HttpReply(200, """{"content":"$enc"}""")
                else -> HttpReply(404, "")
            }
        }
        val o = KuGouProvider(http).fetch(QQ) as ProviderOutcome.Success
        assertEquals("kugou", o.doc.source)
        assertEquals(listOf("Hello", "World"), o.doc.lines.map { it.text })
        assertTrue(http.urls.none { "hash=far" in it })
    }

    @Test
    fun `kugou not found and busy`() = runBlocking {
        val none = Http { if ("mobileservice" in it) HttpReply(200, """{"data":{"info":[]}}""") else HttpReply(200, """{"candidates":[]}""") }
        assertEquals(ProviderOutcome.NotFound, KuGouProvider(none).fetch(QQ))
        assertTrue(KuGouProvider(Http { HttpReply(429, "") }).fetch(QQ) is ProviderOutcome.RateLimited)
        assertTrue(KuGouProvider(Http { throw IOException() }).fetch(QQ) is ProviderOutcome.NetworkError)
    }

    @Test
    fun `provider registry lists every provider with capabilities`() {
        for (id in LyricsProviders.all) assertTrue(id, LyricsProviders.capabilities(id).isNotEmpty())
        assertEquals("choose", LyricsProviders.normalise("bogus"))
        assertEquals("kugou", LyricsProviders.normalise("kugou"))
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
