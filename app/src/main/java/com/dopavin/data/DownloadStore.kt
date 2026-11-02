package com.dopavin.data
import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
class DownloadStore private constructor(context: Context) {
    data class Entry(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val addedAt: Long,
        val fileName: String,
        val coverName: String?,
        val sizeBytes: Long,
        val format: String,
    )
    private val appContext = context.applicationContext
    val dir: File = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
    private val indexFile = File(dir, INDEX_NAME)
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    init {
        synchronized(lock) {
            load()
            dir.listFiles()?.forEach { if (it.name.endsWith(PART_SUFFIX)) it.delete() }
            publish()
        }
    }
    fun isDownloaded(videoId: String): Boolean = synchronized(lock) { entries.containsKey(videoId) }
    fun fileFor(videoId: String): File? = synchronized(lock) {
        entries[videoId]?.let { File(dir, it.fileName) }?.takeIf { it.isFile && it.length() > 0 }
    }
    fun downloadedIds(): Set<String> = synchronized(lock) { entries.keys.toSet() }
    fun totalBytes(): Long = synchronized(lock) {
        dir.listFiles()?.filter { it.isFile && it.name != INDEX_NAME }?.sumOf { it.length() } ?: 0L
    }
    fun freeBytes(): Long = try {
        StatFs(dir.absolutePath).availableBytes
    } catch (e: Exception) {
        0L
    }
    fun partFile(videoId: String, extension: String): File = File(dir, "$videoId.$extension$PART_SUFFIX")
    fun coverFile(videoId: String): File = File(dir, "$videoId.jpg")
    fun commit(
        song: Song,
        part: File,
        extension: String,
        format: String,
        hasCover: Boolean,
    ): Entry? {
        val videoId = song.videoId ?: return null
        val target = File(dir, "$videoId.$extension")
        return synchronized(lock) {
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                null
            } else {
                val entry = Entry(
                    videoId = videoId,
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.durationMs,
                    addedAt = System.currentTimeMillis(),
                    fileName = target.name,
                    coverName = if (hasCover) coverFile(videoId).name else null,
                    sizeBytes = target.length(),
                    format = format,
                )
                entries[videoId] = entry
                persist()
                publish()
                entry
            }
        }
    }
    fun remove(videoId: String) {
        synchronized(lock) {
            val entry = entries.remove(videoId)
            if (entry != null) {
                File(dir, entry.fileName).delete()
                entry.coverName?.let { File(dir, it).delete() }
                persist()
                publish()
            }
        }
    }
    fun trimTo(limitBytes: Long, keepVideoId: String?): Int = synchronized(lock) {
        var total = entries.values.sumOf { it.sizeBytes }
        var removed = 0
        for (entry in entries.values.sortedBy { it.addedAt }) {
            if (total <= limitBytes) break
            if (entry.videoId == keepVideoId) continue
            File(dir, entry.fileName).delete()
            entry.coverName?.let { File(dir, it).delete() }
            entries.remove(entry.videoId)
            total -= entry.sizeBytes
            removed++
        }
        if (removed > 0) {
            persist()
            publish()
        }
        removed
    }
    fun removeAll(): Int = synchronized(lock) {
        val count = entries.size
        dir.listFiles()?.forEach { if (it.name != INDEX_NAME) it.delete() }
        entries.clear()
        persist()
        publish()
        count
    }
    private fun toSong(entry: Entry): Song {
        val audio = File(dir, entry.fileName)
        val cover = entry.coverName?.let { File(dir, it) }?.takeIf { it.isFile }
        return Song(
            id = stableOnlineId(entry.videoId),
            title = entry.title,
            artist = entry.artist,
            album = "",
            albumKey = UNKNOWN_ALBUM_KEY,
            durationMs = entry.durationMs,
            dateAdded = entry.addedAt,
            track = 0,
            isOnline = true,
            videoId = entry.videoId,
            thumbUri = cover?.let { Uri.fromFile(it) },
            sizeBytes = entry.sizeBytes,
            downloadUri = Uri.fromFile(audio),
            format = entry.format,
        )
    }
    private fun publish() {
        _songs.value = entries.values
            .filter { File(dir, it.fileName).isFile }
            .sortedByDescending { it.addedAt }
            .map(::toSong)
    }
    private fun persist() {
        val array = JSONArray()
        for (e in entries.values) {
            array.put(
                JSONObject()
                    .put(K_VIDEO, e.videoId)
                    .put(K_TITLE, e.title)
                    .put(K_ARTIST, e.artist)
                    .put(K_DURATION, e.durationMs)
                    .put(K_ADDED, e.addedAt)
                    .put(K_FILE, e.fileName)
                    .put(K_COVER, e.coverName.orEmpty())
                    .put(K_SIZE, e.sizeBytes)
                    .put(K_FORMAT, e.format),
            )
        }
        val tmp = File(dir, "$INDEX_NAME.tmp")
        try {
            tmp.writeText(array.toString())
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(array.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
        }
    }
    private fun load() {
        if (!indexFile.exists()) return
        runCatching {
            val array = JSONArray(indexFile.readText())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val videoId = o.optString(K_VIDEO).takeIf { it.isNotBlank() } ?: continue
                val file = o.optString(K_FILE)
                if (file.isBlank() || !File(dir, file).isFile) continue
                entries[videoId] = Entry(
                    videoId = videoId,
                    title = o.optString(K_TITLE),
                    artist = o.optString(K_ARTIST),
                    durationMs = o.optLong(K_DURATION),
                    addedAt = o.optLong(K_ADDED),
                    fileName = file,
                    coverName = o.optString(K_COVER).takeIf { it.isNotBlank() },
                    sizeBytes = o.optLong(K_SIZE),
                    format = o.optString(K_FORMAT),
                )
            }
        }
    }
    companion object {
        private const val DIR_NAME = "downloads"
        private const val INDEX_NAME = "index.json"
        const val PART_SUFFIX = ".part"
        private const val K_VIDEO = "v"
        private const val K_TITLE = "t"
        private const val K_ARTIST = "a"
        private const val K_DURATION = "d"
        private const val K_ADDED = "at"
        private const val K_FILE = "f"
        private const val K_COVER = "c"
        private const val K_SIZE = "s"
        private const val K_FORMAT = "fmt"
        @Volatile
        private var instance: DownloadStore? = null
        fun get(context: Context): DownloadStore =
            instance ?: synchronized(this) {
                instance ?: DownloadStore(context).also { instance = it }
            }
    }
}
