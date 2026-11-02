package com.dopavin.playback
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.dopavin.data.AccountStore
import com.dopavin.data.ListenBrainz
import kotlin.concurrent.thread
class Scrobbler(context: Context, private val player: Player) : Player.Listener {
    private val app = context.applicationContext
    private val accounts = AccountStore(app)
    private val handler = Handler(Looper.getMainLooper())
    private var item: MediaItem? = null
    private var startedAtSec = 0L
    private var playedMs = 0L
    private var lastTickAt = 0L
    private var counted = false
    private val tick = object : Runnable {
        override fun run() {
            accumulate()
            check()
            if (player.isPlaying) handler.postDelayed(this, TICK_MS)
        }
    }
    fun start() {
        player.addListener(this)
        begin(player.currentMediaItem)
        if (player.isPlaying) onIsPlayingChanged(true)
    }
    fun stop() {
        handler.removeCallbacks(tick)
        player.removeListener(this)
    }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        accumulate()
        check()
        begin(mediaItem)
        if (player.isPlaying) announce()
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(tick)
        if (isPlaying) {
            lastTickAt = SystemClock.elapsedRealtime()
            handler.postDelayed(tick, TICK_MS)
            if (playedMs == 0L) announce()
        } else {
            accumulate()
            check()
        }
    }
    private fun begin(mediaItem: MediaItem?) {
        item = mediaItem
        startedAtSec = System.currentTimeMillis() / 1000
        playedMs = 0L
        counted = false
        lastTickAt = SystemClock.elapsedRealtime()
    }
    private fun accumulate() {
        val now = SystemClock.elapsedRealtime()
        if (player.isPlaying) playedMs += (now - lastTickAt).coerceIn(0L, 5_000L)
        lastTickAt = now
    }
    private fun check() {
        if (counted || !accounts.scrobblingReady) return
        val current = item ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        if (!ListenBrainz.shouldScrobble(playedMs, duration)) return
        counted = true
        val meta = current.mediaMetadata
        val title = meta.title?.toString().orEmpty()
        val artist = meta.artist?.toString().orEmpty()
        if (title.isBlank() || artist.isBlank()) return
        val listen = ListenBrainz.listenItem(artist, title, meta.albumTitle?.toString().orEmpty(), startedAtSec, duration)
        sendFinished(listen)
    }
    private fun announce() {
        if (!accounts.scrobblingReady) return
        val meta = item?.mediaMetadata ?: return
        val title = meta.title?.toString().orEmpty()
        val artist = meta.artist?.toString().orEmpty()
        if (title.isBlank() || artist.isBlank()) return
        val body = ListenBrainz.body(
            "playing_now",
            listOf(ListenBrainz.listenItem(artist, title, meta.albumTitle?.toString().orEmpty(), null, 0L)),
        )
        val token = accounts.listenBrainzToken
        thread(name = "listenbrainz-now") { ListenBrainz.submit(token, body) }
    }
    private fun sendFinished(listen: String) {
        val token = accounts.listenBrainzToken
        thread(name = "listenbrainz-send") {
            synchronized(LOCK) {
                val queue = accounts.pendingListens + listen
                val result = ListenBrainz.submit(token, ListenBrainz.body("import", queue))
                accounts.pendingListens = if (result == ListenBrainz.Result.Ok) emptyList() else queue
            }
        }
    }
    private companion object {
        const val TICK_MS = 1_000L
        val LOCK = Any()
    }
}
