package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchAssistTest {
    private val vocab = listOf("Blinding Lights", "The Weeknd", "After Hours", "Believer", "Imagine Dragons", "Beyoncé", "Lose Yourself")

    @Test fun `whole name typo`() {
        assertEquals("Believer", SearchAssist.didYouMean("beliver", vocab))
        assertEquals("The Weeknd", SearchAssist.didYouMean("the wekend", vocab))
    }

    @Test fun `swap of neighbours counts as one edit`() {
        assertEquals("Believer", SearchAssist.didYouMean("beleiver", vocab))
    }

    @Test fun `word by word repair`() {
        assertEquals("Blinding Lights", SearchAssist.didYouMean("blindng lights", vocab))
        assertEquals("Lose Yourself", SearchAssist.didYouMean("lose yourselv", vocab))
    }

    @Test fun `no suggestion when the query already matches or is short`() {
        assertNull(SearchAssist.didYouMean("blind", vocab))
        assertNull(SearchAssist.didYouMean("beli", vocab))
        assertNull(SearchAssist.didYouMean("abc", vocab))
        assertNull(SearchAssist.didYouMean("Believer", vocab))
    }

    @Test fun `no suggestion when nothing is close`() {
        assertNull(SearchAssist.didYouMean("zzzzzzzz", vocab))
        assertNull(SearchAssist.didYouMean("beliver", emptyList()))
    }

    @Test fun `accents are ignored`() {
        assertEquals("Beyoncé", SearchAssist.didYouMean("beyonse", vocab))
        assertNull(SearchAssist.didYouMean("beyonce", vocab))
    }

    @Test fun `distance basics`() {
        assertEquals(1, SearchAssist.distance("ab", "ba", 3))
        assertEquals(2, SearchAssist.distance("kitten", "sittin", 3))
        assertEquals(4, SearchAssist.distance("abcdefgh", "a", 3))
    }
}
