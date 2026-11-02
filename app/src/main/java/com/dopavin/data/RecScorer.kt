package com.dopavin.data
data class RecFeatures(
    val moodMatch: Double? = null,
    val taste: Double? = null,
    val artistAffinity: Double? = null,
    val session: Double? = null,
    val novelty: Double? = null,
    val relevance: Double? = null,
)
data class Scored(val value: Double, val reason: ReasonCode, val confidence: Double = 1.0)
enum class ReasonCode {
    MOOD_MATCH, TASTE, FAVORITE_ARTIST, SESSION, REDISCOVERY, RELATED
}
data class ScorerWeights(
    val relevance: Double = 0.35,
    val mood: Double = 0.25,
    val taste: Double = 0.20,
    val artist: Double = 0.10,
    val session: Double = 0.10,
    val novelty: Double = 0.10,
) {
    val total: Double get() = relevance + mood + taste + artist + session + novelty
}
fun score(features: RecFeatures, weights: ScorerWeights = ScorerWeights()): Scored {
    val parts = listOfNotNull(
        features.relevance?.let { Triple(weights.relevance, it, ReasonCode.RELATED) },
        features.moodMatch?.let { Triple(weights.mood, it, ReasonCode.MOOD_MATCH) },
        features.taste?.let { Triple(weights.taste, it, ReasonCode.TASTE) },
        features.artistAffinity?.let { Triple(weights.artist, it, ReasonCode.FAVORITE_ARTIST) },
        features.session?.let { Triple(weights.session, it, ReasonCode.SESSION) },
        features.novelty?.let { Triple(weights.novelty, it, ReasonCode.REDISCOVERY) },
    )
    if (parts.isEmpty()) return Scored(0.0, ReasonCode.TASTE, 0.0)
    val present = parts.sumOf { it.first }
    var sum = 0.0
    var best = -1.0
    var reason = ReasonCode.TASTE
    for ((w, x, r) in parts) {
        val v = x.coerceIn(0.0, 1.0)
        sum += w * v
        if (w * v > best) { best = w * v; reason = r }
    }
    return Scored(sum / present, reason, present / weights.total)
}
