package com.dopavin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkUrlsTest {
    @Test fun googleSquareArtIsEnlarged() {
        val c = ArtworkUrls.candidates("https://lh3.googleusercontent.com/abc123=w544-h544-l90-rj", "vid")
        assertEquals("https://lh3.googleusercontent.com/abc123=w1200-h1200-l90-rj", c.first())
        assertEquals("https://lh3.googleusercontent.com/abc123=w544-h544-l90-rj", c.last())
    }

    @Test fun googleSizeShortFormIsEnlarged() {
        val c = ArtworkUrls.candidates("https://yt3.ggpht.com/x=s256", null)
        assertEquals("https://yt3.ggpht.com/x=w1200-h1200-l90-rj", c.first())
    }

    @Test fun ytimgTriesMaxResFirst() {
        val c = ArtworkUrls.candidates("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", "abcdefghijk")
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg",
                "https://i.ytimg.com/vi/abcdefghijk/sddefault.jpg",
                "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
            ),
            c,
        )
    }

    @Test fun ytimgIdIsReadFromUrlWhenMissing() {
        val c = ArtworkUrls.candidates("https://i.ytimg.com/vi/zzzzzzzzzzz/hqdefault.jpg", null)
        assertEquals("https://i.ytimg.com/vi/zzzzzzzzzzz/maxresdefault.jpg", c.first())
    }

    @Test fun unknownHostKeepsOriginalOnly() {
        assertEquals(listOf("https://example.com/a.jpg"), ArtworkUrls.candidates("https://example.com/a.jpg", "v"))
    }

    @Test fun googleUrlWithoutSizeKeepsOriginalOnly() {
        assertEquals(listOf("https://lh3.googleusercontent.com/abc"), ArtworkUrls.candidates("https://lh3.googleusercontent.com/abc", "v"))
    }
}
