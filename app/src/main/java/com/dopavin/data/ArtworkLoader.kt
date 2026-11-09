package com.dopavin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.net.HttpURLConnection
import java.net.URL

/** Pixel sizes used for artwork. Kept small and few so the cache is effective. */
object ArtworkSize {
    const val Row = 96
    const val Cell = 320
    const val Large = 512
    /** Full-screen player artwork. */
    const val Player = 1024
}

/**
 * Loads artwork (YouTube thumbnails over http, private cover files, content uris) and caches it in memory.
 * Files without artwork are remembered so scrolling back does not retry them.
 */
object ArtworkLoader {

    /** Declared first: [gate] below uses it while the object is being initialised. */
    private const val MAX_PARALLEL_LOADS = 4
    private const val RETRY_AFTER_MS = 60_000L

    // Lag fix: 1/16 of the heap held only a handful of 1024 px player covers (4 MB each) or ~40 grid
    // covers, so scrolling a shelf back and forth kept re-decoding. 1/8 keeps a whole screen of art warm.
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(8 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** When a picture that failed on the network may be tried again. */
    private val retryAfter = ConcurrentHashMap<String, Long>()

    private val missing: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** Video thumbnails whose 1280x720 frame does not exist, so the extra request is never repeated. */
    private val noMaxRes: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Lag fix: `Dispatchers.IO` allows 64 threads, so a fast scroll started dozens of simultaneous
     * downloads + decodes that fought the UI thread for CPU and the radio for bandwidth. Only a few
     * run at once; the rest wait here and are cancelled for free when their row leaves the screen.
     */
    private val gate = Semaphore(MAX_PARALLEL_LOADS)

    private fun keyOf(uri: Uri, sizePx: Int): String = "$uri@$sizePx"

    /** Returns a cached bitmap immediately, or null. Safe to call on the main thread. */
    fun peek(uri: Uri, sizePx: Int): Bitmap? = cache.get(keyOf(uri, sizePx))

    suspend fun load(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        cache.get(keyOf(uri, sizePx))?.let { return it }
        return withContext(Dispatchers.IO) {
            // Rows asking for the same cover queue here; the first one fills the cache and the
            // rest return from it inside loadBlocking without a second download.
            gate.withPermit { loadBlocking(context, uri, sizePx) }
        }
    }

    /** Blocking version. Never call on the main thread. */
    fun loadBlocking(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val key = keyOf(uri, sizePx)
        cache.get(key)?.let { return it }
        if (missing.contains(key)) return null
        // A recent network failure: do not hammer an offline connection on every scroll.
        retryAfter[key]?.let { if (System.currentTimeMillis() < it) return null }

        var networkTrouble = false
        val bitmap = try {
            decode(context.applicationContext, uri, sizePx)
        } catch (e: Exception) {
            // A network problem on a web picture is temporary: it is retried later. Anything else (a missing
            // or unreadable file) is permanent for this session.
            networkTrouble = e is java.io.IOException && (uri.scheme == "https" || uri.scheme == "http")
            null
        }
        when {
            bitmap != null -> {
                cache.put(key, bitmap)
                retryAfter.remove(key)
            }
            networkTrouble -> retryAfter[key] = System.currentTimeMillis() + RETRY_AFTER_MS
            else -> missing.add(key)
        }
        return bitmap
    }

    private fun decode(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val scheme = uri.scheme?.lowercase() ?: return null
        return when (scheme) {
            "https", "http" -> decodeRemote(context, uri, sizePx)
            ONLINE_SCHEME -> null // online songs carry their artwork on [thumbUri], never on the marker uri
            "file" -> decodeFile(uri, sizePx)
            else -> decodeLocal(context, uri, sizePx)
        }
    }

    /** Decodes an HTTP thumbnail straight from YouTube's image CDN. */
    private fun decodeRemote(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val url = uri.toString()
        // Big views try YouTube's 1280x720 frame first; it does not exist for every video.
        if (sizePx >= ArtworkSize.Large && url.contains("i.ytimg.com/vi/") && url.contains("/hqdefault.") &&
            !noMaxRes.contains(url)
        ) {
            val big = url.replace("/hqdefault.", "/maxresdefault.")
            val hit = fetchRemote(context, big, sizePx)
            if (hit != null) return hit
            noMaxRes.add(url)
        }
        return fetchRemote(context, url, sizePx)
    }

    private fun fetchRemote(context: Context, url: String, sizePx: Int): Bitmap? {
        val finalUrl = sharpUrl(url, sizePx)
        // Disk first: thumbnails survive app restarts, so a cold start does not re-download the
        // whole Home feed's artwork.
        val bytes = DiskArtwork.read(context, finalUrl) ?: run {
            val conn = URL(finalUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = REMOTE_TIMEOUT_MS
                conn.readTimeout = REMOTE_TIMEOUT_MS
                if (conn.responseCode != 200) return null
                conn.inputStream.use { it.readBytes() }.also { DiskArtwork.write(context, finalUrl, it) }
            } finally {
                conn.disconnect()
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Google-hosted thumbnails (lh3 / ggpht, e.g. YouTube Music covers) end in `=w120-h120...`; asking for
     * the size we actually draw gives a sharp image instead of an upscaled tiny one.
     */
    private fun sharpUrl(url: String, sizePx: Int): String {
        if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) return url
        val size = sizePx.coerceIn(120, 1200)
        val sized = Regex("=w\\d+-h\\d+[^?#]*$").find(url) ?: return url
        return url.substring(0, sized.range.first) + "=w$size-h$size-l90-rj"
    }

    /** Decodes a private cover image saved next to a download. Audio files have no cover of their own. */
    private fun decodeFile(uri: Uri, sizePx: Int): Bitmap? {
        val path = uri.path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun decodeLocal(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } finally {
            retriever.release()
        }
    }

    private const val REMOTE_TIMEOUT_MS = 10_000
}

/**
 * Small on-disk cache of the raw bytes of remote thumbnails (cache dir, so the system may clear it).
 * Keyed by the final request url. Oldest-used files are dropped when it grows past [MAX_BYTES].
 * Every failure is swallowed: a broken cache must only ever mean "download again".
 */
private object DiskArtwork {
    private const val DIR = "artwork"
    private const val MAX_BYTES = 64L * 1024 * 1024
    private const val TRIM_TO_BYTES = 48L * 1024 * 1024
    private const val TRIM_EVERY_WRITES = 40

    private var writes = 0

    private fun dir(context: Context): File =
        File(context.applicationContext.cacheDir, DIR).also { it.mkdirs() }

    private fun nameOf(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    fun read(context: Context, url: String): ByteArray? = try {
        val file = File(dir(context), nameOf(url))
        if (file.isFile && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            file.readBytes()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    fun write(context: Context, url: String, bytes: ByteArray) {
        try {
            val folder = dir(context)
            val file = File(folder, nameOf(url))
            val temp = File(folder, file.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) temp.delete()
            val trimNow = synchronized(this) { ++writes % TRIM_EVERY_WRITES == 0 }
            if (trimNow) trim(folder)
        } catch (e: Exception) {
            // ignore: the cache is optional
        }
    }

    private fun trim(folder: File) {
        val files = folder.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= TRIM_TO_BYTES) break
            total -= file.length()
            file.delete()
        }
    }
}
