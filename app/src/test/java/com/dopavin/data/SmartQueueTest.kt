package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartQueueTest {
    private fun c(id: String, title: String, artist: String, ms: Long = 200_000, rel: Int? = null, heard: Boolean = false) =
        SmartQueue.Cand(id, title, artist, ms, relatedRank = rel, heardRecently = heard)

    @Test fun relatedOrderWins() {
        val out = SmartQueue.pick(listOf(c("b", "B", "x", rel = 3), c("a", "A", "y", rel = 0)), emptyList(), "Cur", 2)
        assertEquals(listOf("a", "b"), out)
    }

    @Test fun queuedRecordingUnderOtherIdIsDropped() {
        val queued = listOf(Triple("Tum Hi Ho", "Arijit Singh", 262_000L))
        val out = SmartQueue.pick(listOf(c("v2", "Tum Hi Ho", "Arijit Singh", 261_000, rel = 0), c("o", "Other", "Z", rel = 1)), queued, "Cur", 2)
        assertEquals(listOf("o"), out)
    }

    @Test fun otherVersionsSkippedUnlessCurrentIsOne() {
        val pool = listOf(c("r", "Song (Remix)", "A", rel = 0), c("n", "Song 2", "B", rel = 1))
        assertEquals(listOf("n"), SmartQueue.pick(pool, emptyList(), "Cur", 2))
        assertEquals(listOf("r", "n"), SmartQueue.pick(pool, emptyList(), "Cur (Remix)", 2))
    }

    @Test fun recentlyHeardSinksAndArtistRunCapped() {
        val pool = listOf(
            c("1", "T1", "A", rel = 0), c("2", "T2", "A", rel = 1), c("3", "T3", "A", rel = 2),
            c("4", "T4", "B", rel = 3, heard = true), c("5", "T5", "C", rel = 4),
        )
        val out = SmartQueue.pick(pool, emptyList(), "Cur", 4)
        assertEquals(listOf("1", "2", "5", "4"), out)
    }

    @Test fun clipsAndMixesSkipped() {
        val out = SmartQueue.pick(listOf(c("s", "Short", "A", 20_000, rel = 0), c("l", "Mix", "A", 3_600_000, rel = 1), c("ok", "Ok", "A", rel = 2)), emptyList(), "Cur", 3)
        assertEquals(listOf("ok"), out)
        assertTrue(SmartQueue.pick<String>(emptyList(), emptyList(), "x", 3).isEmpty())
        assertFalse(out.contains("s"))
    }

    @Test fun blockedNeverPassesEvenWhenTopRanked() {
        val pool = listOf(
            SmartQueue.Cand("x", "Blocked", "A", 200_000, relatedRank = 0, blocked = true),
            SmartQueue.Cand("y", "Fine", "B", 200_000, relatedRank = 5),
        )
        assertEquals(listOf("y"), SmartQueue.pick(pool, emptyList(), "Cur", 3))
    }

    @Test fun unknownSongGetsNoNoveltyRewardOverARelatedOne() {
        val related = SmartQueue.Cand("rel", "Related", "A", 200_000, relatedRank = 2, taste = null)
        val unknown = SmartQueue.Cand("unk", "Unknown", "B", 200_000)
        val out = SmartQueue.rank(listOf(unknown, related), emptyList(), "Cur", 2)
        assertEquals("rel", out[0].item)
        assertEquals(ReasonCode.RELATED, out[0].reason)
        assertTrue(out[0].confidence > out[1].confidence)
    }

    @Test fun relatedRankIsKeptAgainstALikedLocalSongWithoutRelevance() {
        val radio = SmartQueue.Cand("r0", "First related", "A", 200_000, relatedRank = 0)
        val liked = SmartQueue.Cand("l", "Liked local", "B", 200_000, taste = 0.9, favouriteArtist = true)
        val out = SmartQueue.pick(listOf(liked, radio), emptyList(), "Cur", 2)
        assertEquals("r0", out[0]) // relevance 1.0 beats taste 0.9 + artist 0.9 mean (0.9)
    }

    @Test fun moodScoreOnlyCountsWhenGiven() {
        val withMood = SmartQueue.Cand("m", "Mood", "A", 200_000, moodMatch = 0.6, relatedRank = 3)
        val noMood = SmartQueue.Cand("n", "NoMood", "B", 200_000, relatedRank = 3)
        val out = SmartQueue.rank(listOf(noMood, withMood), emptyList(), "Cur", 2)
        assertTrue(out.first { it.item == "m" }.confidence > out.first { it.item == "n" }.confidence)
    }
}
