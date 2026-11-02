package com.dopavin.data
import kotlin.math.log10
object ArtistPick {
    data class Hit(
        val name: String,
        val url: String,
        val avatar: String?,
        val subscribers: Long = -1L,
        val verified: Boolean = false,
    )
    private val topicSuffix = Regex("""(?i)\s*-\s*topic$|\s*vevo$|\s*official$|\s*music$""")
    private fun norm(s: String) = LyricsMatcher.normalise(topicSuffix.replace(s.trim(), ""))
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
    fun best(artist: String, hits: List<Hit>): Hit? =
        hits.map { it to score(artist, it) }.filter { it.second >= 0 }.maxByOrNull { it.second }?.first
    fun rank(query: String, hits: List<Hit>): List<Hit> =
        hits.sortedByDescending { score(query, it).let { s -> if (s < 0) -0.5 + (if (it.verified) 0.4 else 0.0) else s } }
    fun uploaderTier(uploader: String, verified: Boolean): Int {
        if (verified) return 0
        val u = uploader.trim().lowercase()
        return if (u.endsWith("- topic") || u.endsWith("vevo") || u.endsWith("official")) 0 else 1
    }
    fun <T> demoteUnofficial(items: List<T>, tier: (T) -> Int): List<T> = items.sortedBy(tier)
}
