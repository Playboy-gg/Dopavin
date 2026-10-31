package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParsersTest {

    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
        <p begin="00:10.000" end="00:12.000"><span begin="00:10.000" end="00:10.500">Hel</span><span begin="00:10.500" end="00:11.000">lo</span> <span begin="00:11.000" end="00:12.000">world</span></p>
        <p begin="00:13.000" end="00:15.000"><span begin="00:13.000" end="00:14.000">Second</span> <span begin="00:14.000" end="00:15.000">line</span><span ttm:role="x-bg" begin="00:14.000" end="00:15.000">(ooh)</span></p>
        </div></body></tt>
    """.trimIndent()

    @Test
    fun `ttml keeps word and syllable timing`() {
        val lines = parseTtml(ttml)
        assertEquals(2, lines.size)
        val first = lines[0]
        assertEquals(10_000L, first.timeMs)
        assertEquals("Hello world", first.text)
        assertEquals(3, first.words.size)
        assertEquals("Hel", first.words[0].text)
        assertEquals(false, first.words[0].spaceAfter) // syllable of the same word
        assertEquals(true, first.words[1].spaceAfter)
        assertEquals(11_000L, first.words[2].startMs)
        assertEquals(12_000L, first.words[2].endMs)
    }

    @Test
    fun `ttml background vocals do not disturb the main line`() {
        val second = parseTtml(ttml)[1]
        assertEquals("Second line", second.text)
        assertEquals(2, second.words.size)
    }

    @Test
    fun `ttml nested spans and clock formats`() {
        val xml = """<tt><body><p begin="0:00:01.5" end="3s"><span begin="1.5s" end="2.5s"><span begin="1.5s" end="2s">A</span></span></p></body></tt>"""
        val lines = parseTtml(xml)
        assertEquals(1, lines.size)
        assertEquals(1_500L, lines[0].timeMs)
        assertEquals(3_000L, lines[0].endMs)
    }

    @Test
    fun `ttml without span timing is line level`() {
        val lines = parseTtml("""<tt><body><p begin="5.0" end="6.0">Just a line</p></body></tt>""")
        assertEquals(1, lines.size)
        assertTrue(lines[0].words.isEmpty())
        assertEquals("Just a line", lines[0].text)
    }

    @Test
    fun `malformed ttml gives no lines instead of crashing`() {
        assertTrue(parseTtml("<tt><body><p begin=").isEmpty())
        assertTrue(parseTtml("").isEmpty())
        assertTrue(parseTtml("""<tt><body><p begin="oops">x</p></body></tt>""").isEmpty())
    }

    @Test
    fun `clock parser rejects garbage`() {
        assertEquals(-1L, ttmlClockMs("abc"))
        assertEquals(-1L, ttmlClockMs(null))
        assertEquals(21_460L, ttmlClockMs("00:21.46"))
        assertEquals(500L, ttmlClockMs("500ms"))
    }

    @Test
    fun `standard lrc gives line timing only`() {
        val lines = parseLrc("[ar:Someone]\n[00:12.00]First\n[00:05.50]Zero\n[01:02.5]Last")
        assertEquals(listOf(5_500L, 12_000L, 62_500L), lines.map { it.timeMs })
        assertEquals("Zero", lines[0].text)
        assertTrue(lines.all { it.words.isEmpty() })
    }

    @Test
    fun `lrc line with several stamps repeats the text`() {
        val lines = parseLrc("[00:10.00][00:30.00]Chorus")
        assertEquals(listOf(10_000L, 30_000L), lines.map { it.timeMs })
        assertEquals("Chorus", lines[1].text)
    }

    @Test
    fun `enhanced lrc keeps word timing`() {
        val lines = parseLrc("[00:10.00]<00:10.00>Hello <00:10.50>big <00:11.00>world\n[00:13.00]<00:13.00>Next")
        assertEquals(2, lines.size)
        val words = lines[0].words
        assertEquals(3, words.size)
        assertEquals("Hello big world", lines[0].text)
        assertEquals(10_500L, words[1].startMs)
        assertEquals(10_500L, words[0].endMs) // ends where the next begins
        assertEquals(13_000L, words[2].endMs) // last word closes at the next line
        assertEquals(LyricsKind.WordSynced, kindOf(lines, null))
    }

    @Test
    fun `malformed lrc stamps are skipped not invented`() {
        val lines = parseLrc("[xx:yy]nope\n[00:1]short\nplain text\n[00:07.00]ok")
        assertEquals(1, lines.size)
        assertEquals(7_000L, lines[0].timeMs)
    }

    @Test
    fun `plain text has no timing and no kind of synced`() {
        assertTrue(parseLrc("Just words\nand more words").isEmpty())
        assertEquals(LyricsKind.Plain, kindOf(emptyList(), "Just words"))
        assertNull(kindOf(emptyList(), "  "))
    }

    @Test
    fun `current line search follows position and seeks backwards`() {
        val lines = parseLrc("[00:10.00]a\n[00:20.00]b\n[00:30.00]c")
        assertEquals(-1, currentLineIndex(lines, 5_000))
        assertEquals(0, currentLineIndex(lines, 10_000))
        assertEquals(2, currentLineIndex(lines, 45_000))
        assertEquals(1, currentLineIndex(lines, 25_000)) // after a seek back
    }

    @Test
    fun `word highlight is a pure function of the playback position`() {
        val w = LyricWord(1_000, 2_000, "x")
        assertEquals(0f, wordProgress(w, 500), 0.001f)   // before
        assertEquals(0.5f, wordProgress(w, 1_500), 0.001f) // playing
        assertEquals(0.5f, wordProgress(w, 1_500), 0.001f) // paused: same position, same result
        assertEquals(1f, wordProgress(w, 9_000), 0.001f) // after
        assertEquals(0.25f, wordProgress(w, 1_250), 0.001f) // seek back mid-word
        val line = LyricLine(0, "a b", listOf(LyricWord(0, 500, "a"), LyricWord(500, 900, "b")))
        assertEquals(-1, activeWordIndex(line.copy(words = listOf(LyricWord(100, 200, "a"))), 50))
        assertEquals(1, activeWordIndex(line, 600))
        assertEquals(0, activeWordIndex(line, 100)) // seek back
    }
}
