package com.dopavin.data

/** What kind of upload an online song is, read only from its own title / uploader (never guessed from nothing). */
enum class SongKind { OfficialAudio, MusicVideo, Live, Remix, Cover, Other }

object SongKinds {
    private val officialAudio = Regex("""(?i)\b(official\s+audio|audio\s+only|full\s+audio|provided to youtube)\b""")
    private val musicVideo = Regex("""(?i)\b(official\s+(music\s+)?video|music\s+video|official\s+mv|lyric(al)?\s+video|video\s+song|\bmv\b)\b""")
    private val live = Regex("""(?i)\b(live|concert|unplugged|performance)\b""")
    private val remix = Regex("""(?i)\b(remix|mashup|slowed|reverb|sped\s*up|lofi|lo-fi|nightcore)\b""")
    private val cover = Regex("""(?i)\b(cover|karaoke|instrumental|tribute)\b""")

    /** Order matters: a remix or live cut of an official video is still a remix / live cut. */
    fun classify(rawTitle: String, uploader: String): SongKind = when {
        remix.containsMatchIn(rawTitle) -> SongKind.Remix
        live.containsMatchIn(rawTitle) -> SongKind.Live
        cover.containsMatchIn(rawTitle) -> SongKind.Cover
        musicVideo.containsMatchIn(rawTitle) -> SongKind.MusicVideo
        officialAudio.containsMatchIn(rawTitle) || uploader.trim().endsWith("- Topic", ignoreCase = true) -> SongKind.OfficialAudio
        else -> SongKind.Other
    }
}

/** Combined filters over online results. Every active filter must hold (AND); an inactive one changes nothing. */
data class OnlineFilter(
    val kinds: Set<SongKind> = emptySet(),
    /** Only uploads from the last N days. Songs without a known upload date are left out (never guessed). */
    val maxAgeDays: Int? = null,
    val minDurationMs: Long? = null,
    val maxDurationMs: Long? = null,
    val downloadedOnly: Boolean = false,
    /** Exact (case-insensitive) artist as shown; empty = any. */
    val artist: String = "",
    /** Channel url; empty = any. */
    val channelUrl: String = "",
    val sort: Sort = Sort.Relevance,
) {
    enum class Sort { Relevance, Newest, Alphabetical }

    val isActive: Boolean
        get() = kinds.isNotEmpty() || maxAgeDays != null || minDurationMs != null || maxDurationMs != null ||
            downloadedOnly || artist.isNotBlank() || channelUrl.isNotBlank() || sort != Sort.Relevance

    class Info(
        val title: String,
        val artist: String,
        val channelUrl: String?,
        val kind: SongKind,
        val uploadedAt: Long,
        val durationMs: Long,
        val downloaded: Boolean,
    )

    /** Filters, then sorts. Relevance keeps the input order; Newest puts unknown dates last. */
    fun <T> apply(items: List<T>, now: Long, info: (T) -> Info): List<T> {
        val kept = items.filter { item ->
            val i = info(item)
            (kinds.isEmpty() || i.kind in kinds) &&
                (maxAgeDays == null || (i.uploadedAt > 0 && now - i.uploadedAt <= maxAgeDays * DAY_MS)) &&
                (minDurationMs == null || i.durationMs >= minDurationMs) &&
                (maxDurationMs == null || i.durationMs <= maxDurationMs) &&
                (!downloadedOnly || i.downloaded) &&
                (artist.isBlank() || i.artist.trim().equals(artist.trim(), ignoreCase = true)) &&
                (channelUrl.isBlank() || TrackMatch.sameChannel(i.channelUrl, channelUrl))
        }
        return when (sort) {
            Sort.Relevance -> kept
            Sort.Newest -> kept.sortedWith(compareByDescending<T> { info(it).uploadedAt.takeIf { t -> t > 0 } ?: Long.MIN_VALUE })
            Sort.Alphabetical -> kept.sortedBy { info(it).title.lowercase() }
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
