package com.dopavin.data
object HistoryMath {
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
