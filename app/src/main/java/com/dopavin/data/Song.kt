package com.dopavin.data

import android.net.Uri

enum class LibraryTab { Songs, Artists, Liked }

enum class SortOrder { Title, Artist, Recent }

/**
 * One playable song. Every song comes from a YouTube Music search ([isOnline] + [videoId]); once it
 * is downloaded it also carries [downloadUri], the private file the player opens instead of the
 * network. Identity is [id] (a stable hash of the video id), never the title.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    /** Grouping key of the album, or [UNKNOWN_ALBUM_KEY] for every song without an album. */
    val albumKey: Long,
    val durationMs: Long,
    val dateAdded: Long,
    /** Track number inside the album; 0 when unknown. */
    val track: Int,
    /** True when this song was found through the YouTube search (streams unless downloaded). */
    val isOnline: Boolean = false,
    /** YouTube video id when [isOnline]. */
    val videoId: String? = null,
    /** Artwork: an http(s) thumbnail for streamed songs, a private file uri for downloaded ones. */
    val thumbUri: Uri? = null,
    /** File size in bytes of the downloaded copy; 0 when not downloaded or unknown. */
    val sizeBytes: Long = 0L,
    /** The private audio file of a downloaded song (file uri); null while it only streams. */
    val downloadUri: Uri? = null,
    /** Short audio description of the downloaded file, e.g. "M4A · 128 kbps"; empty when unknown. */
    val format: String = "",
    /** YouTube channel url of the uploader/artist (online songs), for subscribing; null when unknown. */
    val channelUrl: String? = null,
) {
    /** True when a private copy of this song exists on the device. */
    val isDownloaded: Boolean get() = downloadUri != null

    /** What the player should open. Downloaded: the private file. Otherwise a playback marker
     * (scheme [ONLINE_SCHEME], host "stream", path = video id) that the service resolves to a live
     * stream - or to the downloaded file, should one appear meanwhile - right before loading. */
    val uri: Uri
        get() = downloadUri ?: Uri.parse("$ONLINE_SCHEME://stream/${videoId.orEmpty()}")

    /** What the UI and the media notification show as artwork. */
    val artworkUri: Uri get() = thumbUri ?: uri
}

/**
 * Stable long id from a video id, so queue ids, likes and history survive restarts. Shifted into a
 * range of its own so ids stay recognisable and never collide with plain counters.
 */
fun stableOnlineId(videoId: String): Long =
    ONLINE_ID_BIAS or (videoId.hashCode().toLong() and ONLINE_ID_MASK)

private const val ONLINE_ID_BIAS = 0x7E000000L
private const val ONLINE_ID_MASK = 0x00FFFFFFL

/** Uri scheme marker for online songs; the player's resolving data source picks it up. */
const val ONLINE_SCHEME = "dopavin-yt"

const val UNKNOWN_ALBUM_KEY = -1L

data class Album(
    val key: Long,
    val title: String,
    /** The shared artist, or null when the songs have different artists ("Various artists"). */
    val artist: String?,
    val songs: List<Song>,
) {
    val artworkUri: Uri get() = songs.first().artworkUri
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
}

data class Artist(
    /** Lower-case name used for grouping and for navigation. */
    val key: String,
    val name: String,
    val albumCount: Int,
    val songs: List<Song>,
)

/** Everything derived from the full (unfiltered) song list. */
data class Groups(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val byId: Map<Long, Song> = emptyMap(),
) {
    val albumsByKey: Map<Long, Album> by lazy { albums.associateBy { it.key } }
    val artistsByKey: Map<String, Artist> by lazy { artists.associateBy { it.key } }
}

/** What the UI shows after search and sort are applied. */
data class Visible(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    /** Number of downloaded songs in the whole library, before search. Zero means nothing is downloaded. */
    val totalSongs: Int = 0,
) {
    val allEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

// ---- Sorting ----

private fun startsWithLetterOrDigit(s: String): Boolean = s.isNotEmpty() && s[0].isLetterOrDigit()

/** Case-insensitive; names starting with a symbol sort after names starting with a letter or digit. */
fun compareNames(a: String, b: String): Int {
    val aAlnum = startsWithLetterOrDigit(a)
    val bAlnum = startsWithLetterOrDigit(b)
    if (aAlnum != bAlnum) return if (aAlnum) -1 else 1
    return a.compareTo(b, ignoreCase = true)
}

private fun trackOrder(track: Int): Int = if (track <= 0) Int.MAX_VALUE else track

fun sortSongs(songs: List<Song>, order: SortOrder): List<Song> {
    val comparator: Comparator<Song> = when (order) {
        SortOrder.Title -> Comparator { x, y ->
            val c = compareNames(x.title, y.title)
            if (c != 0) c else x.id.compareTo(y.id)
        }
        SortOrder.Artist -> Comparator { x, y ->
            val c = compareNames(x.artist, y.artist)
            if (c != 0) c else {
                val t = compareNames(x.title, y.title)
                if (t != 0) t else x.id.compareTo(y.id)
            }
        }
        SortOrder.Recent -> Comparator { x, y ->
            val c = y.dateAdded.compareTo(x.dateAdded)
            if (c != 0) c else {
                val t = compareNames(x.title, y.title)
                if (t != 0) t else x.id.compareTo(y.id)
            }
        }
    }
    return songs.sortedWith(comparator)
}

// ---- Grouping ----

fun groupAlbums(songs: List<Song>): List<Album> {
    val byTrack = Comparator<Song> { x, y ->
        val c = trackOrder(x.track).compareTo(trackOrder(y.track))
        if (c != 0) c else compareNames(x.title, y.title)
    }
    return songs
        .groupBy { it.albumKey }
        .map { (key, list) ->
            val sorted = list.sortedWith(byTrack)
            val artists = sorted.map { it.artist }.distinct()
            Album(
                key = key,
                title = sorted.first().album,
                artist = if (artists.size == 1) artists[0] else null,
                songs = sorted,
            )
        }
        .sortedWith(Comparator { a, b -> compareNames(a.title, b.title) })
}

fun groupArtists(songs: List<Song>): List<Artist> {
    val byAlbumThenTrack = Comparator<Song> { x, y ->
        val a = compareNames(x.album, y.album)
        if (a != 0) a else {
            val t = trackOrder(x.track).compareTo(trackOrder(y.track))
            if (t != 0) t else compareNames(x.title, y.title)
        }
    }
    return songs
        .groupBy { it.artist.lowercase() }
        .map { (key, list) ->
            val sorted = list.sortedWith(byAlbumThenTrack)
            Artist(
                key = key,
                name = sorted.first().artist,
                albumCount = sorted.map { it.albumKey }.distinct().size,
                songs = sorted,
            )
        }
        .sortedWith(Comparator { a, b -> compareNames(a.name, b.name) })
}

// ---- Search (case-insensitive substring) ----

fun filterSongs(songs: List<Song>, query: String): List<Song> {
    val q = query.trim()
    if (q.isEmpty()) return songs
    return songs.filter {
        it.title.contains(q, ignoreCase = true) ||
            it.artist.contains(q, ignoreCase = true) ||
            it.album.contains(q, ignoreCase = true)
    }
}

fun filterAlbums(albums: List<Album>, query: String): List<Album> {
    val q = query.trim()
    if (q.isEmpty()) return albums
    return albums.filter { album ->
        album.title.contains(q, ignoreCase = true) ||
            album.songs.any { it.artist.contains(q, ignoreCase = true) }
    }
}

fun filterArtists(artists: List<Artist>, query: String): List<Artist> {
    val q = query.trim()
    if (q.isEmpty()) return artists
    return artists.filter { it.name.contains(q, ignoreCase = true) }
}
