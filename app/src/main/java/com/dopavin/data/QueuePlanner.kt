package com.dopavin.data

/** Where a queued song came from. Generation itself is [SmartQueue], the single pipeline. */
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
