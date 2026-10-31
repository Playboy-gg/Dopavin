package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapMathTest {
    private val day = 86_400_000L
    private val now = 100 * day + 12 * 3_600_000L // noon, UTC day 100

    private fun entry(key: String, artist: String, daysAgo: Int, playedMs: Long = 180_000L) = ListenEntry(
        key = key, title = "T-$key", artist = artist, videoId = key, songId = 0L, thumbUri = null,
        playedMs = playedMs, at = now - daysAgo * day,
    )

    @Test fun listenCountsAfterThirtySeconds() {
        assertFalse(RecapMath.counts(29_999L, 200_000L))
        assertTrue(RecapMath.counts(30_000L, 200_000L))
    }

    @Test fun shortSongCountsAfterHalf() {
        assertFalse(RecapMath.counts(9_000L, 20_000L))
        assertTrue(RecapMath.counts(10_000L, 20_000L))
    }

    @Test fun rangeKeepsOnlyRecentDays() {
        val list = listOf(entry("a", "X", 0), entry("b", "X", 6), entry("c", "X", 7), entry("d", "X", 40))
        assertEquals(2, RecapMath.summarize(list, 7, now, 0L).plays)
        assertEquals(3, RecapMath.summarize(list, 30, now, 0L).plays)
        assertEquals(4, RecapMath.summarize(list, 0, now, 0L).plays)
    }

    @Test fun minutesAndDistinctCounts() {
        val list = listOf(entry("a", "X", 0), entry("a", "x", 1), entry("b", "Y", 1, 60_000L))
        val s = RecapMath.summarize(list, 7, now, 0L)
        assertEquals(7L, s.minutes) // 3 + 3 + 1
        assertEquals(3, s.plays)
        assertEquals(2, s.songs)
        assertEquals(2, s.artists) // "X" and "x" are one artist
    }

    @Test fun topSongsAndArtistsAreRankedByPlays() {
        val list = listOf(entry("a", "X", 0), entry("a", "X", 1), entry("a", "X", 2), entry("b", "Y", 0), entry("c", "Y", 0))
        val s = RecapMath.summarize(list, 7, now, 0L)
        assertEquals("a", s.topSongs.first().song.videoId)
        assertEquals(3, s.topSongs.first().plays)
        assertEquals("X", s.topArtists.first().name)
    }

    @Test fun dailyMinutesEndToday() {
        val list = listOf(entry("a", "X", 0, 120_000L), entry("b", "X", 6, 60_000L))
        val d = RecapMath.summarize(list, 7, now, 0L).dailyMinutes
        assertEquals(7, d.size)
        assertEquals(2, d.last())
        assertEquals(1, d.first())
    }

    @Test fun zoneOffsetMovesTheDayBoundary() {
        // 23:30 UTC on day 99 is already day 100 at UTC+5:30.
        val late = ListenEntry("k", "t", "a", "k", 0L, null, 60_000L, 99 * day + 23 * 3_600_000L + 30 * 60_000L)
        val ist = 5 * 3_600_000L + 30 * 60_000L
        val s = RecapMath.summarize(listOf(late), 7, now, ist)
        assertEquals(1, s.dailyMinutes.last())
    }

    @Test fun allTimeHasNoChart() {
        assertTrue(RecapMath.summarize(listOf(entry("a", "X", 0)), 0, now, 0L).dailyMinutes.isEmpty())
    }

    @Test fun shareTextHasLinkForOnlineSongs() {
        val song = Song(1, "Title", "Artist", "", UNKNOWN_ALBUM_KEY, 0, 0, 0, isOnline = true, videoId = "abc")
        assertEquals("Title - Artist\nhttps://music.youtube.com/watch?v=abc", com.dopavin.ui.components.shareTextFor(song))
    }
}

class SpotifyImportTest {
    @Test fun parsesPlaylistAndAlbumLinks() {
        assertEquals("playlist" to "37i9dQZF1DXcBWIGoYBM5M", SpotifyImport.parseLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"))
        assertEquals("album" to "4aawyAB9vmqN3uQ7FjRGTy", SpotifyImport.parseLink("see https://open.spotify.com/intl-fr/album/4aawyAB9vmqN3uQ7FjRGTy"))
        assertEquals("playlist" to "37i9dQZF1DXcBWIGoYBM5M", SpotifyImport.parseLink("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals(null, SpotifyImport.parseLink("https://example.com/playlist/123"))
    }

    @Test fun readsTrackListFromEmbedPage() {
        val html = """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{"name":"Road Trip","trackList":[{"title":"Song A","subtitle":"Artist One"},{"title":"","subtitle":"x"},{"title":"Song B","subtitle":"Artist Two"}]}}}}}}</script></html>"""
        val list = SpotifyImport.parsePage(html)!!
        assertEquals("Road Trip", list.name)
        assertEquals(listOf(SpotifyTrack("Song A", "Artist One"), SpotifyTrack("Song B", "Artist Two")), list.tracks)
    }

    @Test fun unknownPageIsNull() {
        assertEquals(null, SpotifyImport.parsePage("<html>nothing</html>"))
        assertEquals(null, SpotifyImport.parsePage("""<script id="__NEXT_DATA__">{"a":1}</script>"""))
    }
}
