package com.dopavin.data

/**
 * Deterministic hybrid scorer (owner spec §7.1–7.4). Pure Kotlin, no Android types, unit-testable:
 * every feature is optional and a missing one simply drops out of the weighted denominator instead
 * of being scored as zero ("do not fabricate metadata"). There is no language signal anywhere —
 * that rule (§3.9) is structural here: the feature set cannot express one.
 */
data class RecFeatures(
    /** 0..1, present only when the mood actually biases retrieval; null = no mood data. */
    val moodMatch: Double? = null,
    /** 0..1 from plays/completes/likes/skips; null for a song the user has no history with. */
    val taste: Double? = null,
    /** 0..1, present when the song's artist is one the user follows/plays a lot. */
    val artistAffinity: Double? = null,
    /** 0..1, present when the song matches the current session context. */
    val session: Double? = null,
    /** 0..1 rediscovery: a song the user liked before and has not heard for a while. Null when unknown. */
    val novelty: Double? = null,
    /** 0..1 how closely the platform's own retrieval ties the song to the seed (related rank, newest uploads). */
    val relevance: Double? = null,
)

/** [confidence] is the share of the weight that real data covered; [value] is the weighted mean of what is known. */
data class Scored(val value: Double, val reason: ReasonCode, val confidence: Double = 1.0)

enum class ReasonCode {
    MOOD_MATCH, TASTE, FAVORITE_ARTIST, SESSION, REDISCOVERY, RELATED
}

/**
 * Starting weights, kept as data so they are visible and tunable (§7.4). Only features that really
 * exist are listed: there is no audio analysis, so no similarity / genre / energy weight is declared.
 */
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

/**
 * Weighted MEAN of the features that are present (absent ones drop out instead of counting as zero).
 * The reason is the feature that contributed most; [Scored.confidence] says how much of the weight was backed by data.
 */
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
