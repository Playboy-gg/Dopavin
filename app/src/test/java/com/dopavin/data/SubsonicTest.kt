package com.dopavin.data

import org.junit.Assert.*
import org.junit.Test

class SubsonicTest {
    private val server = SubsonicServer("music.example.com:4533/", "harish", "sesame")

    @Test fun md5MatchesSpecExample() {
        // From the Subsonic API documentation: password "sesame", salt "c19b2d" -> 26719a1196d2a940705a59634eb18eab
        assertEquals("26719a1196d2a940705a59634eb18eab", Subsonic.md5("sesamec19b2d"))
    }
    @Test fun urlHasTokenNotPassword() {
        val u = Subsonic.url(server, "search3", listOf("query" to "a b&c"), salt = "c19b2d")
        assertTrue(u.startsWith("https://music.example.com:4533/rest/search3.view?u=harish&t=26719a1196d2a940705a59634eb18eab&s=c19b2d"))
        assertTrue(u.contains("&v=1.16.1&c=Dopavin&f=json"))
        assertTrue(u.endsWith("&query=a+b%26c"))
        assertFalse(u.contains("sesame"))
    }
    @Test fun baseUrlKeepsSchemeAndDropsSlash() {
        assertEquals("http://nas.local:4040", Subsonic.baseUrl("http://nas.local:4040//"))
        assertEquals("https://x.org", Subsonic.baseUrl(" x.org/ "))
        assertEquals("", Subsonic.baseUrl("  "))
    }
    @Test fun serverSongIds() {
        assertTrue(Subsonic.isServerSong("sub:123"))
        assertFalse(Subsonic.isServerSong("dQw4w9WgXcQ"))
        assertFalse(Subsonic.isServerSong(null))
    }
    @Test fun parsesSearchReply() {
        val json = """{"subsonic-response":{"status":"ok","version":"1.16.1","searchResult3":{"song":[
            {"id":"al-1","title":"Song A","artist":"Art","album":"Alb","duration":215,"coverArt":"al-9"},
            {"id":42,"title":"Song B","duration":100},
            {"title":"no id"},
            {"id":"x","title":""}]}}}"""
        val t = Subsonic.parseTracks(json)
        assertEquals(2, t.size)
        assertEquals(SubsonicTrack("al-1", "Song A", "Art", "Alb", 215, "al-9"), t[0])
        assertEquals("42", t[1].id)
        assertNull(t[1].coverArt)
    }
    @Test fun parsesStatus() {
        assertEquals(Subsonic.Reply.Ok, Subsonic.parseStatus("""{"subsonic-response":{"status":"ok"}}"""))
        val f = Subsonic.parseStatus("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""")
        assertEquals(Subsonic.Reply.Failed("Wrong username or password"), f)
        assertTrue(Subsonic.parseStatus("junk") is Subsonic.Reply.Failed)
    }
    @Test fun emptyOrBrokenGivesNoTracks() {
        assertTrue(Subsonic.parseTracks("{}").isEmpty())
        assertTrue(Subsonic.parseTracks("""{"subsonic-response":{"status":"ok","searchResult3":{}}}""").isEmpty())
    }
}
