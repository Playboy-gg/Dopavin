package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackNamesTest {
    private val dn = "Dhanda Nyoliwala"

    @Test fun `artist prefix and official suffix are removed`() {
        assertEquals("No Favours", TrackNames.clean("Dhanda Nyoliwala – No Favours (Official Music Video)", dn))
        assertEquals("Zigane", TrackNames.clean("Dhanda Nyoliwala – Zigane (Official Music Video)", dn))
        assertEquals("Big Plans", TrackNames.clean("Dhanda Nyoliwala - Big Plans (Official Music Video)", dn))
    }

    @Test fun `pipe separated uploader text is cut`() {
        assertEquals("Gaddafi", TrackNames.clean("Gaddafi (Official Music Video) | Dhanda Nyoliwala | Latest Punjabi Songs 2024", dn))
        assertEquals("Tere Pyar Mein Hue Hum Barbaad", TrackNames.clean("Tere Pyar Mein Hue Hum Barbaad | Full Video | T-Series"))
    }

    @Test fun `lyric and quality tags go but meaningful tags stay`() {
        assertEquals("Believer", TrackNames.clean("Believer (Official Lyric Video)"))
        assertEquals("Song (Remix)", TrackNames.clean("Song (Remix) [Official Video]"))
        assertEquals("Song (Acoustic Version)", TrackNames.clean("Song (Acoustic Version)"))
        assertEquals("Song (Official Acoustic Video)", TrackNames.clean("Song (Official Acoustic Video)"))
        assertEquals("Song ft. Other", TrackNames.clean("Artist - Song ft. Other (4K)", "Artist"))
        assertEquals("Song", TrackNames.clean("Song 【MV】"))
    }

    @Test fun `title - artist order and topic channels`() {
        assertEquals("Love Story", TrackNames.clean("Love Story - Taylor Swift", "Taylor Swift"))
        assertEquals("Song", TrackNames.clean("Artist - Song", "Artist - Topic"))
    }

    @Test fun `plain titles and a different artist prefix are untouched`() {
        assertEquals("Blinding Lights", TrackNames.clean("Blinding Lights", "The Weeknd"))
        assertEquals("Love - Drought", TrackNames.clean("Love - Drought", "Someone Else"))
        assertEquals("Official Video", TrackNames.clean("Official Video"))
        assertEquals("", TrackNames.clean("  "))
    }

    @Test fun `hashtags are dropped`() {
        assertEquals("Song", TrackNames.clean("Song #shorts #viral"))
    }

    @Test fun `non music uploads are rejected`() {
        assertFalse(TrackNames.looksLikeSong("Dhanda Nyoliwala Interview | Podcast", 1200))
        assertFalse(TrackNames.looksLikeSong("Making of Gaddafi", 300))
        assertFalse(TrackNames.looksLikeSong("Behind the Scenes - Panamera", 200))
        assertFalse(TrackNames.looksLikeSong("Song (Official Trailer)", 100))
        assertFalse(TrackNames.looksLikeSong("Non Stop Punjabi Jukebox", 600))
        assertFalse(TrackNames.looksLikeSong("A normal title", 20 * 60))
    }

    @Test fun `songs are accepted`() {
        assertTrue(TrackNames.looksLikeSong("Dhanda Nyoliwala – Gaddafi (Official Music Video)", 190))
        assertTrue(TrackNames.looksLikeSong("Panamera", 150))
        assertTrue(TrackNames.looksLikeSong("Song (Live)", 14 * 60))
    }
}
