package com.dopavin.data
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
data class Playlist(val name: String, val songs: List<Song>, val cover: String? = null)
private const val FILE_NAME = "playlists.json"
private const val COVERS_FILE = "playlist_covers.json"
private fun songToJson(song: Song): JSONObject = JSONObject().apply {
    put("id", song.id)
    put("title", song.title)
    put("artist", song.artist)
    put("album", song.album)
    put("albumKey", song.albumKey)
    put("durationMs", song.durationMs)
    put("dateAdded", song.dateAdded)
    put("track", song.track)
    put("isOnline", song.isOnline)
    put("videoId", song.videoId ?: JSONObject.NULL)
    put("thumb", song.thumbUri?.toString() ?: JSONObject.NULL)
}
private fun songFromJson(o: JSONObject): Song = Song(
    id = o.getLong("id"),
    title = o.optString("title"),
    artist = o.optString("artist"),
    album = o.optString("album"),
    albumKey = o.optLong("albumKey", UNKNOWN_ALBUM_KEY),
    durationMs = o.optLong("durationMs", 0L),
    dateAdded = o.optLong("dateAdded", 0L),
    track = o.optInt("track", 0),
    isOnline = o.optBoolean("isOnline", false),
    videoId = o.optNullableString("videoId"),
    thumbUri = o.optNullableUri("thumb"),
)
private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
private fun JSONObject.optNullableUri(key: String): Uri? =
    if (has(key) && !isNull(key)) Uri.parse(getString(key)) else null
private fun encodeAll(playlists: Map<String, List<Song>>): String {
    val root = JSONObject()
    for ((name, songs) in playlists) {
        root.put(name, JSONArray().apply { songs.forEach { put(songToJson(it)) } })
    }
    return root.toString()
}
private fun decodeAll(raw: String): LinkedHashMap<String, MutableList<Song>> {
    val out = LinkedHashMap<String, MutableList<Song>>()
    val root = JSONObject(raw)
    for (key in root.keys()) {
        val array = root.getJSONArray(key)
        out[key] = (0 until array.length())
            .map { songFromJson(array.getJSONObject(it)) }
            .filter { it.videoId != null }
            .toMutableList()
    }
    return out
}
object PlaylistCodec {
    fun encode(playlists: Map<String, List<Song>>): String = encodeAll(playlists)
    fun decode(raw: String): Map<String, List<Song>> = LinkedHashMap(decodeAll(raw))
    fun normaliseName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
}
class PlaylistStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val lock = Any()
    private fun readAll(): LinkedHashMap<String, MutableList<Song>> = synchronized(lock) {
        if (!file.exists()) return LinkedHashMap()
        runCatching { decodeAll(file.readText()) }.getOrDefault(LinkedHashMap())
    }
    private fun writeAll(playlists: Map<String, List<Song>>) {
        synchronized(lock) {
            val tmp = File(appContext.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(encodeAll(playlists))
            if (!tmp.renameTo(file)) {
                file.writeText(encodeAll(playlists))
                tmp.delete()
            }
        }
    }
    private val coversFile = File(appContext.filesDir, COVERS_FILE)
    private fun readCovers(): Map<String, String> = synchronized(lock) {
        if (!coversFile.exists()) return emptyMap()
        runCatching {
            val o = JSONObject(coversFile.readText())
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }
    private fun writeCovers(covers: Map<String, String>) = synchronized(lock) {
        runCatching { coversFile.writeText(JSONObject(covers).toString()) }
    }
    suspend fun loadAll(): List<Playlist> = withContext(Dispatchers.IO) {
        val covers = readCovers()
        readAll().map { (name, songs) -> Playlist(name, songs.toList(), covers[name]) }
    }
    suspend fun load(name: String): Playlist? = withContext(Dispatchers.IO) {
        readAll()[name]?.let { Playlist(name, it.toList(), readCovers()[name]) }
    }
    suspend fun create(name: String, songs: List<Song>, cover: String? = null): Boolean = withContext(Dispatchers.IO) {
        val key = PlaylistCodec.normaliseName(name)
        if (key.isEmpty()) return@withContext false
        val all = readAll()
        if (key in all) return@withContext false
        all[key] = songs.toMutableList()
        writeAll(all)
        if (!cover.isNullOrBlank()) writeCovers(readCovers() + (key to cover))
        true
    }
    suspend fun append(name: String, songs: List<Song>): Boolean = withContext(Dispatchers.IO) {
        val all = readAll()
        val existing = all[name] ?: return@withContext false
        val seen = existing.map { it.id }.toMutableSet()
        songs.forEach { song -> if (seen.add(song.id)) existing.add(song) }
        writeAll(all)
        true
    }
    suspend fun removeSong(name: String, index: Int): Boolean = withContext(Dispatchers.IO) {
        val all = readAll()
        val songs = all[name] ?: return@withContext false
        if (index !in songs.indices) return@withContext false
        songs.removeAt(index)
        if (songs.isEmpty()) {
            all.remove(name)
            writeCovers(readCovers() - name)
        }
        writeAll(all)
        true
    }
    suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        val all = readAll()
        val removed = all.remove(name) != null
        if (removed) {
            writeAll(all)
            writeCovers(readCovers() - name)
        }
        removed
    }
}
