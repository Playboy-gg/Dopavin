package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeTest {
    @Test fun realWordsAreKept() {
        val w = listOf(LyricWord(0, 100, "a"))
        assertEquals(w, Karaoke.words(LyricLine(0, "a", w), 5000))
    }

    @Test fun estimatedWordsAreOrderedAndInsideLine() {
        val line = LyricLine(1000, "hello big world", emptyList())
        val ws = Karaoke.words(line, 5000)
        assertEquals(3, ws.size)
        assertEquals(1000L, ws.first().startMs)
        assertTrue(ws.zipWithNext().all { (a, b) -> a.endMs <= b.startMs + 1 && a.startMs < b.startMs })
        assertTrue(ws.last().endMs <= 5000)
    }

    @Test fun longWordsTakeLonger() {
        val ws = Karaoke.words(LyricLine(0, "a extraordinary", emptyList()), 4000)
        assertTrue(ws[1].endMs - ws[1].startMs > ws[0].endMs - ws[0].startMs)
    }

    @Test fun longPauseIsNotStretched() {
        val ws = Karaoke.words(LyricLine(0, "hi there", emptyList()), 60_000)
        assertTrue(ws.last().endMs <= 9_000)
    }

    @Test fun emptyTextGivesNoWords() {
        assertTrue(Karaoke.words(LyricLine(0, "  ", emptyList()), 1000).isEmpty())
    }

    @Test fun charSweepIsMonotonicAndComplete() {
        val n = 6
        var prev = FloatArray(n)
        for (step in 0..20) {
            val p = step / 20f
            val cur = FloatArray(n) { Karaoke.charProgress(p, it, n) }
            for (i in 0 until n) assertTrue(cur[i] >= prev[i] - 1e-6f)
            for (i in 1 until n) assertTrue(cur[i] <= cur[i - 1] + 1e-6f)
            prev = cur
        }
        assertEquals(1f, prev[n - 1], 1e-4f)
        assertEquals(0f, Karaoke.charProgress(0f, 0, n), 1e-6f)
    }
}

class KaraokeUnitsTest {
    @Test fun latinIsPerLetter() {
        assertEquals(listOf("h", "i"), Karaoke.units("hi"))
    }

    @Test fun devanagariMatrasStayWithBase() {
        // "प्यार": प + virama + य + ा + र  -> conjunct "प्या" stays whole, then "र"
        assertEquals(listOf("प्या", "र"), Karaoke.units("प्यार"))
        // "दिल" : द+ि , ल
        assertEquals(listOf("दि", "ल"), Karaoke.units("दिल"))
        // anusvara and chandrabindu
        assertEquals(listOf("हैं"), Karaoke.units("हैं"))
        assertEquals(listOf("क्ष", "मा"), Karaoke.units("क्षमा"))
    }

    @Test fun arabicIsOneUnit() {
        assertEquals(listOf("حبيبي"), Karaoke.units("حبيبي"))
    }

    @Test fun cjkAndEmojiAndThai() {
        assertEquals(listOf("愛", "し", "て"), Karaoke.units("愛して"))
        assertEquals(listOf("👍🏽"), Karaoke.units("👍🏽"))
        assertEquals(listOf("ที่"), Karaoke.units("ที่"))
    }

    @Test fun unitsAlwaysRejoinToTheWord() {
        for (w in listOf("प्यार", "क्षमा", "கண்ணே", "ಕ್ಷಣ", "হৃদয়", "ਦਿਲ", "السلام", "こんにちは", "한국어", "naïve")) {
            assertEquals(w, Karaoke.units(w).joinToString(""))
        }
    }
}
