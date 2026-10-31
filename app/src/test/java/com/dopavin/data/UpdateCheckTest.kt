package com.dopavin.data
import org.junit.Assert.*
import org.junit.Test
class UpdateCheckTest {
    @Test fun versionOrder() {
        assertTrue(UpdateCheck.isNewer("v1.1", "1.0"))
        assertTrue(UpdateCheck.isNewer("1.10", "1.9"))
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.0", "1.0"))
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0"))
        assertFalse(UpdateCheck.isNewer("v0.9", "1.0"))
        assertFalse(UpdateCheck.isNewer("nightly", "1.0"))
        assertTrue(UpdateCheck.isNewer("v1.2-beta", "1.1"))
    }
    @Test fun parsesRelease() {
        val json = """{"tag_name":"v1.1","html_url":"https://github.com/o/r/releases/tag/v1.1","body":"- Recap\n- Widget","draft":false,"prerelease":false,"assets":[{"name":"notes.txt","browser_download_url":"x","size":1},{"name":"Dopavin-1.1.apk","browser_download_url":"https://github.com/o/r/releases/download/v1.1/Dopavin-1.1.apk","size":12345678}]}"""
        val r = UpdateCheck.parseRelease(json)!!
        assertEquals("v1.1", r.tag)
        assertEquals("- Recap\n- Widget", r.notes)
        assertEquals("https://github.com/o/r/releases/download/v1.1/Dopavin-1.1.apk", r.apkUrl)
        assertEquals(12345678L, r.sizeBytes)
    }
    @Test fun skipsDraftsAndBadJson() {
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"v2","draft":true}"""))
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"v2","prerelease":true}"""))
        assertNull(UpdateCheck.parseRelease("not json"))
        assertNull(UpdateCheck.parseRelease("""{"name":"x"}"""))
    }
    @Test fun releaseWithoutApkStillHasPage() {
        val r = UpdateCheck.parseRelease("""{"tag_name":"v1.2"}""")!!
        assertNull(r.apkUrl)
        assertEquals("https://github.com/playboy-gg/Dopavin/releases", r.pageUrl)
    }
    @Test fun notesBecomePlainLines() {
        val md = "## What's new\r\n\r\n- **Updates** page with [release notes](http://x)\n* `Download` inside the app\n---\nPlain line"
        val lines = UpdateCheck.plainNotes(md)
        assertEquals(
            listOf(
                NoteLine("What's new", heading = true),
                NoteLine("Updates page with release notes", bullet = true),
                NoteLine("Download inside the app", bullet = true),
                NoteLine("Plain line"),
            ),
            lines,
        )
        assertTrue(UpdateCheck.plainNotes("").isEmpty())
    }
}
