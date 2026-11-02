package com.dopavin.data
object SmartQueue {
    class Cand<T>(
        val item: T,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val relatedRank: Int? = null,
        val earlierRank: Int? = null,
        val newestRank: Int? = null,
        val sameArtist: Boolean = false,
        val moodMatch: Double? = null,
        val taste: Double? = null,
        val favouriteArtist: Boolean = false,
        val rediscovery: Double? = null,
        val heardRecently: Boolean = false,
        val blocked: Boolean = false,
    )
    class Ranked<T>(val item: T, val reason: ReasonCode, val confidence: Double, val score: Double)
    const val MIN_MS = 60_000L
    const val MAX_MS = 10 * 60_000L
    private const val HEARD_FACTOR = 0.4
    private val OTHER = setOf("remix", "instrumental", "karaoke", "cover", "mashup", "slowed", "reverb", "sped up", "spedup", "lofi", "lo-fi", "live", "acoustic", "unplugged")
    fun <T> pick(
        pool: List<Cand<T>>,
        queued: List<Triple<String, String, Long>>,
        seedTitle: String,
        size: Int,
        maxArtistRun: Int = 2,
    ): List<T> = rank(pool, queued, seedTitle, size, maxArtistRun).map { it.item }
    fun <T> rank(
        pool: List<Cand<T>>,
        queued: List<Triple<String, String, Long>>,
        seedTitle: String,
        size: Int,
        maxArtistRun: Int = 2,
    ): List<Ranked<T>> {
        if (size <= 0) return emptyList()
        val seedIsVersion = TrackMatch.versions(seedTitle).any { it in OTHER }
        val keys = ArrayList<Triple<String, String, Long>>(queued)
        val scored = ArrayList<Ranked<T>>()
        val byCand = HashMap<Ranked<T>, Cand<T>>()
        for (c in pool) {
            if (c.blocked) continue
            if (c.durationMs in 1 until MIN_MS || c.durationMs > MAX_MS) continue
            if (!seedIsVersion && TrackMatch.isOtherVersion(c.title)) continue
            val s = score(featuresOf(c))
            val value = if (c.heardRecently) s.value * HEARD_FACTOR else s.value
            val r = Ranked(c.item, s.reason, s.confidence, value)
            scored += r
            byCand[r] = c
        }
        val ordered = scored.sortedByDescending { it.score }
        val out = ArrayList<Ranked<T>>(size)
        val artistRun = HashMap<String, Int>()
        for (r in ordered) {
            if (out.size >= size) break
            val c = byCand.getValue(r)
            if (keys.any { sameRecording(it, c) }) continue
            val a = norm(c.artist)
            val run = artistRun[a] ?: 0
            if (maxArtistRun > 0 && run >= maxArtistRun) continue
            artistRun[a] = run + 1
            keys += Triple(c.title, c.artist, c.durationMs)
            out += r
        }
        return out
    }
    internal fun <T> featuresOf(c: Cand<T>): RecFeatures {
        var relevance: Double? = null
        fun up(v: Double) { relevance = maxOf(relevance ?: 0.0, v) }
        c.relatedRank?.let { up(1.0 / (1.0 + it * 0.12)) }
        c.newestRank?.let { up(0.8 / (1.0 + it * 0.35)) }
        c.earlierRank?.let { up(0.7 / (1.0 + it * 0.15)) }
        return RecFeatures(
            moodMatch = c.moodMatch,
            taste = c.taste,
            artistAffinity = if (c.favouriteArtist) 0.9 else null,
            session = if (c.sameArtist) 0.9 else null,
            novelty = c.rediscovery,
            relevance = relevance,
        )
    }
    private fun norm(s: String) = LyricsMatcher.normalise(s)
    private fun <T> sameRecording(q: Triple<String, String, Long>, c: Cand<T>): Boolean {
        if (norm(q.first) != norm(c.title) || norm(q.first).isEmpty()) return false
        val a = norm(q.second)
        val b = norm(c.artist)
        val artistsAgree = a.isEmpty() || b.isEmpty() || a == b || a.contains(b) || b.contains(a)
        if (!artistsAgree) return false
        if (TrackMatch.versions(q.first) != TrackMatch.versions(c.title)) return false
        return q.third <= 0 || c.durationMs <= 0 || kotlin.math.abs(q.third - c.durationMs) <= 4_000L
    }
}
