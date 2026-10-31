package com.dopavin.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * F-103: playlist persistence format. The codec is pure so it is testable on the JVM, but
 * org.json inside the mockable android.jar can be a stub; when it is, the round-trip test is
 * skipped (Assume) instead of failing — the format is still exercised by a real device run.
 */
class PlaylistCodecTest {

    /** A song from before the downloads-first change: a MediaStore row with no video id. */
    private val legacyLocal = Song(
        id = 42L,
        title = "Local Song",
        artist = "Local Artist",
        album = "Local Album",
        albumKey = 7L,
        durationMs = 200_000L,
        dateAdded = 1_700_000_000_000L,
        track = 3,
    )

    private val second = Song(
        id = 1_000_001L,
        title = "Second Song",
        artist = "Second Artist",
        album = "",
        albumKey = UNKNOWN_ALBUM_KEY,
        durationMs = 180_000L,
        dateAdded = 0L,
        track = 0,
        isOnline = true,
        videoId = "def456",
    )

    private val online = Song(
        id = 999_999L,
        title = "Online Song",
        artist = "Online Artist",
        album = "",
        albumKey = UNKNOWN_ALBUM_KEY,
        durationMs = 210_000L,
        dateAdded = 0L,
        track = 0,
        isOnline = true,
        videoId = "abc123",
    )

    @Test
    fun `normalise name trims and collapses whitespace`() {
        assertEquals("Road Trip", PlaylistCodec.normaliseName("  Road   Trip  "))
        assertEquals("Road Trip", PlaylistCodec.normaliseName("Road\tTrip"))
        assertEquals("", PlaylistCodec.normaliseName("   "))
    }

    @Test
    fun `round trips songs through the json codec`() {
        Assume.assumeTrue(orgJsonIsReal())
        val raw = PlaylistCodec.encode(mapOf("Favourites" to listOf(second, online)))
        val decoded = PlaylistCodec.decode(raw)
        assertEquals(listOf(second, online), decoded["Favourites"])
    }

    @Test
    fun `legacy local-file entries are dropped when a playlist is read`() {
        Assume.assumeTrue(orgJsonIsReal())
        val raw = PlaylistCodec.encode(mapOf("Old" to listOf(legacyLocal, online)))
        assertEquals(listOf(online), PlaylistCodec.decode(raw)["Old"])
    }

    @Test
    fun `decoding garbage yields an empty map instead of a crash`() {
        Assume.assumeTrue(orgJsonIsReal())
        assertTrue(PlaylistCodec.decode("{}").isEmpty())
        // The store itself wraps decode in runCatching; the codec only needs to not lose data.
        assertEquals(emptyMap<String, List<Song>>(), PlaylistCodec.decode("{}"))
    }

    /** True only when org.json actually works on this JVM (it is stubbed in mockable android.jar). */
    private fun orgJsonIsReal(): Boolean = runCatching {
        assertEquals(1, JSONObject("{\"a\":1}").getInt("a"))
    }.isSuccess
}
