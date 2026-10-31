package com.dopavin.data

import java.util.Locale

/**
 * F-105: finds candidate duplicates in the local library. Two songs are candidates when the
 * normalised title AND artist match exactly and the durations differ by at most [DURATION_TOLERANCE_MS].
 * Title matching is deliberately forgiving (case, punctuation and the "Unknown" placeholders are
 * normalised away); final deletion is always the user's explicit action with a confirmation.
 */

/** Two songs that look like the same recording, kept with their rows for the review list. */
data class DuplicateGroup(val title: String, val artist: String, val songs: List<Song>)

private const val DURATION_TOLERANCE_MS = 2_000L
private const val MIN_LENGTH_MS = 45_000L

/** Folds whitespace/punctuation/case so "Song - Live" vs "Song (Live)" do not collide unfairly. */
internal fun duplicateKey(title: String, artist: String): String {
    fun fold(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .trim()
    val foldedTitle = fold(title)
    // Everything that has fallen back to the Unknown placeholder must never group together.
    val foldedArtist = fold(artist)
    if (foldedTitle.isEmpty() || foldedArtist.isEmpty()) return ""
    return "$foldedArtist|$foldedTitle"
}

/**
 * Groups local songs by the normalised title + artist pair when both exist, keeping only
 * groups with at least [MIN_GROUP_SIZE] long-enough songs that agree on the duration.
 */
fun findDuplicates(songs: List<Song>): List<DuplicateGroup> {
    val local = songs.filter { !it.isOnline && it.durationMs >= MIN_LENGTH_MS }
    val buckets = HashMap<String, MutableList<Song>>()
    for (song in local) {
        val key = duplicateKey(song.title, song.artist)
        if (key.isEmpty()) continue
        buckets.getOrPut(key) { ArrayList() }.add(song)
    }
    return buckets.values
        .filter { group ->
            group.size >= 2 && group.all { candidate ->
                kotlin.math.abs(candidate.durationMs - group[0].durationMs) <= DURATION_TOLERANCE_MS
            }
        }
        .map { group ->
            DuplicateGroup(
                title = group.minBy { it.title.length }.title,
                artist = group[0].artist,
                songs = group.sortedBy { it.id },
            )
        }
        .sortedWith { a, b ->
            val c = compareNames(a.artist, b.artist)
            if (c != 0) c else compareNames(a.title, b.title)
        }
}
