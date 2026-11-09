package com.dopavin.data

import android.content.Context

/** Small SharedPreferences store: last queue, position and modes, plus one-time flags. */
class PlaybackStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Saved(
        val ids: List<Long>,
        val currentId: Long,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
    )

    fun save(ids: List<Long>, currentId: Long, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        prefs.edit()
            .putString(KEY_IDS, ids.joinToString(","))
            .putLong(KEY_CURRENT_ID, currentId)
            .putLong(KEY_POSITION, positionMs)
            .putBoolean(KEY_SHUFFLE, shuffle)
            .putInt(KEY_REPEAT, repeatMode)
            .apply()
    }

    /** Returns null when nothing (or an empty queue) was saved. */
    fun load(): Saved? {
        val raw = prefs.getString(KEY_IDS, null) ?: return null
        val ids = raw.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return Saved(
            ids = ids,
            currentId = prefs.getLong(KEY_CURRENT_ID, -1L),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0),
        )
    }

    var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "playback"
        const val KEY_IDS = "ids"
        const val KEY_CURRENT_ID = "current_id"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val KEY_NOTIFICATION_ASKED = "notification_asked"
    }
}
