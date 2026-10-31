package com.dopavin.data

/**
 * Queue generation (owner spec §8). Pure Kotlin: takes scored candidates, applies hard exclusions
 * (already-queued ids), deduplicates by identity, and adds the best scores with a soft
 * artist-run cap so a general-purpose batch does not stack one artist. Album/artist-focused modes
 * pass a bigger cap. Deterministic: stable sort, no randomness; an exhausted ranking simply ends
 * the batch (no retries), per §8.1.
 */
enum class QueueOrigin {
    /** Tapped by the user from anywhere. */
    MANUAL,
    /** Part of a restored or explicit album/playlist open. */
    PLAYLIST,
    /** Appended by Smart Autoplay (F-92). */
    SMART_AUTOPLAY,
    MOOD_QUEUE,
    SONG_RADIO,
    PERSONAL_MIX,
}

data class Candidate(val song: Song, val scored: Scored)

data class Batched(val song: Song, val reason: ReasonCode)

/** Plan one generated batch: skip queued ids, dedupe, best scores first, capped artist runs. */
fun planBatch(
    candidates: List<Candidate>,
    excludeIds: Set<Long>,
    size: Int,
    maxArtistRun: Int = 2,
): List<Batched> {
    if (size <= 0) return emptyList()
    val ranked = candidates
        .asSequence()
        .filter { it.song.id !in excludeIds }
        .distinctBy { it.song.id }
        .sortedWith(compareByDescending<Candidate> { it.scored.value }.thenBy { it.song.title.lowercase() })
        .toList()
    val out = ArrayList<Batched>(size)
    val artistRuns = HashMap<String, Int>()
    for (c in ranked) {
        if (out.size >= size) break
        val artist = c.song.artist.trim().lowercase()
        val run = artistRuns[artist] ?: 0
        if (maxArtistRun > 0 && run >= maxArtistRun) continue // soft cap: the ranking moves on
        artistRuns[artist] = run + 1
        out += Batched(c.song, c.scored.reason)
    }
    return out
}
