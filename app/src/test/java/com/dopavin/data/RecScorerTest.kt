package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §15: score calculation, missing-metadata behaviour, reason codes. */
class RecScorerTest {

    private fun f(
        mood: Double? = null,
        taste: Double? = null,
        artist: Double? = null,
        session: Double? = null,
        novelty: Double? = null,
    ) = RecFeatures(mood, taste, artist, session, novelty)

    @Test
    fun `all features present normalises to 0..1`() {
        val s = score(f(1.0, 1.0, 1.0, 1.0, 1.0))
        assertEquals(1.0, s.value, 1e-9)
    }

    @Test
    fun `absent features drop out of the denominator instead of scoring zero`() {
        // Only novelty: normalised to 1.0 because nothing else competes (no 0.10/2.0 dilution).
        val only = score(f(novelty = 1.0))
        assertEquals(1.0, only.value, 1e-9)
        assertEquals(ReasonCode.REDISCOVERY, only.reason)

        // A half-weight feature with nothing else still normalises high, but stays lower than
        // a same-value feature on a bigger weight.
        val moodHigh = score(f(mood = 1.0)).value
        val noveltyHigh = score(f(novelty = 1.0)).value
        assertTrue(noveltyHigh > moodHigh - 1e-9)
    }

    @Test
    fun `reason follows the strongest weighted feature`() {
        // taste 0.4 * 0.25 = 0.10 vs mood 0.3 * 0.25 = 0.075 -> taste wins.
        assertEquals(ReasonCode.TASTE, score(f(mood = 0.3, taste = 0.4)).reason)
        // artist 0.9 * 0.10 = 0.09 vs session 1.0 * 0.10 = 0.10 -> session wins.
        assertEquals(ReasonCode.SESSION, score(f(session = 1.0, artist = 0.9)).reason)
        // mood 1.0 * 0.25 = 0.25 vs novelty 1.0 * 0.10 = 0.10 -> mood wins.
        assertEquals(ReasonCode.MOOD_MATCH, score(f(mood = 1.0, novelty = 1.0)).reason)
    }

    @Test
    fun `no features at all is a zero taste score, not a crash`() {
        val s = score(RecFeatures())
        assertEquals(0.0, s.value, 1e-9)
    }

    @Test
    fun `scores grow with the feature value`() {
        val weak = score(f(taste = 0.2, novelty = 0.1)).value
        val strong = score(f(taste = 0.8, novelty = 0.9)).value
        assertTrue(strong > weak)
    }
}
