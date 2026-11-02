package com.dopavin.playback
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.dopavin.data.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
data class QueueItem(val mediaId: String, val title: String, val artist: String)
data class PlaybackUi(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val speed: Float = 1f,
) {
    val hasItem: Boolean get() = mediaId != null
}
sealed interface PlayerEvent {
    data class SongFailed(val title: String) : PlayerEvent
    data object AllFailed : PlayerEvent
}
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .build(),
        )
        .build()
class PlayerConnection(context: Context) {
    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var errorStreak = 0
    private var cachedQueue: List<QueueItem> = emptyList()
    private val _state = MutableStateFlow(PlaybackUi())
    val state: StateFlow<PlaybackUi> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()
    val isConnected: Boolean get() = controller != null
    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            this@PlayerConnection.controller = null
            future = null
            cachedQueue = emptyList()
            _state.value = PlaybackUi()
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val c = controller ?: return
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                c.playbackState == Player.STATE_READY
            ) {
                errorStreak = 0
            }
            refresh(c, rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            handleError(c)
        }
    }
    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token)
            .setListener(controllerListener)
            .buildAsync()
        future = f
        f.addListener(
            {
                val c = try {
                    f.get()
                } catch (e: Exception) {
                    null
                }
                if (c == null) {
                    future = null
                    pending.clear()
                } else {
                    controller = c
                    c.addListener(playerListener)
                    refresh(c, rebuildQueue = true)
                    val queued = ArrayList(pending)
                    pending.clear()
                    queued.forEach { it(c) }
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }
    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        pending.clear()
    }
    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending.add(block)
            connect()
        }
    }
    private fun refresh(c: MediaController, rebuildQueue: Boolean) {
        if (rebuildQueue) {
            cachedQueue = (0 until c.mediaItemCount).map { index ->
                val item = c.getMediaItemAt(index)
                QueueItem(
                    mediaId = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                )
            }
        }
        val item = c.currentMediaItem
        val duration = c.duration
        _state.value = PlaybackUi(
            connected = true,
            mediaId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = if (duration == C.TIME_UNSET || duration < 0) 0L else duration,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = cachedQueue,
            currentIndex = if (item == null) -1 else c.currentMediaItemIndex,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            speed = c.playbackParameters?.speed ?: 1f,
        )
    }
    private fun handleError(c: MediaController) {
        errorStreak++
        val title = c.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        val everythingFailed = errorStreak >= c.mediaItemCount
        if (everythingFailed || !c.hasNextMediaItem()) {
            c.stop()
            _events.tryEmit(if (everythingFailed) PlayerEvent.AllFailed else PlayerEvent.SongFailed(title))
            errorStreak = 0
        } else {
            _events.tryEmit(PlayerEvent.SongFailed(title))
            c.seekToNextMediaItem()
            c.prepare()
            c.play()
        }
    }
    fun position(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    fun isQueueEmpty(): Boolean = (controller?.mediaItemCount ?: 0) == 0
    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean?) = withController { c ->
        errorStreak = 0
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        if (c.shuffleModeEnabled && songs.size > 1) {
            val start = songs[startIndex.coerceIn(0, songs.lastIndex)]
            val rest = songs.filterIndexed { i, _ -> i != startIndex.coerceIn(0, songs.lastIndex) }.shuffled()
            unshuffled = songs.map { it.id.toString() }
            c.setMediaItems((listOf(start) + rest).map { it.toMediaItem() }, 0, 0L)
        } else {
            unshuffled = emptyList()
            c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        }
        c.prepare()
        c.play()
    }
    private var unshuffled: List<String> = emptyList()
    private fun reorderUpcoming(c: MediaController, order: (List<MediaItem>) -> List<MediaItem>) {
        val from = c.currentMediaItemIndex + 1
        val count = c.mediaItemCount
        if (c.currentMediaItemIndex < 0 || from >= count - 1) return
        val upcoming = (from until count).map { c.getMediaItemAt(it) }
        c.replaceMediaItems(from, count, order(upcoming))
    }
    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(song.toMediaItem()), 0, 0L)
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }
    fun addToQueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(song.toMediaItem()), 0, 0L)
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(song.toMediaItem())
        }
    }
    fun appendBatch(songs: List<Song>) = withController { c ->
        if (songs.isNotEmpty()) {
            if (c.mediaItemCount == 0) {
                c.setMediaItems(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
                c.prepare()
            } else {
                c.addMediaItems(songs.map { it.toMediaItem() })
            }
        }
    }
    fun jumpTo(index: Int) = withController { c ->
        c.seekToDefaultPosition(index)
        c.prepare()
        c.play()
    }
    fun removeAt(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }
    fun moveTo(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && to != from) {
            c.moveMediaItem(from, to)
        }
    }
    fun insertAt(index: Int, song: Song) = withController { c ->
        val target = index.coerceIn(0, c.mediaItemCount)
        c.addMediaItem(target, song.toMediaItem())
    }
    fun clearUpcoming(): Int {
        val c = controller ?: return 0
        val count = c.mediaItemCount
        val current = c.currentMediaItemIndex
        if (count <= 0 || current < 0 || current + 1 >= count) return 0
        val removed = count - current - 1
        c.removeMediaItems(current + 1, count)
        return removed
    }
    fun restore(songs: List<Song>, index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) =
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.repeatMode = repeatMode
            c.setMediaItems(songs.map { it.toMediaItem() }, index, positionMs)
            c.prepare()
        }
    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                Player.STATE_ENDED -> c.seekToDefaultPosition(0)
                else -> Unit
            }
            c.play()
        }
    }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun toggleShuffle() = withController { c ->
        val on = !c.shuffleModeEnabled
        c.shuffleModeEnabled = on
        if (on) {
            unshuffled = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
            reorderUpcoming(c) { it.shuffled() }
        } else {
            val position = HashMap<String, Int>()
            unshuffled.forEachIndexed { i, id -> position.putIfAbsent(id, i) }
            reorderUpcoming(c) { list -> list.sortedBy { position[it.mediaId] ?: Int.MAX_VALUE } }
            unshuffled = emptyList()
        }
    }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
