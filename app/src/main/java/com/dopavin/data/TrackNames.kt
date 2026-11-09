package com.dopavin.data

/**
 * Cleans YouTube titles and filters channel uploads that are not songs. Pure Kotlin (JVM-testable).
 * Both are heuristics and deliberately conservative: a title is only shortened by well-known upload
 * noise ("(Official Music Video)", "| T-Series", "Artist - " prefix ...), and anything unclear is
 * kept as it was. Meaningful tags such as remix, live, acoustic, ft. are never removed.
 */
object TrackNames {
    private val brackets = Regex("""[(\[{【（][^)\]}】）]*[)\]}】）]""")
    private val junkInBracket = Regex(
        """(?i)\b(official|video|audio|lyrics?|lyrical|visuali[sz]er|hd|hq|4k|8k|uhd|mv|m/v|teaser|promo|out now|full song|full video|latest|new\s+\w*\s*song|\d{4}\s+songs?|song\s+\d{4}|prod\.?|music)\b""",
    )
    private val keepInBracket = Regex(
        """(?i)\b(remix|live|acoustic|cover|remaster(ed)?|version|slowed|reverb|sped|instrumental|unplugged|mashup|reprise|demo|extended|radio edit|feat\.?|ft\.?|from|theme)\b""",
    )
    // "... Official Video", "... Lyrical Video", "... Full Song" (needs a qualifier), or a bare
    // "- Video" / "| Lyrics" after a separator. A title that merely ends in "song" is left alone.
    private val trailingJunk = Regex(
        """(?i)\s*[-–—:|]?\s*(?:official|music|lyric(?:al)?|full)\s+(?:(?:official|music|lyric(?:al)?|full|video|audio)\s+)*(?:video|audio|lyrics?|visuali[sz]er|song)\s*$|\s*[-–—:|]\s*(?:video|audio|lyrics?|visuali[sz]er)\s*$""",
    )
    private val hashtags = Regex("""\s+#\S+""")
    private val spaces = Regex("""\s+""")
    private val separators = Regex("""\s+[|｜]\s+""")
    private val prefixSep = Regex("""^\s*[-–—:|]\s*""")
    private val edgeJunk = Regex("""^[\s\-–—:|,.]+|[\s\-–—:|,.]+$""")

    fun clean(rawTitle: String, artist: String = ""): String {
        val original = rawTitle.trim()
        if (original.isEmpty()) return original
        var t = original

        // 1. Junk-only bracket groups out ("(Official Video)", "[4K]", "【MV】"); meaningful ones stay.
        t = brackets.replace(t) { m ->
            val inner = m.value.substring(1, m.value.length - 1)
            if (junkInBracket.containsMatchIn(inner) && !keepInBracket.containsMatchIn(inner)) "" else m.value
        }
        // 2. "Title | Artist | Label" -> the first part.
        t = t.split(separators).firstOrNull { it.isNotBlank() } ?: t
        // 3. Hashtags and trailing "Official Video" style words.
        t = hashtags.replace(t, "")
        t = trailingJunk.replace(t, "")
        t = spaces.replace(t, " ").trim()

        // 4. "Artist - Title" and "Title - Artist" when the artist is the uploader.
        val name = artist.replace(Regex("""(?i)\s*-\s*topic$|\s*vevo$|\s*official$"""), "").trim()
        if (name.length >= 2) {
            val n = LyricsMatcher.normalise(name)
            for (sep in listOf(" - ", " – ", " — ", ": ")) {
                val i = t.indexOf(sep)
                if (i > 0) {
                    val head = t.substring(0, i)
                    val tail = t.substring(i + sep.length)
                    if (LyricsMatcher.normalise(head) == n && tail.isNotBlank()) { t = tail; break }
                    if (LyricsMatcher.normalise(tail) == n && head.isNotBlank()) { t = head; break }
                }
            }
        }
        t = prefixSep.replace(t, "")
        t = edgeJunk.replace(t, "").trim()
        // Never return something emptier than useful: fall back to the original.
        return if (t.length < 2) original else t
    }

    private val notMusic = Regex(
        """(?i)\b(interview|podcast|vlog|behind the scenes|making of|the making|\bbts\b|reaction|reacts?|trailer|teaser|promo|live ?stream|livestream|press conference|documentary|episode|q ?& ?a|unboxing|announcement|first look|poster|full movie|full album|jukebox|non-?stop|best of|top \d+|award(s)?|speech|shorts?|motion poster|story|status video)\b""",
    )

    /** Longest upload still treated as one song (longer ones are mixes, streams, films). */
    const val MAX_SONG_SECONDS = 15 * 60

    /** False for uploads that are clearly not a song: interviews, vlogs, trailers, compilations, very long videos. */
    fun looksLikeSong(rawTitle: String, durationSec: Long): Boolean {
        if (durationSec > MAX_SONG_SECONDS) return false
        return !notMusic.containsMatchIn(rawTitle)
    }
}
