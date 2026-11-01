package com.dopavin.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube Music search and live streams via NewPipe Extractor (which wraps InnerTube). Owner
 * approved 2026-10-19; see docs/01. Only what a search needs leaves the device: the query words.
 *
 * Search results map to [Song] with a stable id derived from the video id, so queue ids keep
 * working. Song list metadata is cached in a small preferences file so a restored queue can
 * recreate online rows without network. Stream urls are never cached; they are resolved fresh
 * every time playback loads a song (they are short-lived).
 */
class OnlineRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("online", Context.MODE_PRIVATE)

    /** In-memory mirror of [persistMetadata]; the source of truth for restore. */
    private val memory = HashMap<String, Song>()

    init {
        synchronized(memory) { loadMetadata() }
    }

    // ---- Search ----

    /** Where the next page of a search continues (kept in memory only). */
    class SearchCursor internal constructor(
        internal val query: String,
        internal val handler: org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler,
        internal val page: org.schabi.newpipe.extractor.Page,
    ) {
        val forQuery: String get() = query
    }

    /** One ranked page of search results and, when YouTube has more, where to continue. */
    class SearchPage(val songs: List<Song>, val more: SearchCursor?)

    /** YouTube Music search results as songs, capped at [limit]. Throws when nothing could be fetched. */
    suspend fun search(query: String, limit: Int = 10): List<Song> = searchPage(query).songs.take(limit)

    /** The whole first page of results (not capped), plus a cursor for [searchMore]. Throws when nothing could be fetched. */
    suspend fun searchPage(query: String): SearchPage = withContext(Dispatchers.IO) {
        init()
        // There is no separate YouTube Music StreamingService; music results come from the plain
        // YouTube service with the MUSIC_SONGS content filter (InnerTube music search).
        val service = ServiceList.YouTube
        val handler = service.getSearchQHFactory().fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            "",
        )
        val info = SearchInfo.getInfo(service, handler)
        var items: List<org.schabi.newpipe.extractor.InfoItem> = info.relatedItems
        // The "Songs" shelf only holds catalogued tracks: a release of the last days is usually
        // there only as a video. The plain video search is added (official uploaders only, never
        // twice the same title) so new songs are found too. Its failure never hides the songs.
        try {
            val videoHandler = service.getSearchQHFactory()
                .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
            val videos = SearchInfo.getInfo(service, videoHandler).relatedItems
            val have = items.filterIsInstance<StreamInfoItem>()
                .map { LyricsMatcher.normalise(TrackNames.clean(it.name.orEmpty(), it.uploaderName.orEmpty())) }
                .toHashSet()
            val extra = videos.filterIsInstance<StreamInfoItem>().filter { v ->
                ArtistPick.uploaderTier(v.uploaderName.orEmpty(), v.isUploaderVerified) == 0 &&
                    TrackNames.looksLikeSong(v.name.orEmpty(), v.duration) &&
                    LyricsMatcher.normalise(TrackNames.clean(v.name.orEmpty(), v.uploaderName.orEmpty())) !in have
            }
            items = items + extra
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // keep the songs
        }
        val songs = rankSearchItems(items, query)
        val next = info.nextPage
        SearchPage(songs, nextCursor(query, handler, next))
    }

    /** The page after [cursor]. Throws when it could not be fetched (the caller keeps the cursor to retry). */
    suspend fun searchMore(cursor: SearchCursor): SearchPage = withContext(Dispatchers.IO) {
        init()
        val more = SearchInfo.getMoreItems(ServiceList.YouTube, cursor.handler, cursor.page)
        val songs = rankSearchItems(more.items, cursor.query)
        val next = more.nextPage
        SearchPage(songs, nextCursor(cursor.query, cursor.handler, next))
    }

    private fun nextCursor(
        query: String,
        handler: org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler,
        next: org.schabi.newpipe.extractor.Page?,
    ): SearchCursor? =
        if (next != null && (!next.url.isNullOrEmpty() || !next.ids.isNullOrEmpty())) SearchCursor(query, handler, next) else null

    private fun rankSearchItems(items: List<org.schabi.newpipe.extractor.InfoItem>, query: String): List<Song> {
        // Genuine uploaders (verified / Topic / VEVO / Official) come first; small re-upload
        // channels are demoted, not dropped, so a rare song that only they have is still found.
        val found = ArrayList<Pair<Song, Int>>()
        for (item in items) {
            if (item !is StreamInfoItem) continue
            val song = toSong(item) ?: continue
            if (found.any { it.first.id == song.id }) continue
            found += song to ArtistPick.uploaderTier(item.uploaderName.orEmpty(), item.isUploaderVerified)
        }
        // Originals before remix/live/karaoke (unless asked for), then the same recording shown
        // twice is merged. Nothing is hidden because of its uploader.
        val ranked = TrackMatch.rank(found, query, { it.first.title }) { it.second }.map { it.first }
        val songs = TrackMatch.dedupe(ranked, { it.title }, { it.artist }, { it.durationMs })
        for (song in songs) synchronized(memory) { song.videoId?.let { memory[it] = song } }
        persistMetadata()
        return songs
    }

    /** Channel hits for [query] (YouTube Music artist filter, plain channel search as fallback). Empty on failure. */
    suspend fun searchArtists(query: String, limit: Int = 6): List<ArtistPick.Hit> = withContext(Dispatchers.IO) {
        init()
        val service = ServiceList.YouTube
        suspend fun run(filter: String): List<ArtistPick.Hit> = try {
            val handler = service.getSearchQHFactory().fromQuery(query, listOf(filter), "")
            SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<ChannelInfoItem>().mapNotNull { c ->
                val url = c.url.orEmpty()
                if (url.isBlank()) return@mapNotNull null
                ArtistPick.Hit(
                    name = c.name.orEmpty().ifBlank { return@mapNotNull null },
                    url = url,
                    avatar = c.thumbnails.asSequence().filter { it.url.isNotBlank() }.maxByOrNull { it.height }?.url,
                    subscribers = c.subscriberCount,
                    verified = c.isVerified,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val hits = run(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS).ifEmpty { run(YoutubeSearchQueryHandlerFactory.CHANNELS) }
        ArtistPick.rank(query, hits).take(limit)
    }

    /**
     * The genuine channel of [artist]: remembered, else the best name-matching verified/large
     * channel of a channel search. Null when none carries the name (callers then keep the uploader).
     */
    suspend fun genuineChannel(artist: String): ArtistIdentity? = withContext(Dispatchers.IO) {
        directory.get(artist)?.takeIf { it.channelUrl.isNotBlank() }?.let { return@withContext it }
        val pick = ArtistPick.best(artist, searchArtists(artist, 10)) ?: return@withContext null
        val found = ArtistIdentity(pick.name, pick.url, pick.avatar)
        directory.put(artist, found)
        found
    }

    /** Related tracks of a watch page (owner F-87). Empty on failure - shelves hide silently. */
    suspend fun related(videoId: String, limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        init()
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return@withContext emptyList()
        val songs = ArrayList<Song>(limit)
        for (item in info.relatedItems) {
            if (songs.size >= limit) break
            if (item !is StreamInfoItem) continue
            val song = toSong(item) ?: continue
            if (songs.any { it.id == song.id }) continue
            songs += song
            synchronized(memory) { song.videoId?.let { memory[it] = song } }
        }
        persistMetadata()
        songs
    }

    /** One search/related hit as a Song; null for anything the player should never see. */
    private fun toSong(item: StreamInfoItem): Song? {
        val type = item.streamType
        if (type != StreamType.VIDEO_STREAM && type != StreamType.AUDIO_STREAM) return null
        if (item.isShortFormContent) return null
        val seconds = item.duration
        if (seconds <= 0) return null // live or unknown
        if (seconds < MIN_SECONDS) return null
        val videoId = Uri.parse(item.url.orEmpty()).getQueryParameter(VIDEO_ID_PARAM)
            .takeUnless { it.isNullOrBlank() } ?: return null
        val rawTitle = item.name.orEmpty().trim()
        if (rawTitle.isBlank()) return null
        // Uploader noise ("(Official Music Video)", "| T-Series", "Artist - " prefix) is cut off the shown title.
        val title = TrackNames.clean(rawTitle, item.uploaderName.orEmpty())
        return Song(
            id = stableOnlineId(videoId),
            title = title,
            // The channel name exactly as NewPipe reports it (only the auto-generated " - Topic" tag is dropped).
            artist = item.uploaderName.orEmpty().replace(Regex("""(?i)\s*-\s*topic$"""), "").trim(),
            album = "", // resolved differently per surface; the search screen supplies its own line
            albumKey = UNKNOWN_ALBUM_KEY,
            durationMs = seconds * 1000L,
            dateAdded = 0L,
            track = 0,
            isOnline = true,
            videoId = videoId,
            thumbUri = thumbOf(item, videoId)?.let(Uri::parse),
            channelUrl = item.uploaderUrl?.takeIf { it.isNotBlank() },
        )
    }

    // ---- Channels (subscriptions, F-129) ----

    /** Name, url and avatar of a channel, or null when it cannot be read. */
    suspend fun channelInfo(url: String): Subscription? = withContext(Dispatchers.IO) {
        init()
        try {
            val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
            Subscription(
                url = info.url?.takeIf { it.isNotBlank() } ?: url,
                name = info.name.orEmpty().ifBlank { url },
                avatar = info.avatars.asSequence()
                    .filter { it.url.isNotBlank() }
                    .maxByOrNull { it.height }
                    ?.url,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Latest uploads of a channel as songs, newest first (the extractor lists the VIDEOS tab that
     * way). Falls back to the TRACKS tab, then the first tab. Empty on any failure.
     */
    suspend fun channelUploads(url: String, limit: Int = 12, fallbackName: String = ""): List<Song> =
        withContext(Dispatchers.IO) {
            init()
            try {
                val service = ServiceList.YouTube
                val info = ChannelInfo.getInfo(service, url)
                val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                    ?: info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.TRACKS) }
                    ?: info.tabs.firstOrNull()
                    ?: return@withContext emptyList()
                val items = ChannelTabInfo.getInfo(service, tab).relatedItems
                val songs = ArrayList<Song>(limit)
                for (item in items) {
                    if (songs.size >= limit) break
                    if (item !is StreamInfoItem) continue
                    // The channel's VIDEOS tab also holds interviews, vlogs, trailers and compilations.
                    if (!TrackNames.looksLikeSong(item.name.orEmpty(), item.duration)) continue
                    val base = toSong(item) ?: continue
                    // Channel tab items often carry no uploader of their own.
                    val artist = base.artist.ifBlank { info.name.orEmpty().ifBlank { fallbackName } }
                    val song = base.copy(
                        title = TrackNames.clean(item.name.orEmpty(), artist),
                        artist = artist,
                        channelUrl = base.channelUrl ?: url,
                    )
                    if (songs.any { it.id == song.id }) continue
                    songs += song
                    synchronized(memory) { song.videoId?.let { memory[it] = song } }
                }
                persistMetadata()
                songs
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ---- Artist identity (F-136) ----

    private val directory by lazy { ArtistDirectory(context) }

    /** The remembered channel + avatar of an artist name, without any network. */
    fun peekIdentity(name: String): ArtistIdentity? = directory.get(name)

    /**
     * Channel url and real profile picture of an artist: the remembered one, else from the channel
     * page when [channelHint] is known, else the first matching channel of a YouTube channel search.
     * Null when nothing was found or the network failed.
     */
    suspend fun artistIdentity(name: String, channelHint: String? = null): ArtistIdentity? =
        withContext(Dispatchers.IO) {
            directory.get(name)?.takeIf { it.avatar != null }?.let { return@withContext it }
            init()
            try {
                val service = ServiceList.YouTube
                val found = if (!channelHint.isNullOrBlank()) {
                    val info = ChannelInfo.getInfo(service, channelHint)
                    ArtistIdentity(
                        info.name.orEmpty().ifBlank { name },
                        channelHint,
                        info.avatars.asSequence().filter { it.url.isNotBlank() }.maxByOrNull { it.height }?.url,
                    )
                } else {
                    val handler = service.getSearchQHFactory()
                        .fromQuery(name, listOf(YoutubeSearchQueryHandlerFactory.CHANNELS), "")
                    val channels = SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<ChannelInfoItem>()
                    val wanted = name.trim().lowercase()
                    val pick = channels.firstOrNull {
                        it.name.orEmpty().lowercase().removeSuffix(" - topic").trim() == wanted
                    } ?: return@withContext directory.get(name) // no exact-name channel: better none than a wrong artist
                    ArtistIdentity(
                        pick.name.orEmpty().ifBlank { name },
                        pick.url.orEmpty(),
                        pick.thumbnails.asSequence().filter { it.url.isNotBlank() }.maxByOrNull { it.height }?.url,
                    )
                }
                if (found.channelUrl.isBlank()) return@withContext null
                directory.put(name, found)
                found
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                directory.get(name)
            }
        }

    // ---- Artist page (F-133) ----

    /** One album / release of an artist page; [url] is a playlist url. */
    data class ArtistAlbum(val name: String, val url: String, val thumb: String?)

    /** An artist (channel) page: header image, their songs (newest first) and their albums. */
    data class ArtistPage(
        val name: String,
        val avatar: String?,
        val songs: List<Song>,
        val albums: List<ArtistAlbum>,
    )

    /**
     * Everything one artist page shows: the channel's avatar, its VIDEOS (else TRACKS) tab as songs
     * and its ALBUMS (else PLAYLISTS) tab as albums. First page of each tab only. Null when the
     * channel itself cannot be read; a tab that fails just comes back empty.
     */
    suspend fun artistPage(url: String, fallbackName: String): ArtistPage? = withContext(Dispatchers.IO) {
        init()
        try {
            val service = ServiceList.YouTube
            val info = ChannelInfo.getInfo(service, url)
            val name = info.name.orEmpty().ifBlank { fallbackName }
            fun tab(vararg kinds: String) = kinds.firstNotNullOfOrNull { kind ->
                info.tabs.firstOrNull { it.contentFilters.contains(kind) }
            }
            val songs = ArrayList<Song>()
            val songTab = tab(ChannelTabs.VIDEOS, ChannelTabs.TRACKS) ?: info.tabs.firstOrNull()
            if (songTab != null) {
                val items = runCatching { ChannelTabInfo.getInfo(service, songTab).relatedItems }
                    .getOrDefault(emptyList())
                for (item in items) {
                    if (songs.size >= ARTIST_SONGS_MAX) break
                    if (item !is StreamInfoItem) continue
                    // The VIDEOS tab also holds interviews, vlogs, trailers and compilations: not songs.
                    if (!TrackNames.looksLikeSong(item.name.orEmpty(), item.duration)) continue
                    val base = toSong(item) ?: continue
                    val artist = base.artist.ifBlank { name }
                    val song = base.copy(
                        title = TrackNames.clean(item.name.orEmpty(), artist),
                        artist = artist,
                        channelUrl = base.channelUrl ?: url,
                    )
                    if (songs.any { it.id == song.id }) continue
                    songs += song
                    synchronized(memory) { song.videoId?.let { memory[it] = song } }
                }
                persistMetadata()
            }
            val albumTab = tab(ChannelTabs.ALBUMS, ChannelTabs.PLAYLISTS)
            val albums = albumTab
                ?.let { runCatching { ChannelTabInfo.getInfo(service, it).relatedItems }.getOrDefault(emptyList()) }
                .orEmpty()
                .filterIsInstance<PlaylistInfoItem>()
                .mapNotNull { item ->
                    val albumName = item.name.orEmpty().trim()
                    val albumUrl = item.url.orEmpty()
                    if (albumName.isBlank() || albumUrl.isBlank()) return@mapNotNull null
                    ArtistAlbum(albumName, albumUrl, item.thumbnails.maxByOrNull { it.height }?.url)
                }
                .take(ARTIST_ALBUMS_MAX)
            ArtistPage(
                name = name,
                avatar = info.avatars.asSequence().filter { it.url.isNotBlank() }.maxByOrNull { it.height }?.url,
                songs = songs,
                albums = albums,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Songs of a YouTube list plus the cover image YouTube gives it (null when it has none). */
    data class Listing(val songs: List<Song>, val cover: String?)

    private fun bestImage(images: List<org.schabi.newpipe.extractor.Image>?): String? =
        images?.asSequence()?.filter { it.url.isNotBlank() }?.maxByOrNull { it.height }?.url

    /** The songs of one album / playlist url, in order, with its cover. Empty on failure. */
    suspend fun playlist(url: String, limit: Int = 80, fallbackArtist: String = ""): Listing =
        withContext(Dispatchers.IO) {
            init()
            try {
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
                val songs = ArrayList<Song>(limit)
                for (item in info.relatedItems) {
                    if (songs.size >= limit) break
                    if (item !is StreamInfoItem) continue
                    val base = toSong(item) ?: continue
                    val artist = base.artist.ifBlank { fallbackArtist }
                    val song = base.copy(title = TrackNames.clean(item.name.orEmpty(), artist), artist = artist)
                    if (songs.any { it.id == song.id }) continue
                    songs += song
                    synchronized(memory) { song.videoId?.let { memory[it] = song } }
                }
                persistMetadata()
                Listing(songs, bestImage(runCatching { info.thumbnails }.getOrNull()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Listing(emptyList(), null)
            }
        }

    /** The songs of one album / playlist url, in order. Empty on failure. */
    suspend fun playlistSongs(url: String, limit: Int = 80, fallbackArtist: String = ""): List<Song> =
        playlist(url, limit, fallbackArtist).songs

    /**
     * YouTube's own radio (Mix) for a song: the playlist `RD<videoId>` read through NewPipe's mix
     * extractor, first page plus up to two more pages, capped at [limit]. The seed song comes
     * first. Empty on any failure (including the consent wall some regions show for mixes).
     */
    suspend fun radio(videoId: String, limit: Int = 50, fallbackArtist: String = ""): Listing =
        withContext(Dispatchers.IO) {
            init()
            try {
                val service = ServiceList.YouTube
                val url = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"
                val info = PlaylistInfo.getInfo(service, url)
                val songs = ArrayList<Song>(limit)
                fun take(items: List<org.schabi.newpipe.extractor.InfoItem>) {
                    for (item in items) {
                        if (songs.size >= limit) break
                        if (item !is StreamInfoItem) continue
                        val base = toSong(item) ?: continue
                        val artist = base.artist.ifBlank { fallbackArtist }
                        val song = base.copy(title = TrackNames.clean(item.name.orEmpty(), artist), artist = artist)
                        if (songs.any { it.id == song.id }) continue
                        songs += song
                        synchronized(memory) { song.videoId?.let { memory[it] = song } }
                    }
                }
                take(info.relatedItems)
                var page = info.nextPage
                var pages = 0
                while (songs.size < limit && page != null && pages < 2) {
                    val more = PlaylistInfo.getMoreItems(service, url, page)
                    take(more.items)
                    page = more.nextPage
                    pages++
                }
                persistMetadata()
                // A mix has no cover of its own: YouTube gives the seed video's thumbnail.
                Listing(songs, bestImage(runCatching { info.thumbnails }.getOrNull()) ?: songs.firstOrNull()?.thumbUri?.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Listing(emptyList(), null)
            }
        }

    // ---- Resolve ----

    /** Best audio-only stream url for a video id; called fresh whenever the player opens the item. */
    suspend fun resolve(videoId: String, quality: AudioQuality = AudioQuality.High): String =
        withContext(Dispatchers.IO) {
            val key = "$videoId/${quality.name}"
            val hit = urlCache[key]
            if (hit != null && hit.expiresAt > System.currentTimeMillis()) return@withContext hit.url
            init()
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val best = pickStream(info.audioStreams, quality, preferM4a = false, progressiveOnly = false)
            val url = best?.content?.takeIf { it.isNotBlank() }
                ?: throw IOException("No audio stream for $videoId")
            urlCache[key] = CachedUrl(url, System.currentTimeMillis() + URL_TTL_MS)
            url
        }

    /**
     * Resolves a stream url ahead of the tap so the first play starts without the extraction wait
     * (cached for [URL_TTL_MS]). Best effort: any failure is ignored, the real play resolves again.
     */
    suspend fun prefetch(videoId: String, quality: AudioQuality = AudioQuality.High) {
        try {
            resolve(videoId, quality)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ignored on purpose
        }
    }

    /** Blocking bridge for the player's data-source resolver (it runs on a loader thread). */
    fun resolveBlocking(videoId: String, quality: AudioQuality = AudioQuality.High): String =
        runBlocking { resolve(videoId, quality) }

    /** A stream chosen for saving to disk: plain progressive http, AAC (m4a) preferred because it
     * plays and exports everywhere. */
    data class DownloadStream(
        val url: String,
        val extension: String,
        val kbps: Int,
        /** Size in bytes when the provider reports it, else -1. */
        val contentLength: Long,
    ) {
        val formatLabel: String
            get() = extension.uppercase() + if (kbps > 0) " · $kbps kbps" else ""
    }

    suspend fun resolveForDownload(videoId: String, quality: AudioQuality): DownloadStream =
        withContext(Dispatchers.IO) {
            init()
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val stream = pickStream(info.audioStreams, quality, preferM4a = true, progressiveOnly = true)
                ?: throw IOException("No downloadable audio stream for $videoId")
            DownloadStream(
                url = stream.content,
                extension = stream.format?.suffix?.takeIf { it.isNotBlank() } ?: "m4a",
                kbps = kbpsOf(stream),
                contentLength = stream.itagItem?.contentLength ?: -1L,
            )
        }

    /** Bitrate in kbps whichever unit the extractor reported. 0 when unknown. */
    private fun kbpsOf(stream: AudioStream): Int {
        val raw = if (stream.averageBitrate > 0) stream.averageBitrate else stream.bitrate
        return when {
            raw <= 0 -> 0
            raw > 1000 -> raw / 1000
            else -> raw
        }
    }

    /**
     * Picks the stream for a quality level: High = highest bitrate, Standard = best at or below
     * 128 kbps, DataSaver = best at or below 64 kbps; when nothing fits under the cap the lowest
     * available stream is used. Streams without a url are never chosen.
     */
    private fun pickStream(
        streams: List<AudioStream>,
        quality: AudioQuality,
        preferM4a: Boolean,
        progressiveOnly: Boolean,
    ): AudioStream? {
        var pool = streams.filter { it.isUrl && !it.content.isNullOrBlank() }
        if (progressiveOnly) pool = pool.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (pool.isEmpty()) return null
        if (preferM4a) {
            val m4a = pool.filter { it.format == MediaFormat.M4A }
            if (m4a.isNotEmpty()) pool = m4a
        }
        val cap = when (quality) {
            AudioQuality.High -> Int.MAX_VALUE
            AudioQuality.Standard -> STANDARD_CAP_KBPS
            AudioQuality.DataSaver -> SAVER_CAP_KBPS
        }
        val fitting = pool.filter { kbpsOf(it) in 1..cap }
        return if (fitting.isNotEmpty()) {
            fitting.maxByOrNull { kbpsOf(it) }
        } else if (quality == AudioQuality.High) {
            pool.maxByOrNull { kbpsOf(it) }
        } else {
            pool.minByOrNull { kbpsOf(it).takeIf { k -> k > 0 } ?: Int.MAX_VALUE }
        }
    }

    // ---- Metadata store (used when a saved queue contains online songs) ----

    /** Snapshot of every known online song, most-search-recently first is not guaranteed. */
    fun cachedSongs(): List<Song> = synchronized(memory) { memory.values.toList() }

    /** Registers online songs that are about to play so [peek] finds them (saved to disk off-thread). */
    fun remember(songs: List<Song>) {
        synchronized(memory) { songs.forEach { s -> s.videoId?.let { memory[it] = s } } }
        Thread { persistMetadata() }.start()
    }

    /** Song with the matching online id, from the in-memory cache. Main-thread safe. */
    fun peek(id: Long): Song? = synchronized(memory) { memory.values.firstOrNull { it.id == id } }

    private fun persistMetadata() {
        val json = JSONObject()
        synchronized(memory) {
            memory.entries.take(METADATA_LIMIT).forEach { (videoId, song) ->
                json.put(
                    videoId,
                    JSONObject()
                        .put(K_ID, song.id)
                        .put(K_TITLE, song.title)
                        .put(K_ARTIST, song.artist)
                        .put(K_DURATION, song.durationMs)
                        .put(K_THUMB, song.thumbUri?.toString().orEmpty())
                        .put(K_CHANNEL, song.channelUrl.orEmpty()),
                )
            }
        }
        prefs.edit().putString(KEY_METADATA, json.toString()).apply()
    }

    private fun loadMetadata() {
        val raw = prefs.getString(KEY_METADATA, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            for (videoId in json.keys()) {
                val entry = json.optJSONObject(videoId) ?: continue
                if (!entry.has(K_ID)) continue
                memory[videoId] = Song(
                    id = entry.getLong(K_ID),
                    title = entry.optString(K_TITLE),
                    artist = entry.optString(K_ARTIST),
                    album = "",
                    albumKey = UNKNOWN_ALBUM_KEY,
                    durationMs = entry.optLong(K_DURATION),
                    dateAdded = 0L,
                    track = 0,
                    isOnline = true,
                    videoId = videoId,
                    thumbUri = entry.optString(K_THUMB).takeIf { it.isNotBlank() }?.let(Uri::parse),
                    channelUrl = entry.optString(K_CHANNEL).takeIf { it.isNotBlank() },
                )
            }
        }
    }

    // ---- Extractor plumbing ----

    /** NewPipe.init must happen once per process before any extractor call; retried if it failed before. */
    private fun init() {
        if (inited) return
        synchronized(OnlineRepository::class.java) {
            if (inited) return
            NewPipe.init(HttpDownloader())
            inited = true
        }
    }

    /** Plain HttpURLConnection downloader - extractor requests never carry anything but headers. */
    private class HttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val conn = URL(request.url()).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.instanceFollowRedirects = true
                request.headers().orEmpty().forEach { (name, values) ->
                    values.forEach { conn.addRequestProperty(name, it) }
                }
                when (request.httpMethod().uppercase()) {
                    "POST" -> {
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        request.dataToSend()?.let { data ->
                            conn.setFixedLengthStreamingMode(data.size)
                            conn.outputStream.use { it.write(data) }
                        }
                    }
                    "HEAD" -> conn.requestMethod = "HEAD"
                    else -> conn.requestMethod = "GET"
                }
                val code = conn.responseCode
                val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return Response(code, conn.responseMessage.orEmpty(), conn.headerFields, body, conn.url.toString())
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun thumbOf(item: StreamInfoItem, videoId: String): String? =
        item.thumbnails.asSequence()
            .filter { it.url.isNotBlank() }
            .maxByOrNull { it.height }
            ?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private class CachedUrl(val url: String, val expiresAt: Long)

    private companion object {
        @Volatile
        var inited = false

        /** Stream urls resolved in this process; short-lived by design (they are tied to time and IP). */
        val urlCache = java.util.concurrent.ConcurrentHashMap<String, CachedUrl>()
        const val URL_TTL_MS = 20L * 60L * 1000L
        const val KEY_METADATA = "songs"
        const val K_ID = "id"
        const val K_TITLE = "title"
        const val K_ARTIST = "artist"
        const val K_DURATION = "duration"
        const val K_THUMB = "thumb"
        const val K_CHANNEL = "channel"
        const val ARTIST_SONGS_MAX = 60
        const val ARTIST_ALBUMS_MAX = 30
        const val VIDEO_ID_PARAM = "v"
        const val MIN_SECONDS = 60
        const val METADATA_LIMIT = 300
        const val TIMEOUT_MS = 12_000
        const val STANDARD_CAP_KBPS = 128
        const val SAVER_CAP_KBPS = 64
    }
}
