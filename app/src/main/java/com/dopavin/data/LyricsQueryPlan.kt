package com.dopavin.data

/**
 * Builds the ordered list of lyrics searches for one song. Pure Kotlin (JVM-testable) and generic:
 * nothing here knows any song or artist. The song's own metadata is never changed; every query is a
 * new [LyricsQuery]. Order (duplicates dropped, at most [MAX_QUERIES]):
 *  1. the title exactly as the song carries it + artist
 *  2. cleaned title + artist
 *  3. cleaned title only (strict: a hit must match the title closely, see [LyricsFallback])
 *  4. cleaned title + album / movie (the album, else a bracket group of the title such as "(Aashiqui 2)")
 *  5. other artist / title combinations: first of several artists, "Artist - Title" read both ways
 *  6. the rest: title with its version tag kept, movie without artist
 */
object LyricsQueryPlan {
    const val MAX_QUERIES = 7

    data class Meta(
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Int,
        val videoId: String?,
    )

    /** What could be read out of a raw title. */
    data class Parsed(
        val clean: String,
        /** Bracket groups that are neither upload noise nor a version: usually the movie / album. */
        val extras: List<String>,
        val featured: List<String>,
        val versions: List<String>,
    )

    private val bracket = Regex("""[(\[{【（]([^)\]}】）]*)[)\]}】）]""")
    private val featWord = Regex("""(?i)^\s*(?:feat\.?|ft\.?|featuring|with)\s+""")
    private val featTail = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring)\s+(.+)$""")
    private val fromWord = Regex("""(?i)^\s*(?:from|film|movie|ost|soundtrack|album)\s*[:\-]?\s*""")
    private val versionWord = Regex(
        """(?i)\b(remix|live|acoustic|instrumental|karaoke|cover|unplugged|mashup|reprise|slowed|reverb|sped\s*up|lofi|remaster(?:ed)?|demo|version|edit)\b""",
    )
    private val looseJunk = Regex(
        """(?i)(?:^|\s)(?:-\s*)?(?:official\s+(?:music\s+)?(?:video|audio|lyric(?:s)?\s+video|visuali[sz]er)|lyrics?\s+video|lyrical\s+video|(?:full|video|audio)\s+song|(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|4k|8k|uhd|hd|hq)(?=\s|$)""",
    )
    private val leadingNumber = Regex("""^\d{1,3}\s*[._)\-]\s+""")
    private val spaces = Regex("""\s+""")
    private val edge = Regex("""^[\s\-–—:|,.]+|[\s\-–—:|,.]+$""")
    private val artistSplit = Regex("""(?i)\s*(?:,|&|/|;|\sand\s|\sx\s|\sfeat\.?\s|\sft\.?\s|\swith\s)\s*""")
    private val labelSuffix = Regex("""(?i)\s*-\s*topic$|\s*vevo$|\s+official$""")

    fun cleanArtist(artist: String): String = labelSuffix.replace(artist.trim(), "").trim()

    private fun firstArtist(artist: String): String = artist.split(artistSplit).firstOrNull { it.isNotBlank() }.orEmpty().trim()

    private fun key(s: String) = LyricsMatcher.normalise(s)

    /** Identity of a query text: same letters in the same order, ignoring case and spacing only. */
    private fun same(s: String) = spaces.replace(s.trim().lowercase(), " ")

    /** Uploaders that are a label or channel rather than a person ("T-Series", "Brown Town Music"). */
    private val labelWords = Regex(
        """(?i)\b(music|records?|recordings?|entertainment|films?|studios?|series|labels?|productions?|network|channel|tv|media|originals|official|vevo|topic|company)\b""",
    )

    fun looksLikeLabel(name: String): Boolean = labelWords.containsMatchIn(name)

    fun parse(rawTitle: String, artist: String = ""): Parsed {
        val extras = ArrayList<String>()
        val featured = ArrayList<String>()
        val versions = ArrayList<String>()
        val who = cleanArtist(artist)
        // Upload noise, "Artist - " prefix, "| Label" tails out first; meaningful brackets stay for the next step.
        var t = TrackNames.clean(rawTitle.trim(), artist)
        t = bracket.replace(t) { m ->
            val inner = m.groupValues[1].trim()
            when {
                inner.isEmpty() -> ""
                featWord.containsMatchIn(inner) -> { featured += featWord.replace(inner, "").trim(); "" }
                versionWord.containsMatchIn(inner) -> { versions += inner; "" }
                else -> {
                    val movie = fromWord.replace(inner, "").trim().trim('"', '\'', '“', '”', '‘', '’').trim()
                    if (movie.length in 2..48 && movie.any { it.isLetterOrDigit() } && key(movie) != key(who)) extras += movie
                    ""
                }
            }
        }
        featTail.find(t)?.let { featured += it.groupValues[1].trim(); t = featTail.replace(t, "") }
        t = looseJunk.replace(t, " ")
        // "Song Name Artist Name" where the artist is the uploader: cut the artist off an edge, never the middle.
        for (name in listOf(who, firstArtist(who)).filter { it.length >= 3 }.distinct()) {
            val esc = Regex.escape(name)
            val trimmed = t.replace(Regex("""(?i)(?:^|\s)$esc\s*$"""), " ").replace(Regex("""(?i)^\s*$esc(?=\s)"""), " ")
            if (trimmed.isNotBlank() && trimmed.trim().length >= 2 && key(trimmed).isNotEmpty()) { t = trimmed; break }
        }
        t = leadingNumber.replace(t, "")
        t = edge.replace(spaces.replace(t, " ").trim(), "").trim()
        return Parsed(t, extras.distinct(), featured, versions)
    }

    fun build(meta: Meta): List<LyricsQuery> {
        val raw = meta.title.trim()
        if (raw.isBlank()) return emptyList()
        val artist = meta.artist.trim()
        val who = cleanArtist(artist)
        val p = parse(raw, artist)
        val clean = p.clean.ifBlank { raw }
        val hasArtist = who.isNotBlank()
        val out = LinkedHashMap<String, LyricsQuery>()

        fun add(title: String, queryArtist: String, album: String, strict: Boolean = false) {
            val t = title.trim()
            if (t.isEmpty() || key(t).isEmpty() || out.size >= MAX_QUERIES) return
            // The real text, not the normalised one: normalising drops brackets, so "Song (Official Video)" and
            // the cleaned "Song" used to count as ONE query and the cleaned search was never sent.
            val k = same(t) + "|" + same(queryArtist) + "|" + same(album)
            if (k in out) return
            out[k] = LyricsQuery(t, queryArtist.trim(), album.trim(), meta.durationSec, meta.videoId, raw, strict)
        }

        add(raw, artist, meta.album)                                  // 1 exact
        add(clean, who, meta.album)                                   // 2 cleaned + artist
        add(clean, "", "", strict = hasArtist)                        // 3 cleaned only
        val movie = meta.album.takeIf { it.isNotBlank() } ?: p.extras.firstOrNull().orEmpty()
        if (movie.isNotBlank()) {                                     // 4 title + album / movie
            add(clean, who, movie)
            add(clean, "", movie, strict = hasArtist)
        }
        val first = firstArtist(who)                                  // 5 alternatives
        if (first.isNotBlank() && key(first) != key(who)) add(clean, first, "")
        val dash = Regex("""\s[-–—:]\s""")
        val parts = clean.split(dash).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size == 2) {
            add(parts[1], parts[0], "")                               // "Artist - Title"
            add(parts[0], parts[1], "")                               // "Title - Artist"
            add(parts[1], who, "")
            add(parts[0], who, "")
        }
        if (p.versions.isNotEmpty()) add("$clean ${p.versions.first()}", who, "") // 6 the rest
        if (p.extras.isNotEmpty() && p.extras.first() != movie) add(clean, who, p.extras.first())
        if (hasArtist && looksLikeLabel(who)) {                       // 7 uploader is a label: the singer may end the title
            val words = clean.split(' ').filter { it.isNotBlank() }
            for (m in listOf(2, 1)) {
                if (words.size - m < 2) continue
                val tail = words.takeLast(m).joinToString(" ")
                if (tail.split(' ').none { it.length >= 2 }) continue
                add(words.dropLast(m).joinToString(" "), tail, "")                    // "Song Name" + "Singer"
            }
            for (m in listOf(2, 1)) {
                if (words.size - m < 2) continue
                add(words.dropLast(m).joinToString(" "), "", "", strict = true)       // "Song Name" alone, strict
            }
        }
        return out.values.toList()
    }
}
