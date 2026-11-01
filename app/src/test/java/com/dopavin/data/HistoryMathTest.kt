package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMathTest {
    private data class E(val id: String, val at: Long)

    private fun record(list: MutableList<E>, id: String, now: Long, cap: Int = 100) = HistoryMath.upsertNewest(
        list, now, 90_000L, { it.at }, { e, t -> e.copy(at = t) }, { it.id == id }, { E(id, now) }, cap,
    )

    @Test fun replayMovesTheEntryToTheEnd() {
        val l = ArrayList<E>()
        record(l, "a", 1_000_000); record(l, "b", 2_000_000); record(l, "c", 3_000_000)
        assertTrue(record(l, "a", 4_000_000))
        assertEquals(listOf("b", "c", "a"), l.map { it.id })
        assertEquals(4_000_000L, l.last().at)
    }

    @Test fun replayWithinTheGapChangesNothing() {
        val l = ArrayList<E>()
        record(l, "a", 1_000_000); record(l, "b", 1_010_000)
        assertFalse(record(l, "a", 1_050_000))
        assertEquals(listOf("a", "b"), l.map { it.id })
    }

    @Test fun capDropsTheOldest() {
        val l = ArrayList<E>()
        for (i in 1..5) record(l, "s$i", i * 1_000_000L, cap = 3)
        assertEquals(listOf("s3", "s4", "s5"), l.map { it.id })
    }
}
