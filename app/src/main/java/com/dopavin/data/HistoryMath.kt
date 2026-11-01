package com.dopavin.data

/** The newest-last upsert rule of [HistoryStore], as pure code so it can be tested. */
object HistoryMath {
    /**
     * Records a play of the item matched by [match]. A play closer than [gapMs] to the last one only
     * refreshes nothing (returns false). Otherwise the existing entry is MOVED to the end with the new time
     * (so "the last entry" is always the latest play), or [create] is appended. Returns true when changed.
     */
    fun <T> upsertNewest(
        entries: MutableList<T>,
        now: Long,
        gapMs: Long,
        at: (T) -> Long,
        withAt: (T, Long) -> T,
        match: (T) -> Boolean,
        create: () -> T,
        cap: Int,
    ): Boolean {
        val i = entries.indexOfLast(match)
        if (i >= 0) {
            val e = entries[i]
            if (now - at(e) < gapMs) return false
            entries.removeAt(i)
            entries += withAt(e, now)
            return true
        }
        entries += create()
        if (entries.size > cap) repeat(entries.size - cap) { entries.removeAt(0) }
        return true
    }
}
