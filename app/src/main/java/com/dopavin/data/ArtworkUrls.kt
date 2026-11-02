package com.dopavin.data
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
