package com.dopavin.data

/**
 * Better-quality candidates for a thumbnail url, best first, the original last. Pure Kotlin
 * (JVM-testable). Used when a download saves its cover so it is sharp on a big Now Playing screen.
 *
 * - Google image hosts (YouTube Music square art, `...=w544-h544-l90-rj`): the size suffix is
 *   rewritten to a large square.
 * - i.ytimg.com video thumbnails: `maxresdefault` (1280x720) is tried first; it does not exist for
 *   every video, so the caller falls back to the next candidate when it answers 404.
 */
object ArtworkUrls {
    private val googleSize = Regex("""=(?:[wsh]\d+)(?:-[a-z]+\d*)*$""")
    private const val BIG = "=w1200-h1200-l90-rj"

    fun candidates(url: String, videoId: String?): List<String> {
        val out = ArrayList<String>(3)
        val host = Regex("""^https?://([^/]+)/""").find(url)?.groupValues?.get(1)?.lowercase().orEmpty()
        when {
            host.endsWith("googleusercontent.com") || host.endsWith("ggpht.com") -> {
                if (googleSize.containsMatchIn(url)) out += googleSize.replace(url, BIG)
            }
            host.endsWith("ytimg.com") -> {
                val id = videoId?.takeIf { it.isNotBlank() } ?: Regex("""/vi(?:_webp)?/([^/]+)/""").find(url)?.groupValues?.get(1)
                if (id != null) {
                    out += "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
                    out += "https://i.ytimg.com/vi/$id/sddefault.jpg"
                }
            }
        }
        if (url !in out) out += url
        return out
    }
}
