package com.dopavin.data

import kotlin.math.log10

/**
 * Picks the genuine artist channel out of channel-search hits and ranks songs by how official
 * their uploader is. Pure Kotlin (JVM-testable). Heuristics only: a channel must match the name
 * first; verified and big channels then win over small re-upload channels.
 */
object ArtistPick {

    /** One channel found by a channel search. */
    data class Hit(
        val name: String,
        val url: String,
        val avatar: String?,
        val subscribers: Long = -1L,
        val verified: Boolean = false,
    )

    private val topicSuffix = Regex("""(?i)\s*-\s*topic$|\s*vevo$|\s*official$|\s*music$""")

    private fun norm(s: String) = LyricsMatcher.normalise(topicSuffix.replace(s.trim(), ""))

    /** 3 exact, 2 starts-with, 1 contained, 0 no match. */
    fun nameMatch(wanted: String, channel: String): Int {
        val w = norm(wanted)
        val c = norm(channel)
        if (w.isEmpty() || c.isEmpty()) return 0
        return when {
            w == c -> 3
            c.startsWith(w) || w.startsWith(c) -> 2
            c.contains(w) || w.contains(c) -> 1
            else -> 0
        }
    }

    private fun score(wanted: String, hit: Hit): Double {
        val m = nameMatch(wanted, hit.name)
        if (m == 0) return -1.0
        val subs = if (hit.subscribers > 0) log10(hit.subscribers.toDouble()) else 0.0
        return m * 10.0 + (if (hit.verified) 8.0 else 0.0) + subs
    }

    /** The genuine channel for [artist], or null when no hit even carries the name. */
    fun best(artist: String, hits: List<Hit>): Hit? =
        hits.map { it to score(artist, it) }.filter { it.second >= 0 }.maxByOrNull { it.second }?.first

    /** Hits worth showing for a search [query]: name-related first, then the rest by size; verified up. */
    fun rank(query: String, hits: List<Hit>): List<Hit> =
        hits.sortedByDescending { score(query, it).let { s -> if (s < 0) -0.5 + (if (it.verified) 0.4 else 0.0) else s } }

    /** 0 = official-looking uploader (verified, Topic, VEVO, Official), 1 = anyone else. */
    fun uploaderTier(uploader: String, verified: Boolean): Int {
        if (verified) return 0
        val u = uploader.trim().lowercase()
        return if (u.endsWith("- topic") || u.endsWith("vevo") || u.endsWith("official")) 0 else 1
    }

    /** Stable sort: official uploaders first, relative order otherwise kept. */
    fun <T> demoteUnofficial(items: List<T>, tier: (T) -> Int): List<T> = items.sortedBy(tier)
}
