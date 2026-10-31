package com.dopavin.data

import kotlin.math.abs

/**
 * Duplicate and version handling for online song lists. Pure Kotlin and generic over the item type
 * (JVM-testable without Android classes). The video id stays the real identity of a song; this only
 * decides which entries of ONE list are the same recording shown twice, and which are other
 * versions that should rank below the original.
 */
object TrackMatch {
    /** Versions that are a different recording: they must never be merged with the original. */
    private val OTHER_VERSIONS = setOf(
        "remix", "instrumental", "karaoke", "cover", "mashup", "slowed", "reverb", "sped up", "spedup",
        "lofi", "lo-fi", "live", "acoustic", "unplugged",
    )
    private const val SAME_LENGTH_MS = 3_000L

    private val artistSplit = Regex("""(?i)\s*(?:,|&|/|;|\sand\s|\sx\s|\sfeat\.?\s|\sft\.?\s)\s*""")

    /** Version words of a title ("Song (Live)" -> live), lower-case. */
    fun versions(title: String): Set<String> = LyricsMatcher.versionTags(title)

    /** True when the title marks a remix / live / karaoke ... the query did not ask for. */
    fun isOtherVersion(title: String, query: String = ""): Boolean {
        val asked = versions(query)
        return versions(title).any { it in OTHER_VERSIONS && it !in asked }
    }

    private fun baseKey(title: String, artist: String): String =
        LyricsMatcher.normalise(title) + "|" +
            LyricsMatcher.normalise(artist.split(artistSplit).firstOrNull { it.isNotBlank() }.orEmpty())

    /**
     * Drops entries that repeat an earlier one: same title and main artist, same version words and
     * (when both lengths are known) lengths within 3 s. A remix, live cut or a different-length
     * edit stays. The first (best ranked) entry of each group is kept, order is preserved.
     */
    fun <T> dedupe(
        items: List<T>,
        title: (T) -> String,
        artist: (T) -> String,
        durationMs: (T) -> Long,
    ): List<T> {
        val out = ArrayList<T>(items.size)
        val seen = HashMap<String, MutableList<Pair<Set<String>, Long>>>()
        for (item in items) {
            val key = baseKey(title(item), artist(item))
            val v = versions(title(item))
            val d = durationMs(item)
            val group = seen.getOrPut(key) { ArrayList() }
            val duplicate = key.length > 1 && group.any { (gv, gd) ->
                gv == v && (d <= 0 || gd <= 0 || abs(d - gd) <= SAME_LENGTH_MS)
            }
            if (duplicate) continue
            group += v to d
            out += item
        }
        return out
    }

    /**
     * Stable ranking: originals before other versions (unless the query asked for that version),
     * and within each, official-looking uploaders first ([tier] 0 before 1). Nothing is removed.
     */
    fun <T> rank(items: List<T>, query: String, title: (T) -> String, tier: (T) -> Int): List<T> =
        items.sortedWith(compareBy({ if (isOtherVersion(title(it), query)) 1 else 0 }, { tier(it) }))

    /** Channel id of a youtube channel url ("…/channel/UCxxxx" -> "UCxxxx"), else the url itself. */
    fun channelKey(url: String?): String? {
        val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val id = u.substringAfter("/channel/", "").substringBefore('/').substringBefore('?')
        return id.ifEmpty { u.trimEnd('/').lowercase() }
    }

    fun sameChannel(a: String?, b: String?): Boolean {
        val x = channelKey(a) ?: return false
        val y = channelKey(b) ?: return false
        return x == y
    }
}
