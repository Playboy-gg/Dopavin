package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Weighted mean of the present features, reasons, confidence, missing metadata. */
class RecScorerTest {

    private fun f(
        mood: Double? = null, taste: Double? = null, artist: Double? = null,
        session: Double? = null, novelty: Double? = null, relevance: Double? = null,
    ) = RecFeatures(mood, taste, artist, session, novelty, relevance)

    @Test fun allFeaturesAtOneScoreOne() {
        val s = score(f(1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
        assertEquals(1.0, s.value, 1e-9)
        assertEquals(1.0, s.confidence, 1e-9)
    }

    @Test fun everyPresentFeatureCountsNotOnlyTheBest() {
        // Old bug: the score was max(w*x)/total, so a second feature changed nothing.
        val one = score(f(relevance = 1.0)).value
        val two = score(f(relevance = 1.0, taste = 0.0)).value
        assertTrue("a known-zero taste must pull the score down", two < one)
        val strongBoth = score(f(relevance = 0.8, taste = 0.8)).value
        val strongOne = score(f(relevance = 0.8, taste = 0.2)).value
        assertTrue(strongBoth > strongOne)
    }

    @Test fun absentFeaturesDropOutInsteadOfScoringZero() {
        assertEquals(1.0, score(f(novelty = 1.0)).value, 1e-9)
        assertEquals(0.5, score(f(taste = 0.5)).value, 1e-9)
    }

    @Test fun confidenceIsTheShareOfWeightBackedByData() {
        val w = ScorerWeights()
        assertEquals(w.novelty / w.total, score(f(novelty = 1.0)).confidence, 1e-9)
        assertTrue(score(f(relevance = 0.5, taste = 0.5, session = 0.5)).confidence > score(f(session = 0.5)).confidence)
        assertEquals(0.0, score(RecFeatures()).confidence, 1e-9)
    }

    @Test fun reasonIsTheStrongestWeightedContribution() {
        assertEquals(ReasonCode.RELATED, score(f(relevance = 0.9, taste = 0.9)).reason) // 0.315 vs 0.18
        assertEquals(ReasonCode.TASTE, score(f(mood = 0.3, taste = 0.9)).reason)        // 0.075 vs 0.18
        assertEquals(ReasonCode.MOOD_MATCH, score(f(mood = 1.0, novelty = 1.0)).reason)
        assertEquals(ReasonCode.SESSION, score(f(session = 1.0, artist = 0.9)).reason)   // 0.10 vs 0.09
    }

    @Test fun noFeaturesIsZeroNotACrash() {
        val s = score(RecFeatures())
        assertEquals(0.0, s.value, 1e-9)
    }

    @Test fun valuesAreClampedToUnitRange() {
        assertEquals(1.0, score(f(taste = 7.0)).value, 1e-9)
        assertEquals(0.0, score(f(taste = -3.0)).value, 1e-9)
    }
}
