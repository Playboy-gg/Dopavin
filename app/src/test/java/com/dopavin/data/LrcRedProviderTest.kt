package com.dopavin.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcRedProviderTest {
    private val q = LyricsQuery("Tum Hi Ho", "Arijit Singh", "", 262, "vid", "Tum Hi Ho")

    @Test fun biniPicksMatchingRecordingWithLrcRedUrl() {
        val body = """{"results":[
          {"track_name":"Tum Hi Ho (Live)","artist_name":"Arijit Singh","duration":262,"isrc":"INA101300001","lyricsUrl":"https://lrc.red/s/INA101300001.ttml"},
          {"track_name":"Tum Hi Ho","artist_name":"Arijit Singh","duration":262,"isrc":"INA101300002","lyricsUrl":"https://lrc.red/s/INA101300002.ttml"}]}"""
        assertEquals("https://lrc.red/s/INA101300002.ttml", LrcRedProvider.pickBini(q, body))
    }

    @Test fun foreignHostIsNeverFollowedButIsrcStillWorks() {
        val body = """{"results":[{"track_name":"Tum Hi Ho","artist_name":"Arijit Singh","duration":262,"isrc":"INA101300002","lyricsUrl":"https://evil.example/x.ttml"}]}"""
        assertEquals("https://lrc.red/s/INA101300002.ttml", LrcRedProvider.pickBini(q, body))
    }

    @Test fun searchRejectsOtherArtistAndLengthAndBadIsrc() {
        val body = """{"hits":[
          {"title":"Tum Hi Ho","artist":"Someone Else","duration":262,"isrc":"INA101300003"},
          {"title":"Tum Hi Ho","artist":"Arijit Singh","duration":300,"isrc":"INA101300004"},
          {"title":"Tum Hi Ho","artist":"Arijit Singh","duration":262,"isrc":"bad"},
          {"title":"Tum Hi Ho","artist":"Arijit Singh","duration":263,"isrc":"INA101300005"}]}"""
        assertEquals("https://lrc.red/s/INA101300005.ttml", LrcRedProvider.pickSearch(q, body))
        assertNull(LrcRedProvider.pickSearch(q, """{"hits":[]}"""))
        assertNull(LrcRedProvider.pickBini(q, "not json"))
    }

    @Test fun documentUrlNeedsValidIsrc() {
        assertNull(LrcRedProvider.documentUrl("12"))
        assertTrue(LrcRedProvider.documentUrl("ina101300002")!!.endsWith("INA101300002.ttml"))
    }
}
