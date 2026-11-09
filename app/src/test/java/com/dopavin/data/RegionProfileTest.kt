package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionProfileTest {

    private fun queries(p: RegionProfile, learned: List<String> = emptyList()) =
        HomeCategories.plan(p, learned).map { it.query }

    @Test fun simCountryWinsThenNetworkThenPhoneRegion() {
        assertEquals("IN", RegionProfiles.build("in", "us", "gb", emptyList()).country)
        assertEquals("US", RegionProfiles.build(null, "US", "GB", emptyList()).country)
        assertEquals("GB", RegionProfiles.build("", "x", "GB", emptyList()).country)
        assertEquals("", RegionProfiles.build(null, null, null, emptyList()).country)
    }

    @Test fun languagesAreNormalisedAndKeepTheirOrder() {
        val p = RegionProfiles.build("IN", null, null, listOf("ta-IN", "en_US", "iw", "in", "ta", "pa-Guru-IN", "x"))
        assertEquals(listOf("ta", "en", "he", "id", "pa"), p.languages)
    }

    @Test fun anEnglishPhoneInIndiaKeepsTheOriginalSixTiles() {
        val plan = HomeCategories.plan(RegionProfiles.build("IN", null, null, listOf("en")), emptyList())
        assertEquals(6, plan.size)
        assertEquals(HomeCategories.CHARTS, plan.first())
        assertEquals(HomeCategories.LOFI, plan.last())
        assertEquals(
            setOf("new punjabi songs", "latest bollywood songs", "romantic hindi songs", "top english hits"),
            plan.map { it.query }.toSet() - setOf("top songs this week", "lofi chill songs"),
        )
        assertEquals(plan, HomeCategories.DEFAULT)
    }

    @Test fun aTamilPhoneGetsTamilRightAfterTheChartsAndNoPunjabiOrBollywood() {
        val q = queries(RegionProfiles.build("IN", null, null, listOf("ta", "en")))
        assertEquals(listOf("top songs this week", "latest tamil songs", "top english hits", "lofi chill songs"), q)
    }

    @Test fun aListenerAbroadIsNotGivenHindiAndPunjabiByDefault() {
        val q = queries(RegionProfiles.build("US", null, null, listOf("en")))
        assertEquals(listOf("top songs this week", "top english hits", "lofi chill songs"), q)
        assertFalse(q.any { "hindi" in it || "punjabi" in it || "bollywood" in it })
    }

    @Test fun aSpanishPhoneInSpainGetsSpanish() {
        val q = queries(RegionProfiles.build("ES", null, null, listOf("es")))
        assertEquals("latest spanish songs", q[1])
    }

    @Test fun whatTheListenerPlaysComesFirst() {
        val q = queries(RegionProfiles.build("IN", null, null, listOf("en")), learned = listOf("te"))
        assertEquals(listOf("top songs this week", "latest telugu songs", "top english hits", "lofi chill songs"), q)
    }

    @Test fun twoLearnedLanguagesBothShow() {
        val q = queries(RegionProfiles.build("IN", null, null, listOf("en")), learned = listOf("ta", "hi"))
        assertEquals("latest tamil songs", q[1])
        assertEquals("latest bollywood songs", q[2])
    }

    @Test fun planNeverExceedsTheLimitAndAlwaysEndsNeutral() {
        val busy = RegionProfiles.build("IN", null, null, listOf("ta", "te", "ml", "kn", "bn", "mr", "hi", "pa", "en"))
        val plan = HomeCategories.plan(busy, listOf("gu", "ur"))
        assertEquals(HomeCategories.MAX, plan.size)
        assertEquals("latest gujarati songs", plan[1].query)
        assertEquals(HomeCategories.LOFI, plan.last())
        assertEquals(plan.size, plan.map { it.query }.toSet().size)
    }

    @Test fun unknownLanguagesAreIgnored() {
        val q = queries(RegionProfiles.build("ZZ", null, null, listOf("xx", "qq")))
        assertEquals(listOf("top songs this week", "top english hits", "lofi chill songs"), q)
    }

    @Test fun scriptSaysWhichLanguageASongNameIsIn() {
        assertEquals("hi", ScriptLanguages.detect("\u0924\u0941\u092e \u0939\u0940 \u0939\u094b"))
        assertEquals("pa", ScriptLanguages.detect("\u0a2e\u0a48\u0a28\u0a42\u0a70 \u0a2c\u0a23\u0a3f\u0a06\u0a02"))
        assertEquals("ta", ScriptLanguages.detect("\u0b95\u0bbe\u0ba4\u0bb2\u0bcd"))
        assertNull(ScriptLanguages.detect("PARIS Guru Randhawa"))
        assertNull(ScriptLanguages.detect("9:45"))
    }

    @Test fun aLanguageNeedsSeveralSongsBeforeItIsLearned() {
        val tamil = "\u0b95\u0bbe\u0ba4\u0bb2\u0bcd"
        val hindi = "\u0924\u0941\u092e"
        assertEquals(listOf("ta"), ScriptLanguages.learn(listOf(tamil, tamil, tamil, hindi, "Song")))
        assertEquals(emptyList<String>(), ScriptLanguages.learn(listOf(tamil, tamil, hindi)))
        assertEquals(listOf("ta", "hi"), ScriptLanguages.learn(listOf(tamil, tamil, tamil, tamil, hindi, hindi, hindi)))
    }
}
