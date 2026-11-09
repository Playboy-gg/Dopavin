package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsQueryPlanTest {
    private fun meta(t: String, a: String = "", al: String = "") = LyricsQueryPlan.Meta(t, a, al, 250, "vid")

    @Test fun exampleFromTheBrief() {
        val plan = LyricsQueryPlan.build(meta("Tum Hi Ho (Aashiqui 2) Arijit Singh Official Video", "Arijit Singh"))
        val pairs = plan.map { it.title to it.artist }
        assertEquals("Tum Hi Ho (Aashiqui 2) Arijit Singh Official Video" to "Arijit Singh", pairs[0]) // exact first
        assertEquals("Tum Hi Ho" to "Arijit Singh", pairs[1])                                              // cleaned + artist
        assertEquals("Tum Hi Ho" to "", pairs[2])                                                          // cleaned only
        assertTrue(plan.any { it.title == "Tum Hi Ho" && it.album == "Aashiqui 2" })                       // movie from the title
    }

    @Test fun originalMetadataIsKeptInEveryQuery() {
        val raw = "Song (Live) [4K]"
        val plan = LyricsQueryPlan.build(meta(raw, "Band"))
        assertTrue(plan.all { it.rawTitle == raw })
        assertEquals(raw, plan.first().title)
    }

    @Test fun artistLessQueriesAreStrictOnlyWhenAnArtistExisted() {
        assertTrue(LyricsQueryPlan.build(meta("Song", "Band")).first { it.artist.isBlank() }.strict)
        assertTrue(LyricsQueryPlan.build(meta("Song", "")).none { it.strict })
    }

    @Test fun noDuplicatesAndBounded() {
        val plan = LyricsQueryPlan.build(meta("Song", "Band", "Album"))
        assertEquals(plan.size, plan.map { Triple(it.title.lowercase(), it.artist.lowercase(), it.album.lowercase()) }.toSet().size)
        assertTrue(plan.size <= LyricsQueryPlan.MAX_QUERIES)
        assertEquals("Song" to "Band", plan.first().let { it.title to it.artist })
    }

    @Test fun albumFromMetadataIsUsed() {
        val plan = LyricsQueryPlan.build(meta("Song", "Band", "The Album"))
        assertTrue(plan.any { it.title == "Song" && it.album == "The Album" && it.artist == "Band" })
    }

    @Test fun topicAndVevoSuffixesAreDroppedFromTheArtist() {
        val plan = LyricsQueryPlan.build(meta("Song", "Band - Topic"))
        assertTrue(plan.any { it.artist == "Band" })
        assertEquals("Band", LyricsQueryPlan.cleanArtist("BandVEVO"))
    }

    @Test fun firstOfSeveralArtistsIsAnAlternative() {
        val plan = LyricsQueryPlan.build(meta("Song", "Alpha, Beta & Gamma"))
        assertTrue(plan.any { it.artist == "Alpha" })
    }

    @Test fun artistDashTitleIsReadBothWays() {
        val plan = LyricsQueryPlan.build(meta("Alpha - Some Song", ""))
        assertTrue(plan.any { it.title == "Some Song" && it.artist == "Alpha" })
        assertTrue(plan.any { it.title == "Alpha" && it.artist == "Some Song" })
    }

    @Test fun usefulBracketsAreKeptOutOfTheCleanTitleButNotLost() {
        val p = LyricsQueryPlan.parse("Song (feat. Guest) (Remix) (From \"Big Movie\")", "Band")
        assertEquals("Song", p.clean)
        assertEquals(listOf("Guest"), p.featured)
        assertEquals(listOf("Remix"), p.versions)
        assertEquals(listOf("Big Movie"), p.extras)
        // the version tag still gets its own query
        assertTrue(LyricsQueryPlan.build(meta("Song (Remix)", "Band")).any { it.title.contains("Remix") && it.title != "Song (Remix)" })
    }

    @Test fun noiseWordsAreRemovedButTitleWordsStay() {
        assertEquals("Fire", LyricsQueryPlan.parse("Fire (Official Audio) 4K HD Remastered 2011", "").clean)
        assertEquals("Live Forever", LyricsQueryPlan.parse("Live Forever", "").clean)
        assertEquals("Hello", LyricsQueryPlan.parse("Hello - Remastered", "Adele").clean)
    }

    @Test fun artistInTheMiddleOfATitleIsNotCut() {
        assertEquals("Love Me Like You Do", LyricsQueryPlan.parse("Love Me Like You Do", "Me").clean)
    }

    @Test fun blankTitleGivesNoQueries() {
        assertTrue(LyricsQueryPlan.build(meta("   ", "Band")).isEmpty())
    }

    @Test fun matcherRejectsSimilarButDifferentSongs() {
        val query = LyricsQuery("Love", "Band", "", 200, null)
        assertNull(LyricsMatcher.score(query, "Love Story", "Band", 200.0)) // "love" is not "love story"
        assertNull(LyricsMatcher.score(query, "Hate", "Band", 200.0))
        assertNull(LyricsMatcher.score(query, "Love", "Other Artist", 200.0))
        assertNull(LyricsMatcher.score(query, "Love", "Band", 260.0))       // far off in length
    }

    @Test fun matcherToleratesHarmlessDifferences() {
        val query = LyricsQuery("Tum Hi Ho", "Arijit Singh", "", 262, null)
        assertTrue(LyricsMatcher.score(query, "  tum  hi ho!! ", "ARIJIT SINGH", 262.0)!! >= 0.9f)
        assertTrue(LyricsMatcher.score(query, "Tum Hi Ho (From \"Aashiqui 2\")", "Arijit Singh", 261.0)!! > 0.7f)
        assertTrue(LyricsMatcher.score(query, "Tum Hi Ho Aashiqui 2", "Arijit Singh", 262.0) != null) // longer shared title
    }

    @Test fun matcherKeepsVersionsApart() {
        val original = LyricsQuery("Song", "Band", "", 200, null, rawTitle = "Song")
        assertNull(LyricsMatcher.score(original, "Song (Remix)", "Band", 200.0))
        assertNull(LyricsMatcher.score(original, "Song (Karaoke Version)", "Band", 200.0))
        val remix = LyricsQuery("Song", "Band", "", 200, null, rawTitle = "Song (Remix)")
        assertNull(LyricsMatcher.score(remix, "Song", "Band", 200.0))
        assertTrue(LyricsMatcher.score(remix, "Song (Remix)", "Band", 200.0) != null)
        // live is a softer difference: still the same lyrics, only a small penalty
        val live = LyricsMatcher.score(original, "Song (Live)", "Band", 200.0)
        val studio = LyricsMatcher.score(original, "Song", "Band", 200.0)
        assertTrue(live != null && studio != null && live < studio)
        assertFalse(LyricsMatcher.versionTags("Plain Song").isNotEmpty())
    }

    @Test fun cleanedQueryIsNotDroppedWhenOnlyBracketsDiffer() {
        // "Song (Official Video)" and "Song" normalise alike, but they are different search texts.
        val pairs = LyricsQueryPlan.build(meta("Tum Hi Ho (Official Video)", "Arijit Singh")).map { it.title to it.artist }
        assertEquals("Tum Hi Ho (Official Video)" to "Arijit Singh", pairs[0])
        assertEquals("Tum Hi Ho" to "Arijit Singh", pairs[1])
        assertEquals("Tum Hi Ho" to "", pairs[2])
    }

    @Test fun plainTitleGivesNoDuplicateQuery() {
        val pairs = LyricsQueryPlan.build(meta("PARIS", "Guru Randhawa")).map { it.title to it.artist }
        assertEquals(listOf("PARIS" to "Guru Randhawa", "PARIS" to ""), pairs)
    }

    @Test fun singerAtTheEndOfTheTitleIsTriedWhenTheUploaderIsALabel() {
        val plan = LyricsQueryPlan.build(meta("CALIFORNIA LOVE Cheema Y", "Brown Town Music"))
        assertTrue(plan.any { it.title == "CALIFORNIA LOVE" && it.artist == "Cheema Y" })
        assertTrue(plan.any { it.title == "CALIFORNIA LOVE" && it.artist.isBlank() && it.strict })
        assertTrue(plan.size <= LyricsQueryPlan.MAX_QUERIES)
    }

    @Test fun aPersonAsUploaderDoesNotGetTrailingWordGuesses() {
        val plan = LyricsQueryPlan.build(meta("Tera Ban Jaunga", "Akhil Sachdeva"))
        assertTrue(plan.none { it.title == "Tera Ban" })
    }

    @Test fun labelDetection() {
        assertTrue(LyricsQueryPlan.looksLikeLabel("T-Series"))
        assertTrue(LyricsQueryPlan.looksLikeLabel("Brown Town Music"))
        assertFalse(LyricsQueryPlan.looksLikeLabel("Arijit Singh"))
    }

    @Test fun indicScriptWordsStayWholeWhenNormalised() {
        // Vowel signs are marks, not letters: they used to turn every word into loose single letters.
        assertEquals("\u0924\u0941\u092e \u0939\u0940 \u0939\u094b", LyricsMatcher.normalise("\u0924\u0941\u092e \u0939\u0940 \u0939\u094b (Lyrics)"))
        assertEquals(3, LyricsMatcher.normalise("\u0a2e\u0a48\u0a28\u0a42\u0a70 \u0a2c\u0a23\u0a3f\u0a06\u0a02 \u0a24\u0a47\u0a30\u0a47").split(' ').size)
    }
}
