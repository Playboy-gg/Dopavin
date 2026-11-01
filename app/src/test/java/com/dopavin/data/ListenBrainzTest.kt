package com.dopavin.data

import org.junit.Assert.*
import org.junit.Test

class ListenBrainzTest {
    @Test fun countsAfterHalfOrFourMinutes() {
        assertFalse(ListenBrainz.shouldScrobble(60_000, 200_000))
        assertTrue(ListenBrainz.shouldScrobble(100_000, 200_000))
        assertFalse(ListenBrainz.shouldScrobble(200_000, 600_000))
        assertTrue(ListenBrainz.shouldScrobble(240_000, 600_000))
    }
    @Test fun shortTracksAndUnknownLength() {
        assertFalse(ListenBrainz.shouldScrobble(29_000, 29_000))
        assertFalse(ListenBrainz.shouldScrobble(30_000, 0))
        assertTrue(ListenBrainz.shouldScrobble(60_000, 0))
    }
    @Test fun topicSuffixRemoved() {
        assertEquals("Arijit Singh", ListenBrainz.cleanArtist("Arijit Singh - Topic"))
        assertEquals("Queen", ListenBrainz.cleanArtist("  Queen "))
    }
    @Test fun payloadIsValidJsonWithEscapes() {
        val item = ListenBrainz.listenItem("A \"B\" - Topic", "Line1\nLine2 \\ x", "Alb", 1700000000L, 123456)
        val body = ListenBrainz.body("single", listOf(item))
        val root = MiniJson.obj(MiniJson.parse(body))!!
        assertEquals("single", root["listen_type"])
        val listen = MiniJson.obj(MiniJson.list(root["payload"])!![0])!!
        assertEquals(1700000000.0, listen["listened_at"])
        val meta = MiniJson.obj(listen["track_metadata"])!!
        assertEquals("A \"B\"", meta["artist_name"])
        assertEquals("Line1\nLine2 \\ x", meta["track_name"])
        assertEquals("Alb", meta["release_name"])
        assertEquals(123456.0, MiniJson.obj(meta["additional_info"])!!["duration_ms"])
    }
    @Test fun playingNowHasNoTimestamp() {
        val item = ListenBrainz.listenItem("A", "T", "", null, 0)
        assertFalse(item.contains("listened_at"))
        assertFalse(item.contains("release_name"))
        assertNotNull(MiniJson.parse(ListenBrainz.body("playing_now", listOf(item))))
    }
}
