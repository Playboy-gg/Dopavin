package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun localSong(id: Long, title: String, artist: String, durationMs: Long = 240_000L) = Song(
    id = id,
    title = title,
    artist = artist,
    album = "",
    albumKey = 0L,
    durationMs = durationMs,
    dateAdded = 0L,
    track = 0,
)

private fun onlineSong(id: Long, title: String, artist: String) = Song(
    id = id,
    title = title,
    artist = artist,
    album = "",
    albumKey = 0L,
    durationMs = 240_000L,
    dateAdded = 0L,
    track = 0,
    isOnline = true,
    videoId = "v$id",
)

/** F-105: duplicate candidates. Pure functions, so these run on the JVM. */
class DuplicatesTest {

    @Test
    fun `key folds case punctuation and whitespace`() {
        assertEquals(duplicateKey("Song  LIVE!", "A R  Rahman"), duplicateKey("song   live", "a.r. rahman"))
        assertNotEquals(duplicateKey("Song", "Artist A"), duplicateKey("Song", "Artist B"))
    }

    @Test
    fun `empty or unknown artists never group`() {
        assertEquals("", duplicateKey("Song", ""))
        assertEquals("", duplicateKey("", "Artist"))
    }

    @Test
    fun `groups only same song same artist within duration tolerance`() {
        val duplicates = findDuplicates(
            listOf(
                localSong(1, "Midnight City", "M83", 244_000),
                localSong(2, "midnight city", "M83", 245_500), // 1.5s apart: inside tolerance
                localSong(3, "Midnight City", "M83", 300_000), // live version: too far apart
            ),
        )
        assertEquals(1, duplicates.size)
        assertEquals(listOf(1L, 2L), duplicates[0].songs.map { it.id })
    }

    @Test
    fun `different artists with the same title do not group`() {
        val groups = findDuplicates(
            listOf(
                localSong(1, "Yesterday", "The Beatles"),
                localSong(2, "Yesterday", "Some Cover Band"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `online and very short songs are ignored`() {
        val groups = findDuplicates(
            listOf(
                onlineSong(1, "Streaming Hit", "Online Artist"),
                onlineSong(2, "Streaming Hit", "Online Artist"),
                localSong(3, "Tiny Jingle", "Local Artist", 10_000),
                localSong(4, "Tiny Jingle", "Local Artist", 11_000),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `groups come back sorted by artist then title`() {
        val groups = findDuplicates(
            listOf(
                localSong(1, "B Track", "Zed Artist"),
                localSong(2, "B Track", "Zed Artist"),
                localSong(3, "A Track", "Abe Artist"),
                localSong(4, "A Track", "Abe Artist"),
            ),
        )
        assertEquals("Abe Artist", groups[0].artist)
        assertEquals("Zed Artist", groups[1].artist)
        // Group titles fold to the shortest spelling seen.
        assertEquals("A Track", groups[0].title)
    }
}
