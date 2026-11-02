package com.dopavin.data
import java.net.HttpURLConnection
import java.net.URL
data class ReleaseInfo(
    val tag: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
    val sizeBytes: Long,
)
enum class UpdateStatus { Idle, Checking, UpToDate, Available, Failed }
enum class UpdateStep { None, Downloading, Ready, Failed }
data class UpdateUi(
    val status: UpdateStatus = UpdateStatus.Idle,
    val release: ReleaseInfo? = null,
    val step: UpdateStep = UpdateStep.None,
    val progress: Float = 0f,
    val errorRes: Int = 0,
)
data class NoteLine(val text: String, val heading: Boolean = false, val bullet: Boolean = false)
object UpdateCheck {
    const val REPO = "playboy-gg/Dopavin"
    sealed interface Fetch {
        data class Ok(val release: ReleaseInfo) : Fetch
        data object None : Fetch
        data object Error : Fetch
    }
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
