package com.dopavin.data

import java.io.StringReader

/** One word (or syllable) of a word-synced line. [spaceAfter] is false inside a split word. */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String, val spaceAfter: Boolean = true)

/**
 * One timed line of synced lyrics. [words] is filled when the provider has word timing (karaoke
 * display); [endMs] is the end of the line when known, else -1.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val endMs: Long = -1L,
)

enum class LyricsStatus { Idle, Loading, Found, NotFound, Failed, Disabled, Instrumental }

/** What the lyrics sheet shows. [songId] guards against showing the previous song's lyrics. */
data class LyricsUi(
    val songId: Long = -1L,
    val status: LyricsStatus = LyricsStatus.Idle,
    val synced: List<LyricLine> = emptyList(),
    val plain: String? = null,
    /** Friendly cause when [status] is Failed, from strings.xml. */
    val error: String? = null,
    /** Raw technical detail of the same failure ("HTTP 503"), so problems can be reported. */
    val errorDetail: String? = null,
    /** Provider id that supplied these lyrics (see [LyricsProviders]), and the timing it carries. */
    val source: String? = null,
    val kind: LyricsKind? = null,
    /** Per-song sync offset in ms: positive shows lines earlier. */
    val offsetMs: Long = 0L,
    /** Provider choice that produced this result: "choose" (race) or a provider id. */
    val selection: String = LyricsProviders.CHOOSE,
)

private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val WORD_TAG = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

private fun clockMs(min: String, sec: String, frac: String): Long {
    val ms = when (frac.length) {
        0 -> 0L
        1 -> frac.toLong() * 100
        2 -> frac.toLong() * 10
        else -> frac.take(3).toLong()
    }
    return min.toLong() * 60_000L + sec.toLong() * 1000L + ms
}

/**
 * Parses LRC text. A line may carry several timestamps; metadata tags like [ar:...] are skipped.
 * Enhanced LRC (`[00:12.00] <00:12.00> word <00:12.40> word`) keeps its word timing: the line then
 * has [LyricLine.words], each word ending where the next begins (the last one at the next line).
 * Standard LRC gives line timing only. Malformed stamps are ignored, never fabricated.
 */
fun parseLrc(text: String): List<LyricLine> {
    val lines = ArrayList<LyricLine>()
    for (raw in text.lineSequence()) {
        val stamps = TIMESTAMP.findAll(raw).toList()
        if (stamps.isEmpty()) continue
        val content = raw.substring(stamps.last().range.last + 1).trim()
        val tags = WORD_TAG.findAll(content).toList()
        for (m in stamps) {
            val start = clockMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            if (tags.isEmpty()) {
                lines += LyricLine(start, content)
            } else {
                val words = ArrayList<LyricWord>()
                tags.forEachIndexed { i, tag ->
                    val from = tag.range.last + 1
                    val to = if (i + 1 < tags.size) tags[i + 1].range.first else content.length
                    val chunk = content.substring(from, to)
                    val word = chunk.trim()
                    if (word.isEmpty()) return@forEachIndexed
                    val t0 = clockMs(tag.groupValues[1], tag.groupValues[2], tag.groupValues[3])
                    val t1 = if (i + 1 < tags.size) {
                        clockMs(tags[i + 1].groupValues[1], tags[i + 1].groupValues[2], tags[i + 1].groupValues[3])
                    } else {
                        -1L
                    }
                    words += LyricWord(t0, if (t1 >= t0) t1 else t0, word, chunk.last().isWhitespace())
                }
                val text2 = content.replace(WORD_TAG, "").replace(Regex("\\s+"), " ").trim()
                lines += LyricLine(start, text2, words)
            }
        }
    }
    val sorted = lines.sortedBy { it.timeMs }
    // Close open word ends and line ends with the next line's start.
    return sorted.mapIndexed { i, line ->
        val next = sorted.getOrNull(i + 1)?.timeMs ?: -1L
        if (line.words.isEmpty()) {
            line
        } else {
            val last = line.words.last()
            val fixedLast = if (last.endMs <= last.startMs && next > last.startMs) last.copy(endMs = next) else last
            val ws = line.words.dropLast(1) + fixedLast
            line.copy(words = ws, endMs = fixedLast.endMs)
        }
    }
}

/** What kind of timing a lyrics text carries. */
enum class LyricsKind { WordSynced, LineSynced, Plain }

fun kindOf(lines: List<LyricLine>, plain: String?): LyricsKind? = when {
    lines.any { it.words.isNotEmpty() } -> LyricsKind.WordSynced
    lines.isNotEmpty() -> LyricsKind.LineSynced
    !plain.isNullOrBlank() -> LyricsKind.Plain
    else -> null
}

/**
 * Parses the Better Lyrics provider's TTML (owner F-89, word timing F-131; SAX since F-141 so it also
 * runs in JVM unit tests). Each `<p begin end>` is a line; `<span begin end>` inside it are words or
 * syllables with their own timing, nested spans included. Whitespace between spans marks a word
 * boundary (no whitespace = the next span continues the same word). Background-vocal spans
 * (`role="x-bg"`) are skipped so they cannot disturb the main line. Broken XML gives an empty list.
 */
fun parseTtml(xml: String): List<LyricLine> = try {
    val factory = javax.xml.parsers.SAXParserFactory.newInstance()
    val handler = TtmlHandler()
    factory.newSAXParser().parse(org.xml.sax.InputSource(StringReader(xml)), handler)
    handler.lines.sortedBy { it.timeMs }
} catch (e: Exception) {
    emptyList()
}

private class TtmlHandler : org.xml.sax.helpers.DefaultHandler() {
    val lines = ArrayList<LyricLine>()
    private var inParagraph = false
    private var beginMs = -1L
    private var endMs = -1L
    private var plain = StringBuilder()
    private var words = ArrayList<LyricWord>()
    private val spans = ArrayList<LongArray?>()
    private var spanText = StringBuilder()
    private var skipDepth = 0

    private fun local(qName: String) = qName.substringAfterLast(':')

    override fun startElement(uri: String?, localName: String?, qName: String, a: org.xml.sax.Attributes) {
        val name = local(qName)
        if (name.equals("p", ignoreCase = true)) {
            inParagraph = true
            beginMs = ttmlClockMs(a.getValue("begin"))
            endMs = ttmlClockMs(a.getValue("end"))
            plain = StringBuilder()
            words = ArrayList()
            spans.clear()
            skipDepth = 0
        } else if (inParagraph && name.equals("span", ignoreCase = true)) {
            var background = false
            for (i in 0 until a.length) {
                if (local(a.getQName(i)) == "role" && a.getValue(i) == "x-bg") background = true
            }
            if (background || skipDepth > 0) {
                skipDepth++
                spans.add(null)
            } else {
                val start = ttmlClockMs(a.getValue("begin"))
                val end = ttmlClockMs(a.getValue("end"))
                spans.add(if (start >= 0) longArrayOf(start, end) else null)
            }
            spanText = StringBuilder()
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (!inParagraph || skipDepth > 0) return
        val text = String(ch, start, length)
        val timed = spans.lastOrNull()
        if (timed != null) {
            spanText.append(text)
        } else if (text.isNotEmpty() && text.isBlank() && words.isNotEmpty()) {
            val last = words.last()
            words[words.lastIndex] = last.copy(spaceAfter = true)
        } else if (spans.isEmpty()) {
            plain.append(text)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        val name = local(qName)
        if (inParagraph && name.equals("span", ignoreCase = true) && spans.isNotEmpty()) {
            val timing = spans.removeAt(spans.lastIndex)
            if (timing == null) {
                if (skipDepth > 0) skipDepth--
            } else {
                val word = spanText.toString().replace(WS, " ")
                if (word.isNotBlank()) {
                    val trailing = word.last() == ' '
                    val end = if (timing[1] >= timing[0]) timing[1] else timing[0]
                    words += LyricWord(timing[0], end, word.trim(), trailing)
                }
            }
            spanText = StringBuilder()
        } else if (name.equals("p", ignoreCase = true) && inParagraph) {
            inParagraph = false
            if (beginMs >= 0) {
                if (words.isNotEmpty()) {
                    words[words.lastIndex] = words.last().copy(spaceAfter = false)
                    val text = buildString {
                        words.forEachIndexed { i, w ->
                            append(w.text)
                            if (w.spaceAfter && i < words.lastIndex) append(' ')
                        }
                    }
                    if (text.isNotBlank()) lines += LyricLine(beginMs, text, words.toList(), endMs)
                } else {
                    val text = plain.toString().replace(WS, " ").trim()
                    if (text.isNotBlank()) lines += LyricLine(beginMs, text, emptyList(), endMs)
                }
            }
        }
    }

    private companion object {
        val WS = Regex("\\s+")
    }
}

/** TTML clock value ("00:21.46", "0:00:21.46", "21.46", "21460ms", "21.4s") to ms; -1 when absent or unreadable. */
internal fun ttmlClockMs(raw: String?): Long {
    if (raw.isNullOrBlank()) return -1
    val v = raw.trim()
    return try {
        if (v.endsWith("ms")) return v.removeSuffix("ms").toDouble().toLong().takeIf { it >= 0 } ?: -1
        val parts = v.removeSuffix("s").split(':')
        val ms = when (parts.size) {
            1 -> (parts[0].toDouble() * 1000).toLong()
            2 -> (parts[0].toDouble() * 60_000).toLong() + (parts[1].toDouble() * 1000).toLong()
            3 -> parts[0].toLong() * 3_600_000 + parts[1].toLong() * 60_000 + (parts[2].toDouble() * 1000).toLong()
            else -> -1
        }
        if (ms < 0) -1 else ms
    } catch (e: NumberFormatException) {
        -1
    }
}

/** How far a word has been sung at [positionMs]: 0 before it starts, 1 once it ended. A pure function of the playback clock, so seek, pause and speed changes need no special cases. */
fun wordProgress(word: LyricWord, positionMs: Long): Float {
    val span = (word.endMs - word.startMs).coerceAtLeast(WORD_MIN_SPAN_MS)
    return ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
}

/** Index of the word being sung in [line] at [positionMs] (the last started one), or -1 before the line. */
fun activeWordIndex(line: LyricLine, positionMs: Long): Int {
    var found = -1
    line.words.forEachIndexed { i, w -> if (w.startMs <= positionMs) found = i }
    return found
}

private const val WORD_MIN_SPAN_MS = 120L

/** Index of the line being sung at [positionMs], or -1 before the first line. */
fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}
