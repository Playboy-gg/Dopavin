package com.dopavin.data
import kotlin.math.max
import kotlin.math.min
object Karaoke {
    private const val MAX_SUNG_MS = 9_000L
    private const val MS_PER_CHAR = 85L
    private const val MIN_SUNG_MS = 600L
    private const val DEFAULT_LINE_MS = 3_000L
    fun words(line: LyricLine, nextLineMs: Long?): List<LyricWord> {
        if (line.words.isNotEmpty()) return line.words
        val tokens = line.text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val end = when {
            line.endMs > line.timeMs -> line.endMs
            nextLineMs != null && nextLineMs > line.timeMs -> nextLineMs
            else -> line.timeMs + DEFAULT_LINE_MS
        }
        val span = end - line.timeMs
        val unitCounts = tokens.map { units(it).size.coerceAtLeast(1) }
        val chars = unitCounts.sum()
        val estimate = (chars * MS_PER_CHAR).coerceIn(MIN_SUNG_MS, MAX_SUNG_MS)
        val sung = min(span, max(estimate, (span * 0.85).toLong()).coerceAtMost(MAX_SUNG_MS))
        val weights = unitCounts.map { it + 1 }
        val total = weights.sum().toDouble()
        var cursor = line.timeMs.toDouble()
        return tokens.mapIndexed { i, t ->
            val start = cursor
            cursor += sung * (weights[i] / total)
            LyricWord(start.toLong(), if (i == tokens.lastIndex) line.timeMs + sung else cursor.toLong(), t, i < tokens.lastIndex)
        }
    }
    private val VIRAMAS = intArrayOf(
        0x094D, 0x09CD, 0x0A4D, 0x0ACD, 0x0B4D, 0x0BCD, 0x0C4D, 0x0CCD, 0x0D4D, 0x0DCA, 0x0E3A, 0x1039, 0x17D2,
    )
    private fun isCursive(cp: Int): Boolean =
        cp in 0x0600..0x06FF || cp in 0x0700..0x074F || cp in 0x0750..0x077F || cp in 0x07C0..0x07FF ||
            cp in 0x08A0..0x08FF || cp in 0x1800..0x18AF || cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF
    private fun isJoiner(cp: Int): Boolean = cp == 0x200D || cp == 0x200C || cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF
    private fun isMark(cp: Int): Boolean {
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
    }
    fun units(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        var first = 0
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            if (isCursive(cp)) return listOf(word)
            i += Character.charCount(cp)
            first++
        }
        val out = ArrayList<String>(first)
        val cur = StringBuilder()
        var afterVirama = false
        i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            val attach = cur.isNotEmpty() && (isMark(cp) || isJoiner(cp) || afterVirama)
            if (!attach && cur.isNotEmpty()) {
                out += cur.toString()
                cur.setLength(0)
            }
            cur.appendCodePoint(cp)
            afterVirama = cp in VIRAMAS || (afterVirama && isJoiner(cp))
            i += Character.charCount(cp)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
    fun charProgress(progress: Float, index: Int, count: Int): Float {
        if (count <= 1) return smooth(progress)
        val x = progress * (count + OVERLAP) - index * 1f
        return smooth((x / (1f + OVERLAP)).coerceIn(0f, 1f))
    }
    private const val OVERLAP = 1.5f
    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)
}
