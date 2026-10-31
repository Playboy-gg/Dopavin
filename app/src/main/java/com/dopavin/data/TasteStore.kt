package com.dopavin.data

import android.content.Context
import org.json.JSONObject

/**
 * What the song has earned with the user (owner spec §7.5). Identity is the same one the rest of
 * the app uses for online songs: [videoId] when it has one, `"l<songId>"` otherwise, so local and
 * YouTube copies of a track never share a slot. SharedPreferences JSON, capped, apply()-writes —
 * the house pattern. Nothing here leaves the device, and a single skip never buries a song.
 */
class TasteStore(context: Context) {

    enum class Event { Started, Completed, Skipped }

    data class Taste(val plays: Int, val completes: Int, val skips: Int, val lastAt: Long, val blocked: Boolean)

    private val prefs = context.applicationContext
        .getSharedPreferences("taste", Context.MODE_PRIVATE)

    private val memory = LinkedHashMap<String, Taste>()
    private var version = 0L

    init {
        load()
    }

    /** Rises on every event; the ViewModel keeps its flows in sync with it. */
    fun version(): Long = synchronized(memory) { version }

    fun taste(song: Song): Taste = tasteKey(keyOf(song))

    fun tasteKey(key: String): Taste = synchronized(memory) { memory[key] ?: Taste(0, 0, 0, 0L, false) }

    fun setBlocked(song: Song) {
        write(keyOf(song)) { it.copy(blocked = true) }
    }

    fun isBlocked(song: Song): Boolean = synchronized(memory) { memory[keyOf(song)]?.blocked ?: false }

    fun event(song: Song, event: Event, now: Long = System.currentTimeMillis()) = when (event) {
        Event.Completed -> write(keyOf(song)) { t ->
            Taste(t.plays + 1, t.completes + 1, t.skips, now, t.blocked)
        }
        Event.Skipped -> write(keyOf(song)) { t ->
            Taste(t.plays + 1, t.completes, t.skips + 1, now, t.blocked)
        }
        // A fresh play of something already counted this session keeps the counters honest:
        // the 90 s gap guard in HistoryStore is the same idea.
        Event.Started -> write(keyOf(song)) { t ->
            if (now - t.lastAt > GAP_MS) Taste(t.plays + 1, t.completes, t.skips, now, t.blocked) else t
        }
    }

    fun clear() {
        synchronized(memory) {
            memory.clear()
            version++
        }
        prefs.edit().remove(KEY_JSON).apply()
    }

    private fun keyOf(song: Song): String = song.videoId ?: "l${song.id}"

    private fun write(key: String, update: (Taste) -> Taste) {
        synchronized(memory) {
            val next = update(memory[key] ?: Taste(0, 0, 0, 0L, false))
            memory[key] = next
            if (memory.size > CAP) {
                val dropping = memory.entries.iterator()
                repeat(memory.size - CAP) {
                    if (dropping.hasNext()) {
                        dropping.next()
                        dropping.remove()
                    }
                }
            }
            persist()
        }
    }

    private fun persist() {
        version++
        val json = JSONObject()
        for ((key, t) in memory) {
            json.put(
                key,
                JSONObject()
                    .put(K_PLAYS, t.plays)
                    .put(K_COMPLETES, t.completes)
                    .put(K_SKIPS, t.skips)
                    .put(K_AT, t.lastAt)
                    .put(K_BLOCKED, t.blocked),
            )
        }
        prefs.edit().putString(KEY_JSON, json.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY_JSON, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val e = json.optJSONObject(key) ?: continue
                memory[key] = Taste(
                    plays = e.optInt(K_PLAYS),
                    completes = e.optInt(K_COMPLETES),
                    skips = e.optInt(K_SKIPS),
                    lastAt = e.optLong(K_AT),
                    blocked = e.optBoolean(K_BLOCKED, false),
                )
            }
        }
    }

    private companion object {
        const val KEY_JSON = "entries"
        const val K_PLAYS = "p"
        const val K_COMPLETES = "c"
        const val K_SKIPS = "s"
        const val K_AT = "at"
        const val K_BLOCKED = "b"
        const val CAP = 400
        const val GAP_MS = 90_000L
    }
}
