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
import kotlinx.coroutines.withContext
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

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(4 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val missing: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun keyOf(uri: Uri, sizePx: Int): String = "$uri@$sizePx"

    /** Returns a cached bitmap immediately, or null. Safe to call on the main thread. */
    fun peek(uri: Uri, sizePx: Int): Bitmap? = cache.get(keyOf(uri, sizePx))

    suspend fun load(context: Context, uri: Uri, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) { loadBlocking(context, uri, sizePx) }

    /** Blocking version. Never call on the main thread. */
    fun loadBlocking(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val key = keyOf(uri, sizePx)
        cache.get(key)?.let { return it }
        if (missing.contains(key)) return null

        val bitmap = try {
            decode(context.applicationContext, uri, sizePx)
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) cache.put(key, bitmap) else missing.add(key)
        return bitmap
    }

    private fun decode(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val scheme = uri.scheme?.lowercase() ?: return null
        return when (scheme) {
            "https", "http" -> decodeRemote(uri, sizePx)
            ONLINE_SCHEME -> null // online songs carry their artwork on [thumbUri], never on the marker uri
            "file" -> decodeFile(uri, sizePx)
            else -> decodeLocal(context, uri, sizePx)
        }
    }

    /** Decodes an HTTP thumbnail straight from YouTube's image CDN. */
    private fun decodeRemote(uri: Uri, sizePx: Int): Bitmap? {
        val url = uri.toString()
        // Big views try YouTube's 1280x720 frame first; it does not exist for every video.
        if (sizePx >= ArtworkSize.Large && url.contains("i.ytimg.com/vi/") && url.contains("/hqdefault.")) {
            fetchRemote(url.replace("/hqdefault.", "/maxresdefault."), sizePx)?.let { return it }
        }
        return fetchRemote(url, sizePx)
    }

    private fun fetchRemote(url: String, sizePx: Int): Bitmap? {
        val conn = URL(sharpUrl(url, sizePx)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = REMOTE_TIMEOUT_MS
            conn.readTimeout = REMOTE_TIMEOUT_MS
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) {
                sample *= 2
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            conn.disconnect()
        }
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
