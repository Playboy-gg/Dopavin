package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistPickTest {
    private fun hit(n: String, subs: Long, v: Boolean = false) = ArtistPick.Hit(n, "u/$n", null, subs, v)

    @Test fun verifiedBigChannelBeatsSmallReupload() {
        val hits = listOf(hit("Arijit Singh Songs", 5_000), hit("Arijit Singh", 30_000_000, true))
        assertEquals("Arijit Singh", ArtistPick.best("Arijit Singh", hits)?.name)
    }

    @Test fun noNameMatchGivesNull() {
        assertNull(ArtistPick.best("Arijit Singh", listOf(hit("Random Beats", 9_000_000, true))))
    }

    @Test fun topicSuffixIgnored() {
        assertEquals(3, ArtistPick.nameMatch("Arijit Singh", "Arijit Singh - Topic"))
    }

    @Test fun demotionIsStable() {
        val items = listOf("a" to 1, "b" to 0, "c" to 1, "d" to 0)
        assertEquals(listOf("b", "d", "a", "c"), ArtistPick.demoteUnofficial(items) { it.second }.map { it.first })
    }

    @Test fun tiers() {
        assertEquals(0, ArtistPick.uploaderTier("Foo - Topic", false))
        assertEquals(0, ArtistPick.uploaderTier("Foo", true))
        assertEquals(1, ArtistPick.uploaderTier("Local Music Hub", false))
    }

    @Test fun rankPutsMatchesFirst() {
        val r = ArtistPick.rank("arijit", listOf(hit("Zed", 99_000_000, true), hit("Arijit Singh", 1_000)))
        assertEquals("Arijit Singh", r.first().name)
    }
}
