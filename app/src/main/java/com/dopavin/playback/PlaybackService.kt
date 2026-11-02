package com.dopavin.playback
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.dopavin.MainActivity
import com.dopavin.data.ONLINE_SCHEME
import com.dopavin.data.OnlineRepository
import com.dopavin.data.DownloadStore
import com.dopavin.data.PlaybackStore
import com.dopavin.data.effectiveStreamQuality
import com.dopavin.data.SettingsStore
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private var scrobbler: Scrobbler? = null
    private var songFade: SongFade? = null
    private lateinit var store: PlaybackStore
    private var cachedIds: List<Long> = emptyList()
    private var noisyPauseAt = 0L
    private val noisyListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                noisyPauseAt = System.currentTimeMillis()
            } else if (playWhenReady) {
                noisyPauseAt = 0L
            }
        }
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val player = mediaSession?.player ?: return
            val output = addedDevices.any { it.isSink && it.type in RESUME_DEVICE_TYPES }
            if (!output || noisyPauseAt == 0L) return
            if (System.currentTimeMillis() - noisyPauseAt > RESUME_WINDOW_MS) return
            if (!SettingsStore(this@PlaybackService).load().resumeOnConnect) return
            if (player.mediaItemCount > 0 && !player.playWhenReady) {
                noisyPauseAt = 0L
                player.play()
            }
        }
    }
    private val saveListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                cachedIds = idsOf(player)
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                pushWidget(player)
            }
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                saveState(player)
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        store = PlaybackStore(this)
        val online = OnlineRepository(this)
        val downloads = DownloadStore.get(this)
        val settingsStore = SettingsStore(this)
        val resolver = ResolvingDataSource.Resolver { spec ->
            if (spec.uri.scheme == ONLINE_SCHEME) {
                val videoId = spec.uri.pathSegments.firstOrNull().orEmpty()
                val local = downloads.fileFor(videoId)
                val target = if (local != null) {
                    Uri.fromFile(local)
                } else if (com.dopavin.data.Subsonic.isServerSong(videoId)) {
                    val account = com.dopavin.data.AccountStore(this)
                    Uri.parse(
                        com.dopavin.data.Subsonic.streamUrl(
                            com.dopavin.data.SubsonicServer(account.subsonicUrl, account.subsonicUser, account.subsonicPassword),
                            videoId.removePrefix(com.dopavin.data.Subsonic.ID_PREFIX),
                        ),
                    )
                } else {
                    Uri.parse(online.resolveBlocking(videoId, settingsStore.load().effectiveStreamQuality(this)))
                }
                spec.buildUpon().setUri(target).build()
            } else {
                spec
            }
        }
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(STREAM_TIMEOUT_MS)
            .setReadTimeoutMs(STREAM_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http), resolver)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.UnshuffledShuffleOrder(0))
        player.addListener(saveListener)
        player.addListener(noisyListener)
        (getSystemService(AUDIO_SERVICE) as AudioManager)
            .registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        PlaybackRuntime.attach(this, player)
        scrobbler = Scrobbler(this, player).also { it.start() }
        songFade = SongFade(this, player).also { it.start() }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun seekToPrevious() {
                if (hasPreviousMediaItem()) seekToPreviousMediaItem() else seekTo(0L)
            }
        }
        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, LibraryTree(this))
            .setBitmapLoader(ArtworkBitmapLoader(this))
            .setSessionActivity(openApp)
            .build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession
    override fun onDestroy() {
        (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(deviceCallback)
        scrobbler?.stop()
        songFade?.stop()
        scrobbler = null
        songFade = null
        mediaSession?.run {
            player.removeListener(noisyListener)
            saveState(player)
            PlaybackRuntime.detach()
            player.removeListener(saveListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
    private fun pushWidget(player: Player) {
        val item = player.currentMediaItem
        val meta = item?.mediaMetadata
        NowPlayingWidget.push(
            this,
            title = meta?.title?.toString().orEmpty(),
            artist = meta?.artist?.toString().orEmpty(),
            artworkUri = meta?.artworkUri?.toString(),
            playing = player.isPlaying,
        )
    }
    private fun idsOf(player: Player): List<Long> =
        (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.toLongOrNull()
        }
    private fun saveState(player: Player) {
        if (player.mediaItemCount == 0) {
            store.save(emptyList(), -1L, 0L, player.shuffleModeEnabled, player.repeatMode)
            return
        }
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        store.save(
            ids = cachedIds,
            currentId = currentId,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }
    private companion object {
        const val STREAM_TIMEOUT_MS = 15_000
        const val RESUME_WINDOW_MS = 10 * 60 * 1000L
        val RESUME_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
