package com.dopavin.data
import android.net.Uri
enum class SortOrder { Title, Artist, Recent }
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumKey: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val track: Int,
    val isOnline: Boolean = false,
    val videoId: String? = null,
    val thumbUri: Uri? = null,
    val sizeBytes: Long = 0L,
    val downloadUri: Uri? = null,
    val format: String = "",
    val channelUrl: String? = null,
) {
    val isDownloaded: Boolean get() = downloadUri != null
    val uri: Uri
        get() = downloadUri ?: Uri.parse("$ONLINE_SCHEME://stream/${videoId.orEmpty()}")
    val artworkUri: Uri get() = thumbUri ?: uri
}
fun stableOnlineId(videoId: String): Long =
    ONLINE_ID_BIAS or (videoId.hashCode().toLong() and ONLINE_ID_MASK)
private const val ONLINE_ID_BIAS = 0x7E000000L
private const val ONLINE_ID_MASK = 0x00FFFFFFL
const val ONLINE_SCHEME = "dopavin-yt"
const val UNKNOWN_ALBUM_KEY = -1L
data class Album(
    val key: Long,
    val title: String,
    val artist: String?,
    val songs: List<Song>,
) {
    val artworkUri: Uri get() = songs.first().artworkUri
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
}
data class Artist(
    val key: String,
    val name: String,
    val albumCount: Int,
    val songs: List<Song>,
)
data class Groups(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val byId: Map<Long, Song> = emptyMap(),
) {
    val albumsByKey: Map<Long, Album> by lazy { albums.associateBy { it.key } }
    val artistsByKey: Map<String, Artist> by lazy { artists.associateBy { it.key } }
}
data class Visible(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val totalSongs: Int = 0,
) {
    val allEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}
private fun startsWithLetterOrDigit(s: String): Boolean = s.isNotEmpty() && s[0].isLetterOrDigit()
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
