package com.dopavin.data

import android.content.Context
import org.json.JSONArray

/** The last searches the user committed (keyboard Search key or opening a result). On-device only, capped. */
class SearchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    fun all(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Newest first, no case-insensitive duplicates. Returns the new list. */
    fun add(query: String): List<String> {
        val q = query.trim()
        if (q.length < 2) return all()
        return save(listOf(q) + all().filterNot { it.equals(q, ignoreCase = true) })
    }

    fun remove(query: String): List<String> = save(all().filterNot { it.equals(query, ignoreCase = true) })

    fun clear(): List<String> = save(emptyList())

    private fun save(list: List<String>): List<String> {
        val capped = list.take(MAX)
        prefs.edit().putString(KEY, JSONArray(capped).toString()).apply()
        return capped
    }

    private companion object {
        const val KEY = "recent"
        const val MAX = 12
    }
}
