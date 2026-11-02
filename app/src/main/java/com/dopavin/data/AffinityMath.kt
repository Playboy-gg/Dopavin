package com.dopavin.data
import kotlin.math.pow
object AffinityMath {
    const val HALF_LIFE_DAYS = 45.0
    private const val DAY_MS = 86_400_000.0
    fun decay(ageMs: Long, halfLifeDays: Double = HALF_LIFE_DAYS): Double =
        0.5.pow(ageMs.coerceAtLeast(0L) / DAY_MS / halfLifeDays)
    fun topArtists(listens: List<Pair<String, Long>>, now: Long, limit: Int): List<String> {
        val weight = HashMap<String, Double>()
        for ((artist, at) in listens) {
            val key = artist.trim().lowercase()
            if (key.isBlank() || key.length > 80) continue
            weight[key] = (weight[key] ?: 0.0) + decay(now - at)
        }
        return weight.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key }
    }
    fun taste(plays: Int, completes: Int, skips: Int, lastAt: Long, liked: Boolean, now: Long): Double? {
        if (plays <= 0 && completes <= 0 && skips <= 0 && !liked) return null
        val raw = (completes * 2.0 + plays * 0.3 - skips * 0.8) / 8.0
        val faded = raw.coerceAtLeast(0.0) * (if (lastAt > 0) 0.5 + 0.5 * decay(now - lastAt) else 1.0)
        return (faded + if (liked) 0.8 else 0.0).coerceIn(0.0, 1.0)
    }
    fun rediscovery(completes: Int, skips: Int, lastAt: Long, now: Long): Double? {
        if (completes <= 0 || lastAt <= 0 || skips > completes) return null
        val days = ((now - lastAt) / DAY_MS).coerceAtLeast(0.0)
        return (days / 30.0).coerceIn(0.0, 1.0)
    }
}
