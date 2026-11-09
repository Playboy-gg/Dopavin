package com.dopavin.data

import android.app.PendingIntent
import android.content.Context
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import com.dopavin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/** Songs shorter than this (ringtones, notification sounds, voice notes) are not shown. */
const val MIN_DURATION_MS = 10_000L

private const val CHANGE_DEBOUNCE_MS = 1_000L

/** Reads the music library from MediaStore. No custom scanner, no database. */
class MusicRepository(context: Context) {

    private val appContext = context.applicationContext
    private val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val unknownArtist = appContext.getString(R.string.unknown_artist)
    private val unknownAlbum = appContext.getString(R.string.unknown_album)
    private val unknownTitle = appContext.getString(R.string.unknown_title)

    suspend fun loadSongs(minDurationMs: Long = MIN_DURATION_MS): List<Song> =
        withContext(Dispatchers.IO) { query(minDurationMs) }

    /** Emits (debounced) whenever MediaStore audio content changes. */
    @OptIn(FlowPreview::class)
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        appContext.contentResolver.registerContentObserver(audioUri, true, observer)
        awaitClose { appContext.contentResolver.unregisterContentObserver(observer) }
    }.debounce(CHANGE_DEBOUNCE_MS)

    private fun isUnknown(value: String?): Boolean =
        value.isNullOrBlank() || value == MediaStore.UNKNOWN_STRING

    private fun query(minDurationMs: Long): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.SIZE,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())

        val songs = ArrayList<Song>()
        appContext.contentResolver.query(audioUri, projection, selection, selectionArgs, null)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val sizeCol = runCatching { cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE) }.getOrDefault(-1)

                while (cursor.moveToNext()) {
                    val rawAlbum = cursor.getString(albumCol)
                    val albumUnknown = isUnknown(rawAlbum)
                    val rawTitle = cursor.getString(titleCol)
                    val rawArtist = cursor.getString(artistCol)
                    songs.add(
                        Song(
                            id = cursor.getLong(idCol),
                            title = if (isUnknown(rawTitle)) unknownTitle else rawTitle,
                            artist = if (isUnknown(rawArtist)) unknownArtist else rawArtist,
                            album = if (albumUnknown) unknownAlbum else rawAlbum,
                            albumKey = if (albumUnknown) UNKNOWN_ALBUM_KEY else cursor.getLong(albumIdCol),
                            durationMs = cursor.getLong(durationCol),
                            dateAdded = cursor.getLong(addedCol),
                            // MediaStore encodes disc * 1000 + track.
                            track = cursor.getInt(trackCol) % 1000,
                            sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                        ),
                    )
                }
            }
        return songs
    }

    /**
     * F-105: the system's own delete consent request for the duplicate finder. Android 11+ asks
     * the user with a system dialog, so the app never needs write permission. Older Android
     * versions would need WRITE_EXTERNAL_STORAGE, which Dopavin deliberately never requests.
     */
    fun deletableRequest(ids: List<Long>): PendingIntent? {
        if (ids.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val uris = ids.map { ContentUris.withAppendedId(audioUri, it) }
            MediaStore.createDeleteRequest(appContext.contentResolver, uris)
        } catch (e: Exception) {
            null
        }
    }

    /** Free space on the shared storage volume that holds the music. 0 when it cannot be read. */
    fun sharedStorageFreeBytes(): Long = try {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        stat.availableBytes
    } catch (e: Exception) {
        0L
    }
}
