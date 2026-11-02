package com.dopavin.data
enum class Mood(val seedQueries: List<String>) {
    Chill(listOf("chill lofi songs", "relaxing chill songs")),
    Happy(listOf("happy feel good songs", "uplifting songs")),
    Energetic(listOf("energetic workout songs", "party dance hits")),
    Focus(listOf("focus deep work instrumental songs", "ambient focus songs")),
    Reflective(listOf("reflective mellow songs", "emotional soulful songs")),
    Romantic(listOf("romantic love songs", "romantic duet songs")),
}
