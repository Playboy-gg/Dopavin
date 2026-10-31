package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchTest {
    private data class T(val title: String, val artist: String, val ms: Long, val tier: Int = 1)

    private fun dedupe(l: List<T>) = TrackMatch.dedupe(l, { it.title }, { it.artist }, { it.ms })

    @Test fun sameRecordingShownTwiceIsMerged() {
        val r = dedupe(listOf(T("Tum Hi Ho", "Arijit Singh", 262_000), T("Tum Hi Ho (Official Video)", "Arijit Singh", 263_000)))
        assertEquals(1, r.size)
        assertEquals("Tum Hi Ho", r[0].title) // the first (best ranked) one is kept
    }

    @Test fun remixLiveAndKaraokeAreNotMergedWithTheOriginal() {
        val r = dedupe(
            listOf(
                T("Song", "Band", 200_000), T("Song (Remix)", "Band", 200_000),
                T("Song (Live)", "Band", 200_000), T("Song - Karaoke Version", "Band", 200_000),
            ),
        )
        assertEquals(4, r.size)
    }

    @Test fun differentLengthSameTitleStays() {
        assertEquals(2, dedupe(listOf(T("Song", "Band", 200_000), T("Song", "Band", 245_000))).size)
    }

    @Test fun differentArtistsWithSimilarNamesStay() {
        assertEquals(2, dedupe(listOf(T("Song", "Arijit Singh", 200_000), T("Song", "Arijit Sing", 200_000))).size)
    }

    @Test fun unknownLengthDoesNotBlockMerging() {
        assertEquals(1, dedupe(listOf(T("Song", "Band", 0), T("Song", "Band", 210_000))).size)
    }

    @Test fun featuredArtistsDoNotSplitASong() {
        assertEquals(1, dedupe(listOf(T("Song", "Band", 200_000), T("Song", "Band, Guest", 200_000))).size)
    }

    @Test fun originalsRankAboveOtherVersionsUnlessAsked() {
        val items = listOf(T("Song (Remix)", "B", 1), T("Song", "B", 1), T("Song (Live)", "B", 1))
        val plain = TrackMatch.rank(items, "song", { it.title }, { it.tier })
        assertEquals("Song", plain.first().title)
        val asked = TrackMatch.rank(items, "song remix", { it.title }, { it.tier })
        // asked for the remix: it is not "other" any more, so it keeps its place before the live cut
        assertTrue(asked.indexOf(items[0]) < asked.indexOf(items[2]))
    }

    @Test fun officialUploadersComeFirstInsideTheSameGroup() {
        val items = listOf(T("Song", "B", 1, tier = 1), T("Song ", "B", 1, tier = 0))
        assertEquals(0, TrackMatch.rank(items, "song", { it.title }, { it.tier }).first().tier)
    }

    @Test fun rankIsStableAndRemovesNothing() {
        val items = listOf(T("A", "x", 1), T("B", "x", 1), T("C (Remix)", "x", 1), T("D", "x", 1))
        val r = TrackMatch.rank(items, "q", { it.title }, { it.tier })
        assertEquals(listOf("A", "B", "D", "C (Remix)"), r.map { it.title })
    }

    @Test fun channelKeysCompareByIdNotByUrlShape() {
        assertTrue(TrackMatch.sameChannel("https://www.youtube.com/channel/UCabc", "https://youtube.com/channel/UCabc/videos"))
        assertFalse(TrackMatch.sameChannel("https://www.youtube.com/channel/UCabc", "https://www.youtube.com/channel/UCxyz"))
        assertFalse(TrackMatch.sameChannel(null, "https://www.youtube.com/channel/UCabc"))
    }
}
