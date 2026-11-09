package com.dopavin.data

import android.content.Context
import org.json.JSONArray

/**
 * The first-run "start with your artists" card: whether it is finished (picked or skipped) and which
 * artists were picked. Local only. Picked artists act as a starting listening history until real plays exist.
 */
class WelcomeStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("welcome", Context.MODE_PRIVATE)

    fun isDone(): Boolean = prefs.getBoolean(KEY_DONE, false)

    fun picked(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_PICKED, "[]"))
        (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Marks the card as finished; an empty [picked] is a skip. */
    fun finish(picked: List<String>) {
        val clean = picked.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(MAX_PICKS)
        prefs.edit()
            .putBoolean(KEY_DONE, true)
            .putString(KEY_PICKED, JSONArray(clean).toString())
            .apply()
    }

    /** "Reset personalization": the card may appear again for a listener with no history. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val MAX_PICKS = 12
        private const val KEY_DONE = "done"
        private const val KEY_PICKED = "picked"
    }
}
