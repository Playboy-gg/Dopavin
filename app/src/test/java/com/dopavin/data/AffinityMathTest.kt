package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AffinityMathTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test fun topArtistsCountsListensNotSongsAndDecays() {
        val listens = listOf("A" to now - 1 * day, "A" to now - 2 * day, "A" to now - 3 * day, "B" to now - 1 * day,
            "C" to now - 400 * day, "C" to now - 401 * day, "C" to now - 402 * day, "C" to now - 403 * day)
        assertEquals(listOf("a", "b", "c"), AffinityMath.topArtists(listens, now, 3))
        assertEquals(listOf("a"), AffinityMath.topArtists(listens, now, 1))
    }

    @Test fun blankAndHugeNamesIgnored() {
        assertTrue(AffinityMath.topArtists(listOf("" to now, "x".repeat(200) to now), now, 5).isEmpty())
    }

    @Test fun unknownSongHasNoTasteAndNoNovelty() {
        assertNull(AffinityMath.taste(0, 0, 0, 0L, false, now))
        assertNull(AffinityMath.rediscovery(0, 0, 0L, now))
        assertNull(AffinityMath.rediscovery(0, 0, now - 90 * day, now))
    }

    @Test fun likeIsStrongAndSkipsNeverGoBelowZero() {
        val liked = AffinityMath.taste(0, 0, 0, 0L, true, now)!!
        assertTrue(liked >= 0.8)
        assertEquals(0.0, AffinityMath.taste(3, 0, 3, now, false, now)!!, 1e-9)
    }

    @Test fun tasteFadesWithAge() {
        val fresh = AffinityMath.taste(5, 5, 0, now, false, now)!!
        val old = AffinityMath.taste(5, 5, 0, now - 300 * day, false, now)!!
        assertTrue(fresh > old)
        assertNotNull(old)
    }

    @Test fun rediscoveryNeedsFinishedPlaysAndGrowsWithTime() {
        assertNull(AffinityMath.rediscovery(1, 2, now - 60 * day, now)) // skipped more than finished
        val a = AffinityMath.rediscovery(2, 0, now - 5 * day, now)!!
        val b = AffinityMath.rediscovery(2, 0, now - 40 * day, now)!!
        assertTrue(b > a)
        assertEquals(1.0, b, 1e-9)
    }
}
