package com.dopavin.data

/**
 * The six mood presets (owner spec §6.2). A mood is a listening goal, not a diagnosis: it biases
 * candidate retrieval (the seed queries drive the online side) and scoring, never the queue alone.
 * Labels live in strings.xml; this enum stays free of Android types so the scorer can be unit-tested.
 * The queries are backend search phrasing, not a language concept (spec §3.9).
 */
enum class Mood(val seedQueries: List<String>) {
    Chill(listOf("chill lofi songs", "relaxing chill songs")),
    Happy(listOf("happy feel good songs", "uplifting songs")),
    Energetic(listOf("energetic workout songs", "party dance hits")),
    Focus(listOf("focus deep work instrumental songs", "ambient focus songs")),
    Reflective(listOf("reflective mellow songs", "emotional soulful songs")),
    Romantic(listOf("romantic love songs", "romantic duet songs")),
}
