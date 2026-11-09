package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineFilterTest {
    private val day = 86_400_000L
    private val now = 500 * day

    private fun i(title: String, artist: String = "A", kind: SongKind = SongKind.OfficialAudio, age: Long? = 1, dur: Long = 200_000,
                  dl: Boolean = false, ch: String? = "https://www.youtube.com/channel/UC1") =
        OnlineFilter.Info(title, artist, ch, kind, if (age == null) 0 else now - age * day, dur, dl)

    private val items = listOf(
        i("b new audio", age = 3), i("a old audio", age = 200), i("c video", kind = SongKind.MusicVideo, age = 10),
        i("d live", kind = SongKind.Live, age = 5, artist = "B"), i("e nodate", age = null),
        i("f long", age = 2, dur = 600_000), i("g dl", age = 1, dl = true, ch = "https://www.youtube.com/channel/UC2"),
    )
    private fun run(f: OnlineFilter) = f.apply(items, now) { it }.map { it.title }

    @Test fun classifyTitles() {
        assertEquals(SongKind.OfficialAudio, SongKinds.classify("Song (Official Audio)", "T-Series"))
        assertEquals(SongKind.MusicVideo, SongKinds.classify("Song | Official Music Video", "X"))
        assertEquals(SongKind.Remix, SongKinds.classify("Song (Official Video) [Remix]", "X"))
        assertEquals(SongKind.Live, SongKinds.classify("Song Live at Wembley", "X"))
        assertEquals(SongKind.OfficialAudio, SongKinds.classify("Plain Title", "Artist - Topic"))
        assertEquals(SongKind.Other, SongKinds.classify("Plain Title", "Some Channel"))
    }

    @Test fun inactiveFilterChangesNothing() {
        assertFalse(OnlineFilter().isActive)
        assertEquals(items.map { it.title }, run(OnlineFilter()))
    }

    @Test fun combinedFiltersAreAnd() {
        // Artist = A AND last 30 days AND official audio, newest first
        val f = OnlineFilter(kinds = setOf(SongKind.OfficialAudio), maxAgeDays = 30, artist = "a", sort = OnlineFilter.Sort.Newest)
        assertEquals(listOf("g dl", "f long", "b new audio"), run(f))
    }

    @Test fun ageFilterDropsUnknownDatesInsteadOfGuessing() {
        assertFalse("e nodate" in run(OnlineFilter(maxAgeDays = 365)))
        assertTrue("e nodate" in run(OnlineFilter()))
    }

    @Test fun newestPutsUnknownLast() {
        val out = run(OnlineFilter(sort = OnlineFilter.Sort.Newest))
        assertEquals("e nodate", out.last())
        assertEquals("g dl", out.first())
    }

    @Test fun durationDownloadedAndChannel() {
        assertEquals(listOf("f long"), run(OnlineFilter(minDurationMs = 300_000)))
        assertEquals(listOf("g dl"), run(OnlineFilter(downloadedOnly = true)))
        assertEquals(listOf("g dl"), run(OnlineFilter(channelUrl = "https://www.youtube.com/channel/UC2")))
    }

    @Test fun alphabetical() {
        assertEquals("a old audio", run(OnlineFilter(sort = OnlineFilter.Sort.Alphabetical)).first())
    }
}
