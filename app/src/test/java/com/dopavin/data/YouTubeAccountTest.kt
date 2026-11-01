package com.dopavin.data

import org.junit.Assert.*
import org.junit.Test

class YouTubeAccountTest {
    @Test fun sha1KnownValue() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", YouTubeAccount.sha1Hex("abc"))
    }
    @Test fun authHeaderShape() {
        val h = YouTubeAccount.authHeader("SID", 1700000000L)
        assertEquals("SAPISIDHASH 1700000000_" + YouTubeAccount.sha1Hex("1700000000 SID https://music.youtube.com"), h)
    }
    @Test fun cookieLookup() {
        val c = "VISITOR=1; SAPISID=abc/123; __Secure-3PAPISID=zzz; X=y"
        assertEquals("abc/123", YouTubeAccount.sapisid(c))
        assertEquals("zzz", YouTubeAccount.sapisid("A=b; __Secure-3PAPISID=zzz"))
        assertTrue(YouTubeAccount.isSignedIn(c))
        assertFalse(YouTubeAccount.isSignedIn("VISITOR=1; SAPISID="))
        assertFalse(YouTubeAccount.isSignedIn(""))
    }
    @Test fun durations() {
        assertEquals(225, YouTubeAccount.parseDuration("3:45"))
        assertEquals(3723, YouTubeAccount.parseDuration("1:02:03"))
        assertEquals(0, YouTubeAccount.parseDuration("live"))
        assertEquals(0, YouTubeAccount.parseDuration(null))
    }

    private val sample = """{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
      {"musicShelfRenderer":{"contents":[
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"AAAAAAAAAAA"},
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://t/small"},{"url":"https://t/big"}]}}},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song One"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist One"},{"text":" • "},{"text":"Album"}]}}}],
          "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:45"}]}}}]}},
        {"musicResponsiveListItemRenderer":{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Unavailable"}]}}}]}},
        {"musicResponsiveListItemRenderer":{
          "overlay":{"x":{"playNavigationEndpoint":{"watchEndpoint":{"videoId":"BBBBBBBBBBB"}}}},
          "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song Two"}]}}}]}}
      ],"continuations":[{"nextContinuationData":{"continuation":"TOKEN1"}}]}}]}}}}]}}}"""

    @Test fun parsesSongRows() {
        val t = YouTubeAccount.parseTracks(sample)
        assertEquals(2, t.size)
        assertEquals(YtTrack("AAAAAAAAAAA", "Song One", "Artist One", 225, "https://t/big"), t[0])
        assertEquals("BBBBBBBBBBB", t[1].videoId)
        assertEquals("", t[1].artist)
        assertEquals("TOKEN1", YouTubeAccount.continuation(sample))
    }
    @Test fun newStyleContinuation() {
        val j = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"T2"}}}}]}}]}"""
        assertEquals("T2", YouTubeAccount.continuation(j))
        assertNull(YouTubeAccount.continuation("{}"))
    }
    @Test fun parsesPlaylists() {
        val j = """{"a":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Gym"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL123"}}}},
          {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Liked"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLLM"}}}},
          {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Artist"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"UCabc"}}}}]}"""
        assertEquals(listOf(YtPlaylist("VLPL123", "Gym")), YouTubeAccount.parsePlaylists(j))
    }
    @Test fun brokenInputIsEmpty() {
        assertTrue(YouTubeAccount.parseTracks("not json").isEmpty())
        assertTrue(YouTubeAccount.parsePlaylists("").isEmpty())
    }
}
