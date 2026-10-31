package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun song(id: Long, artist: String) = Song(
    id = id,
    title = "Song $id",
    artist = artist,
    album = "",
    albumKey = "k$id",
    durationMs = 240_000L,
    dateAdded = 0L,
    track = 0,
)

/** Spec §15: queue deduplication, artist-run diversity. */
class QueuePlannerTest {

    private val scored = Scored(0.5, ReasonCode.TASTE)

    @Test
    fun `excludes ids already in the queue`() {
        val songs = listOf(song(1, "A"), song(2, "B"))
        val batch = planBatch(
            candidates = songs.map { Candidate(it, scored) },
            excludeIds = setOf(1L),
            size = 5,
        )
        assertEquals(listOf(2L), batch.map { it.song.id })
    }

    @Test
    fun `caps consecutive artist runs`() {
        val songs = (1L..6L).map { song(it, "Same") } + song(7L, "Other") + song(8L, "Other")
        val batch = planBatch(songs.map { Candidate(it, scored) }, emptySet(), size = 8)
        // No stretch of three same-artist rows in a general-purpose batch.
        var run = 1
        for (i in 1 until batch.size) {
            run = if (batch[i].song.artist == batch[i - 1].song.artist) run + 1 else 1
            assertTrue(run <= 2)
        }
    }

    @Test
    fun `higher scores come first`() {
        val low = Candidate(song(1, "A"), Scored(0.1, ReasonCode.TASTE))
        val high = Candidate(song(2, "B"), Scored(0.9, ReasonCode.MOOD_MATCH))
        val batch = planBatch(listOf(low, high), emptySet(), size = 2)
        assertEquals(2L, batch[0].song.id)
        assertEquals(ReasonCode.MOOD_MATCH, batch[0].reason)
    }

    @Test
    fun `empty ranking yields an empty batch, not retries`() {
        assertTrue(planBatch(emptyList(), emptySet(), size = 5).isEmpty())
        val blocked = Candidate(song(1, "A"), scored)
        assertTrue(planBatch(listOf(blocked), setOf(1L), size = 5).isEmpty())
    }

    @Test
    fun `deduplicates repeated candidates`() {
        val s = song(1, "A")
        val batch = planBatch(listOf(Candidate(s, scored), Candidate(s, scored)), emptySet(), size = 5)
        assertEquals(1, batch.size)
    }
}
