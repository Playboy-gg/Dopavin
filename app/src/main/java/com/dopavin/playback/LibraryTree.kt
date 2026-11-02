package com.dopavin.playback
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.dopavin.R
import com.dopavin.data.HistoryStore
import com.dopavin.data.OnlineRepository
import com.dopavin.data.PlaylistStore
import com.dopavin.data.SettingsStore
import com.dopavin.data.Song
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
class LibraryTree(context: Context) : MediaLibraryService.MediaLibrarySession.Callback {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val catalog = ConcurrentHashMap<String, Song>()
    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, app.getString(R.string.app_name)), params))
    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val song = catalog[mediaId]
        val item = when {
            song != null -> playable(song)
            mediaId == ROOT -> folder(ROOT, app.getString(R.string.app_name))
            mediaId == RECENT -> folder(RECENT, app.getString(R.string.auto_recent))
            mediaId == LIKED -> folder(LIKED, app.getString(R.string.auto_liked))
            mediaId == PLAYLISTS -> folder(PLAYLISTS, app.getString(R.string.auto_playlists))
            else -> null
        }
        return Futures.immediateFuture(
            if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
        )
    }
    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        worker.execute {
            try {
                val items = children(parentId)
                val from = (page * pageSize).coerceAtMost(items.size)
                val slice = if (pageSize > 0) items.subList(from, (from + pageSize).coerceAtMost(items.size)) else items
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(slice), params))
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
        Futures.immediateFuture(
            mediaItems.map { item -> catalog[item.mediaId]?.toMediaItem() ?: item }.toMutableList(),
        )
    private fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(RECENT, app.getString(R.string.auto_recent)),
            folder(LIKED, app.getString(R.string.auto_liked)),
            folder(PLAYLISTS, app.getString(R.string.auto_playlists)),
        )
        parentId == RECENT -> songs(HistoryStore(app).recent(RECENT_LIMIT))
        parentId == LIKED -> {
            val ids = SettingsStore(app).likedIds
            songs(OnlineRepository(app).cachedSongs().filter { it.id in ids }.sortedBy { it.title.lowercase() }.take(LIST_LIMIT))
        }
        parentId == PLAYLISTS -> runBlocking { PlaylistStore(app).loadAll() }.map { folder(PL_PREFIX + it.name, it.name) }
        parentId.startsWith(PL_PREFIX) ->
            songs(runBlocking { PlaylistStore(app).load(parentId.removePrefix(PL_PREFIX)) }?.songs.orEmpty().take(LIST_LIMIT))
        else -> emptyList()
    }
    private fun songs(list: List<Song>): List<MediaItem> = list.map { song ->
        catalog[song.id.toString()] = song
        playable(song)
    }
    private fun playable(song: Song): MediaItem {
        val item = song.toMediaItem()
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setIsBrowsable(false).setIsPlayable(true).build())
            .build()
    }
    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()
    private companion object {
        const val ROOT = "root"
        const val RECENT = "recent"
        const val LIKED = "liked"
        const val PLAYLISTS = "playlists"
        const val PL_PREFIX = "pl:"
        const val RECENT_LIMIT = 30
        const val LIST_LIMIT = 100
    }
}
