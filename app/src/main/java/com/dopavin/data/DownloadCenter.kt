package com.dopavin.data
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.dopavin.playback.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
enum class DownloadStatus { Queued, Active, Failed }
enum class DownloadError { Network, WifiOnly, NoSpace, Unavailable }
data class DownloadItem(
    val videoId: String,
    val song: Song,
    val status: DownloadStatus,
    val progress: Float = -1f,
    val error: DownloadError? = null,
)
object DownloadCenter {
    private const val PARALLEL = 2
    private const val CHUNK_BYTES = 1_048_576L
    private const val TIMEOUT_MS = 20_000
    private const val MIN_VALID_BYTES = 10_000L
    private const val MIN_HD_COVER_BYTES = 5_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(PARALLEL)
    private val jobs = HashMap<String, Job>()
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()
    fun enqueue(context: Context, song: Song): Boolean {
        val app = context.applicationContext
        val videoId = song.videoId ?: return false
        if (com.dopavin.data.Subsonic.isServerSong(videoId)) return false
        if (DownloadStore.get(app).isDownloaded(videoId)) return false
        synchronized(jobs) {
            val existing = _items.value.firstOrNull { it.videoId == videoId }
            if (existing != null && existing.status != DownloadStatus.Failed) return false
            if (jobs.containsKey(videoId)) return false
            _items.update { list ->
                list.filterNot { it.videoId == videoId } +
                    DownloadItem(videoId, song, DownloadStatus.Queued)
            }
            jobs[videoId] = scope.launch { run(app, song, videoId) }
        }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        return true
    }
    fun cancel(videoId: String) {
        synchronized(jobs) { jobs.remove(videoId)?.cancel() }
        _items.update { list -> list.filterNot { it.videoId == videoId } }
    }
    fun cancelAll() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        _items.value = emptyList()
    }
    fun isBusy(): Boolean = _items.value.any { it.status != DownloadStatus.Failed }
    private suspend fun run(app: Context, song: Song, videoId: String) {
        val store = DownloadStore.get(app)
        var part: File? = null
        try {
            gate.withPermit {
                setState(videoId, DownloadStatus.Active, -1f, null)
                val settings = SettingsStore(app).load()
                if (settings.downloadWifiOnly && !onUnmeteredNetwork(app)) {
                    throw DownloadFailure(DownloadError.WifiOnly)
                }
                val stream = try {
                    OnlineRepository(app).resolveForDownload(videoId, settings.downloadQuality)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw DownloadFailure(DownloadError.Unavailable)
                } catch (e: Exception) {
                    throw DownloadFailure(DownloadError.Unavailable)
                }
                if (stream.contentLength > 0 && store.freeBytes() in 1 until (stream.contentLength * 11 / 10)) {
                    throw DownloadFailure(DownloadError.NoSpace)
                }
                val target = store.partFile(videoId, stream.extension)
                part = target
                fetch(stream.url, target, stream.contentLength) { fraction ->
                    setState(videoId, DownloadStatus.Active, fraction, null)
                }
                val hasCover = fetchCover(song, store.coverFile(videoId))
                val entry = store.commit(song, target, stream.extension, stream.formatLabel, hasCover)
                    ?: throw DownloadFailure(DownloadError.NoSpace)
                check(entry.sizeBytes >= MIN_VALID_BYTES)
                if (settings.downloadLimitMb > 0) {
                    store.trimTo(settings.downloadLimitMb * 1024L * 1024L, videoId)
                }
            }
            _items.update { list -> list.filterNot { it.videoId == videoId } }
        } catch (e: CancellationException) {
            part?.delete()
            throw e
        } catch (e: DownloadFailure) {
            part?.delete()
            setState(videoId, DownloadStatus.Failed, -1f, e.error)
        } catch (e: Exception) {
            part?.delete()
            val full = e.message.orEmpty().contains("ENOSPC", ignoreCase = true) ||
                e.message.orEmpty().contains("No space", ignoreCase = true)
            setState(videoId, DownloadStatus.Failed, -1f, if (full) DownloadError.NoSpace else DownloadError.Network)
        } finally {
            synchronized(jobs) { jobs.remove(videoId) }
        }
    }
    private class DownloadFailure(val error: DownloadError) : Exception()
    private fun setState(videoId: String, status: DownloadStatus, progress: Float, error: DownloadError?) {
        _items.update { list ->
            list.map { if (it.videoId == videoId) it.copy(status = status, progress = progress, error = error) else it }
        }
    }
    private fun onUnmeteredNetwork(app: Context): Boolean {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
    private suspend fun fetch(
        url: String,
        target: File,
        knownTotal: Long,
        onProgress: (Float) -> Unit,
    ) {
        var offset = 0L
        var total = knownTotal
        if (target.exists()) target.delete()
        FileOutputStream(target, false).use { out ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val end = if (total > 0) minOf(offset + CHUNK_BYTES, total) - 1 else offset + CHUNK_BYTES - 1
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = TIMEOUT_MS
                    conn.readTimeout = TIMEOUT_MS
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", USER_AGENT)
                    conn.setRequestProperty("Range", "bytes=$offset-$end")
                    val code = conn.responseCode
                    when (code) {
                        HttpURLConnection.HTTP_PARTIAL -> {
                            val rangeTotal = conn.getHeaderField("Content-Range")
                                ?.substringAfterLast('/')?.trim()?.toLongOrNull()
                            if (rangeTotal != null && rangeTotal > 0) total = rangeTotal
                            val requested = end - offset + 1
                            var got = 0L
                            conn.inputStream.use { input ->
                                while (true) {
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    got += n
                                    offset += n
                                    if (total > 0) onProgress((offset.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                            if (got == 0L) throw IOException("Empty range response")
                            if (got < requested && total <= 0) break
                            if (total > 0 && offset >= total) break
                        }
                        HttpURLConnection.HTTP_OK -> {
                            if (offset != 0L) throw IOException("Server restarted the stream")
                            val length = conn.contentLengthLong
                            conn.inputStream.use { input ->
                                while (true) {
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    offset += n
                                    if (length > 0) onProgress((offset.toFloat() / length).coerceIn(0f, 1f))
                                }
                            }
                            total = offset
                            break
                        }
                        416 -> break
                        else -> throw IOException("HTTP $code")
                    }
                } finally {
                    conn.disconnect()
                }
            }
            out.flush()
        }
        if (offset < MIN_VALID_BYTES || (knownTotal > 0 && offset < knownTotal)) {
            throw IOException("Incomplete download: $offset of $total bytes")
        }
        onProgress(1f)
    }
    private fun fetchCover(song: Song, target: File): Boolean {
        val uri = song.thumbUri ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        for (candidate in ArtworkUrls.candidates(uri.toString(), song.videoId)) {
            val minBytes = if (candidate == uri.toString()) 200 else MIN_HD_COVER_BYTES
            if (downloadImage(candidate, target, minBytes)) return true
        }
        return false
    }
    private fun downloadImage(url: String, target: File, minBytes: Int): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                false
            } else {
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.size < minBytes) {
                    false
                } else {
                    target.writeBytes(bytes)
                    true
                }
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        false
    }
}
