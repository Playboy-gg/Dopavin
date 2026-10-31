package com.dopavin.data

import com.dopavin.R

/**
 * Deterministic hybrid scorer (owner spec §7.1–7.4). Pure Kotlin, no Android types, unit-testable:
 * every feature is optional and a missing one simply drops out of the weighted denominator instead
 * of being scored as zero ("do not fabricate metadata"). There is no language signal anywhere —
 * that rule (§3.9) is structural here: the feature set cannot express one.
 */
data class RecFeatures(
    /** 0..1, present only when the mood actually biases retrieval; null = no mood data. */
    val moodMatch: Double? = null,
    /** 0..1 from plays/completes/likes/skips. */
    val taste: Double? = null,
    /** 0..1, present when the song's artist is one the user follows/plays a lot. */
    val artistAffinity: Double? = null,
    /** 0..1, present when the song matches the current session context. */
    val session: Double? = null,
    /** 0..1 rediscovery: matching taste but unheard recently. */
    val novelty: Double? = null,
)

data class Scored(val value: Double, val reason: ReasonCode)

enum class ReasonCode {
    MOOD_MATCH, TASTE, FAVORITE_ARTIST, SESSION, REDISCOVERY
}

/** The one truthful reason word, from strings.xml, for the UI row's subtitle. */
fun ReasonCode.reasonRes(): Int = when (this) {
    ReasonCode.MOOD_MATCH -> R.string.reason_mood
    ReasonCode.TASTE -> R.string.reason_taste
    ReasonCode.FAVORITE_ARTIST -> R.string.reason_artist
    ReasonCode.SESSION -> R.string.reason_session
    ReasonCode.REDISCOVERY -> R.string.reason_rediscovery
}

/** The spec's starting weights, kept as data so they are visible + tunable (§7.4). */
data class ScorerWeights(
    val mood: Double = 0.25,
    val taste: Double = 0.25,
    // §7.4 "genre/audio-feature similarity" — no audio analysis exists yet, so the weight pair is
    // kept only through artistAffinity/session weighting; the constants below stay for later.
    val similarity: Double = 0.15,
    val artist: Double = 0.10,
    val session: Double = 0.10,
    val novelty: Double = 0.10,
    val context: Double = 0.05,
)

fun score(features: RecFeatures, weights: ScorerWeights = ScorerWeights()): Scored {
    val parts = listOfNotNull(
        features.moodMatch?.let { weights.mood to (it to ReasonCode.MOOD_MATCH) },
        features.taste?.let { weights.taste to (it to ReasonCode.TASTE) },
        features.artistAffinity?.let { weights.artist to (it to ReasonCode.FAVORITE_ARTIST) },
        features.session?.let { weights.session to (it to ReasonCode.SESSION) },
        features.novelty?.let { weights.novelty to (it to ReasonCode.REDISCOVERY) },
    )
    if (parts.isEmpty()) return Scored(0.0, ReasonCode.TASTE)
    val total = parts.sumOf { (w, _) -> w }
    var best = 0.0
    var bestReason = ReasonCode.TASTE
    for ((w, contribution) in parts) {
        val weighted = w * contribution.first
        if (weighted > best) {
            best = weighted
            bestReason = contribution.second
        }
    }
    return Scored(best / total, bestReason)
}
