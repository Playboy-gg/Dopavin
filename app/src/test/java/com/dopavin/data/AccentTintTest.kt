package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTintTest {
    @Test fun hsvRoundTrips() {
        for (c in AccentTint.PRESETS) {
            val h = AccentTint.toHsv(c)
            val back = AccentTint.fromHsv(h[0], h[1], h[2])
            for (shift in listOf(16, 8, 0)) assertTrue(kotlin.math.abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
        }
    }

    @Test fun settingValuesMapToBases() {
        assertNull(AccentTint.baseFor(AccentTint.DEFAULT, 0xFF112233.toInt()))
        assertEquals(AccentTint.PRESETS[0], AccentTint.baseFor(1, null))
        assertEquals(AccentTint.PRESETS.last(), AccentTint.baseFor(AccentTint.PRESETS.size, null))
        assertNull(AccentTint.baseFor(AccentTint.PRESETS.size + 1, null))
        assertEquals(0xFF112233.toInt(), AccentTint.baseFor(AccentTint.DYNAMIC, 0xFF112233.toInt()))
        assertNull(AccentTint.baseFor(AccentTint.DYNAMIC, null)) // no artwork colour yet: app colours stay
    }

    @Test fun darkArtworkStillGivesABrightAccentOnDark() {
        val d = AccentTint.derive(0xFF101018.toInt(), dark = true)
        assertTrue(AccentTint.luminance(d.accent) > 0.12f)
    }

    @Test fun lightThemeAccentIsDarkEnoughForContrast() {
        val d = AccentTint.derive(0xFFFFFF66.toInt(), dark = false)
        assertTrue(AccentTint.luminance(d.accent) <= 0.31f)
    }

    @Test fun gradientEndsDifferFromEachOther() {
        val d = AccentTint.derive(AccentTint.PRESETS[5], dark = true)
        assertNotEquals(d.start, d.end)
    }

    @Test fun inkIsReadableOnTheAccent() {
        for (c in AccentTint.PRESETS) for (dark in listOf(true, false)) {
            val d = AccentTint.derive(c, dark)
            val l1 = AccentTint.luminance(d.accent)
            val l2 = AccentTint.luminance(d.onAccent)
            val ratio = (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
            assertTrue("contrast $ratio for ${Integer.toHexString(c)} dark=$dark", ratio >= 3f)
        }
    }

    private fun max(a: Float, b: Float) = if (a > b) a else b
    private fun min(a: Float, b: Float) = if (a < b) a else b
}
