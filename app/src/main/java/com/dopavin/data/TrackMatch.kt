package com.dopavin.data
import kotlin.math.abs
object TrackMatch {
    private val OTHER_VERSIONS = setOf(
        "remix", "instrumental", "karaoke", "cover", "mashup", "slowed", "reverb", "sped up", "spedup",
        "lofi", "lo-fi", "live", "acoustic", "unplugged",
    )
    private const val SAME_LENGTH_MS = 3_000L
    private val artistSplit = Regex("""(?i)\s*(?:,|&|/|;|\sand\s|\sx\s|\sfeat\.?\s|\sft\.?\s)\s*""")
    fun versions(title: String): Set<String> = LyricsMatcher.versionTags(title)
    fun isOtherVersion(title: String, query: String = ""): Boolean {
        val asked = versions(query)
        return versions(title).any { it in OTHER_VERSIONS && it !in asked }
    }
    private fun baseKey(title: String, artist: String): String =
        LyricsMatcher.normalise(title) + "|" +
            LyricsMatcher.normalise(artist.split(artistSplit).firstOrNull { it.isNotBlank() }.orEmpty())
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
    fun <T> rank(items: List<T>, query: String, title: (T) -> String, tier: (T) -> Int): List<T> =
        items.sortedWith(compareBy({ if (isOtherVersion(title(it), query)) 1 else 0 }, { tier(it) }))
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
