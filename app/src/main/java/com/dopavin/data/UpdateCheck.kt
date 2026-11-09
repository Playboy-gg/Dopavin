package com.dopavin.data

import java.net.HttpURLConnection
import java.net.URL

/** The newest GitHub release: what the Updates page shows and where "Download" goes. */
data class ReleaseInfo(
    val tag: String,
    val notes: String,
    /** Direct link of the first .apk attached to the release, or null when only a page exists. */
    val apkUrl: String?,
    val pageUrl: String,
    val sizeBytes: Long,
)

enum class UpdateStatus { Idle, Checking, UpToDate, Available, Failed }

/** F-207: where the APK is on its way to the installer. */
enum class UpdateStep { None, Downloading, Ready, Failed }

/** What the Updates page reads: the check result plus the state of the in-app download. */
data class UpdateUi(
    val status: UpdateStatus = UpdateStatus.Idle,
    val release: ReleaseInfo? = null,
    val step: UpdateStep = UpdateStep.None,
    /** 0..1 while [step] is Downloading; 0 when the size is unknown. */
    val progress: Float = 0f,
    /** String resource explaining a failed download. */
    val errorRes: Int = 0,
)

/** One line of release notes after the GitHub markdown was reduced to plain text. */
data class NoteLine(val text: String, val heading: Boolean = false, val bullet: Boolean = false)

/**
 * F-206: asks GitHub for the latest release of the owner's repository and compares its tag with the installed
 * version. Nothing is downloaded here (see [UpdateInstaller], F-207). Parsing, comparison and the notes clean-up
 * are plain Kotlin so they are unit-tested.
 */
object UpdateCheck {
    const val REPO = "playboy-gg/Dopavin"

    sealed interface Fetch {
        data class Ok(val release: ReleaseInfo) : Fetch
        /** The repository has no published (non-draft, non-prerelease) release yet. */
        data object None : Fetch
        data object Error : Fetch
    }

    /** True when [tag] ("v1.2", "1.2.0") is a higher version than [current] ("1.1"). Unreadable versions are never newer. */
    fun isNewer(tag: String, current: String): Boolean {
        val a = numbers(tag)
        val b = numbers(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.trim().trimStart('v', 'V').takeWhile { it.isDigit() || it == '.' }
            .split('.').mapNotNull { it.toIntOrNull() }

    /** Reads the GitHub "latest release" JSON; null for drafts, pre-releases and anything unreadable. */
    fun parseRelease(json: String): ReleaseInfo? {
        val root = MiniJson.obj(MiniJson.parse(json)) ?: return null
        if (root["draft"] == true || root["prerelease"] == true) return null
        val tag = MiniJson.str(root["tag_name"])?.trim().orEmpty()
        if (tag.isEmpty()) return null
        val asset = MiniJson.list(root["assets"]).orEmpty()
            .mapNotNull { MiniJson.obj(it) }
            .firstOrNull { MiniJson.str(it["name"])?.endsWith(".apk", ignoreCase = true) == true }
        return ReleaseInfo(
            tag = tag,
            notes = MiniJson.str(root["body"]).orEmpty().trim(),
            apkUrl = asset?.let { MiniJson.str(it["browser_download_url"]) },
            pageUrl = MiniJson.str(root["html_url"]) ?: "https://github.com/$REPO/releases",
            sizeBytes = (asset?.get("size") as? Double)?.toLong() ?: 0L,
        )
    }

    /**
     * GitHub release notes are markdown; the Updates page shows plain lines: headings (#), bullets (-, *) and text.
     * Bold/italic/code marks and link syntax are removed, blank runs collapse to one gap.
     */
    fun plainNotes(markdown: String): List<NoteLine> {
        val out = ArrayList<NoteLine>()
        for (raw in markdown.replace("\r", "").lines()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("```") || line.all { it == '-' || it == '=' || it == '*' } && line.length >= 3) continue
            val heading = line.startsWith("#")
            if (heading) line = line.trimStart('#').trim()
            val bullet = !heading && (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ "))
            if (bullet) line = line.substring(2).trim()
            line = line
                .replace(Regex("\\[([^\\]]+)\\]\\(([^)]*)\\)"), "$1")
                .replace("**", "").replace("__", "").replace("`", "")
            if (line.isNotEmpty()) out += NoteLine(line, heading, bullet)
        }
        return out
    }

    /** Blocking; call off the main thread. */
    fun fetchLatest(): Fetch = try {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Dopavin-update-check")
        try {
            when (conn.responseCode) {
                200 -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
                    ?.let { Fetch.Ok(it) } ?: Fetch.None
                404 -> Fetch.None
                else -> Fetch.Error
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Fetch.Error
    }
}
