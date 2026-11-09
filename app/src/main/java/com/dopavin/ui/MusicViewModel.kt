package com.dopavin.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dopavin.R
import com.dopavin.data.Groups
import com.dopavin.data.HistoryStore
import com.dopavin.data.effectiveStreamQuality
import com.dopavin.data.SearchAssist
import com.dopavin.data.SmartQueue
import com.dopavin.data.SearchHistoryStore
import com.dopavin.data.HomeCache
import com.dopavin.data.HomeCategories
import com.dopavin.data.HomeCategorySpec
import com.dopavin.data.RegionDetector
import com.dopavin.data.ScriptLanguages
import com.dopavin.data.WelcomeStore
import com.dopavin.data.HomeSnapshot
import com.dopavin.data.LyricsOffsetStore
import com.dopavin.data.LyricsProviders
import com.dopavin.data.LyricsRepository
import com.dopavin.data.LyricsResult
import com.dopavin.data.LyricsStatus
import com.dopavin.data.LyricsUi
import com.dopavin.data.Mood
import com.dopavin.data.AppSettings
import com.dopavin.data.AudioQuality
import com.dopavin.data.DownloadCenter
import com.dopavin.data.DownloadStore
import com.dopavin.data.OnlineRepository
import com.dopavin.data.PlaybackStore
import com.dopavin.data.QueueOrigin
import com.dopavin.data.ReasonCode
import com.dopavin.data.TasteStore
import com.dopavin.data.AffinityMath
import androidx.media3.common.Player
import com.dopavin.data.SettingsStore
import com.dopavin.data.Subscription
import com.dopavin.data.SubscriptionFeed
import com.dopavin.data.SubscriptionStore
import com.dopavin.data.ThemeMode
import com.dopavin.data.Song
import com.dopavin.data.SortOrder
import com.dopavin.data.Visible
import com.dopavin.data.filterAlbums
import com.dopavin.data.filterArtists
import com.dopavin.data.filterSongs
import com.dopavin.data.groupAlbums
import com.dopavin.data.groupArtists
import com.dopavin.data.sortSongs
import com.dopavin.playback.EffectsState
import com.dopavin.playback.PlaybackRuntime
import com.dopavin.playback.PlaybackUi
import com.dopavin.playback.SleepState
import com.dopavin.playback.PlayerConnection
import com.dopavin.playback.PlayerEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import kotlin.random.Random
import com.dopavin.data.Playlist
import com.dopavin.data.PlaylistStore

/** The largest queue sent to the player in one go (see scope F-45). */
private const val MAX_QUEUE = 2000

/** YouTube search: how long typing settles before one request, and how short a query still is worth it. */
private const val ONLINE_DEBOUNCE_MS = 400L
private const val ONLINE_MIN_QUERY = 2

// Music Core tuning (owner spec §8.5/§8.4): one batch of generated picks when this much queue
// remains, a cooldown so empty results do not hammer anything, and batch sizes worth scrolling.
private const val AUTOPLAY_THRESHOLD = 3

/** Autoplay keeps about this many songs queued after the playing one; each top-up fills up to it. */
private const val AUTOPLAY_TARGET = 10
private const val AUTOPLAY_MIN_NEED = 5

/** After a successful top-up the next one may start this soon (an empty result still waits the full cooldown). */
private const val AUTOPLAY_AFTER_SUCCESS_MS = 5_000L
private const val MOOD_BATCH = 20
private const val RADIO_BATCH = 20
private const val PERSONAL_MIX_BATCH = 30

/** Mood retrieval is a search, not an audio measurement: its score never goes above this. */
private const val MOOD_RETRIEVAL_CAP = 0.6
private const val AUTOPLAY_COOLDOWN_MS = 25_000L
private const val SAMPLE_PERIOD_MS = 4_000L
private const val DAY_MS = 86_400_000.0

// F-97 Home: how many history cards sit on the shelf and how many played artists get a full section.
private const val HOME_HISTORY_ITEMS = 12
private const val HOME_ARTIST_SHELVES = 5
private const val HOME_TOP_ARTISTS = 12

/** How many recent plays are read to learn which languages the listener plays. */
private const val LEARN_HISTORY_ITEMS = 60
private const val WELCOME_CHOICES = 10
private const val WELCOME_MIN_CHOICES = 6
private const val WELCOME_MAX_NAME = 40
private const val HOME_TRENDING_ITEMS = 15
private const val HOME_TRENDING_QUERY = "top songs this week"

/** Online Home shelves younger than this are shown as they are, without a network refresh. */
private const val HOME_CACHE_TTL_MS = 30L * 60L * 1000L
private const val SUBS_TTL_MS = 30L * 60L * 1000L
private const val SUBS_PER_CHANNEL = 12
private const val SUBS_FEED_MAX = 60

/** Marks an online-list query as "uploads of this channel url" instead of a search phrase. */
const val CHANNEL_QUERY_PREFIX = "ch:"
const val PLAYLIST_QUERY_PREFIX = "pl:"

/** F-220: online lists of the owner's Subsonic server: "sub:random", "sub:starred", "sub:s:<search text>". */
const val SUBSONIC_QUERY_PREFIX = "sub:"
/** Opens YouTube's radio (mix) of one video id, see [OnlineRepository.radio]. */
const val RADIO_QUERY_PREFIX = "radio:"
const val HISTORY_QUERY = "hist:"
private const val HISTORY_LIST_ITEMS = 60

/** How many leading Home songs get their stream url resolved in advance (instant first tap). */
private const val PREFETCH_COUNT = 2

/** F-204: most songs taken from one Spotify list, so a huge playlist cannot run for minutes. */
private const val SPOTIFY_IMPORT_MAX = 100

/** F-221: most library playlists copied in one import. */
private const val YT_IMPORT_MAX_PLAYLISTS = 20

/** F-206: automatic update checks run at most this often. */
private const val UPDATE_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

/** The release tag the listener chose to skip on the update dialog (kept in the "update_check" prefs). */
private const val KEY_SKIPPED_UPDATE = "skipped_tag"

/** F-205: how many of the latest counted listens autoplay avoids repeating. */
private const val AUTOPLAY_FRESH_WINDOW = 60

class MusicViewModel(application: Application) : AndroidViewModel(application), MusicActions {

    private val downloadStore = DownloadStore.get(application)
    private val onlineRepository = OnlineRepository(application)
    private val historyStore = HistoryStore(application)
    private val listenLog = com.dopavin.data.ListenLog(application)
    // Declared before `init` on purpose: init starts the automatic update check, which reads these.
    private val updatePrefs = application.getSharedPreferences("update_check", Context.MODE_PRIVATE)
    private val _update = MutableStateFlow(com.dopavin.data.UpdateUi())
    val update: StateFlow<com.dopavin.data.UpdateUi> = _update.asStateFlow()
    /** True while the "Update available" dialog should be on screen. */
    private val _updateDialog = MutableStateFlow(false)
    val updateDialog: StateFlow<Boolean> = _updateDialog.asStateFlow()
    private var updateJob: Job? = null
    private var updateDownloadJob: Job? = null
    // F-217: accounts (ListenBrainz, Subsonic, YouTube). Above `init` like the update state, same reason.
    private val accountStore = com.dopavin.data.AccountStore(application)
    private val _accounts = MutableStateFlow(accountStore.snapshot())
    val accounts: StateFlow<com.dopavin.data.AccountsUi> = _accounts.asStateFlow()
    private var accountJob: Job? = null
    private var spotifyJob: Job? = null
    private val _recap = MutableStateFlow(emptyList<com.dopavin.data.ListenEntry>())
    val recap: StateFlow<List<com.dopavin.data.ListenEntry>> = _recap.asStateFlow()
    private val store = PlaybackStore(application)
    private val connection = PlayerConnection(application)
    private val settingsStore = SettingsStore(application)

    /** AndroidViewModel hides its Application getter behind a generic method, so Kotlin has no
     * usable `application` property on the supertype; screens-level strings come from this copy. */
    private val appContext: Context = application.applicationContext

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _liked = MutableStateFlow(settingsStore.likedIds)
    val liked: StateFlow<Set<Long>> = _liked.asStateFlow()

    override fun toggleLike(songId: Long) {
        val next = _liked.value.let { if (songId in it) it - songId else it + songId }
        _liked.value = next
        settingsStore.likedIds = next
        // F-115: a new like of a YouTube song can be kept offline automatically.
        if (songId in next && _settings.value.autoDownloadLiked) {
            (groups.value.byId[songId] ?: onlineRepository.peek(songId))?.let(::downloadSong)
        }
    }

    /** Equaliser state and sleep timer, owned by the playback service side. */
    val effects: StateFlow<EffectsState> = PlaybackRuntime.effects
    val sleep: StateFlow<SleepState> = PlaybackRuntime.sleep

    private val _view = MutableStateFlow(ViewState())
    val view: StateFlow<ViewState> = _view.asStateFlow()

    /** The library = the downloaded songs (F-110). Fed by [DownloadStore.songs]. */
    private val _songs = MutableStateFlow(downloadStore.songs.value)

    /** Albums, artists and an id lookup for the whole library. Recomputed only when the songs change. */
    val groups: StateFlow<Groups> = _songs
        .map { songs -> Groups(groupAlbums(songs), groupArtists(songs), songs.associateBy { it.id }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Groups())

    /** The whole library, songs sorted by the chosen order. Computed off the main thread. */
    val visible: StateFlow<Visible> = combine(
        _songs,
        groups,
        _view.map { it.sort }.distinctUntilChanged(),
    ) { songs, grouped, sort ->
        Visible(
            songs = sortSongs(songs, sort),
            albums = grouped.albums,
            artists = grouped.artists,
            totalSongs = songs.size,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Visible())

    /** Every liked song, downloaded or only played online, in the chosen sort order. */
    val favourites: StateFlow<List<Song>> = combine(
        _songs,
        _liked,
        _view.map { it.sort }.distinctUntilChanged(),
    ) { songs, liked, sort ->
        val pool = (songs + onlineRepository.cachedSongs()).distinctBy { it.id }
        sortSongs(pool.filter { it.id in liked }, sort)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Search results for the current query (empty lists while the query is blank). */
    val results: StateFlow<Visible> = combine(
        _songs,
        groups,
        _view.map { it.query }.distinctUntilChanged(),
    ) { songs, grouped, query ->
        if (query.isBlank()) {
            Visible(totalSongs = songs.size)
        } else {
            Visible(
                songs = sortSongs(filterSongs(songs, query), SortOrder.Title),
                albums = filterAlbums(grouped.albums, query),
                artists = filterArtists(grouped.artists, query),
                totalSongs = songs.size,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Visible())

    private val searchHistory = SearchHistoryStore(appContext)
    private val _recents = MutableStateFlow(searchHistory.all())

    /** Recent searches + "Did you mean" (only when the library has no match; names come from the library and recents). */
    @OptIn(FlowPreview::class)
    val searchUi: StateFlow<SearchUi> = combine(
        _recents,
        results,
        groups,
        _view.map { it.query.trim() }.distinctUntilChanged().debounce(250),
    ) { recents, res, grouped, query ->
        val suggestion = if (res.allEmpty && query.length >= 4) {
            val vocab = ArrayList<String>(grouped.byId.size * 2 + recents.size)
            grouped.byId.values.forEach { vocab += it.title; vocab += it.artist }
            grouped.albums.forEach { vocab += it.title }
            vocab += recents
            SearchAssist.didYouMean(query, vocab.distinct())
        } else {
            null
        }
        SearchUi(recents, suggestion, query)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUi())

    /** The song loaded in the player, whether it is a local file or an online row (owner F-80). */
    val currentSong: StateFlow<Song?> = combine(
        connection.state.map { it.mediaId?.toLongOrNull() },
        groups,
    ) { id, grouped ->
        id?.let { grouped.byId[it] } ?: id?.let { onlineRepository.peek(it) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val playback: StateFlow<PlaybackUi> = connection.state

    /** The YouTube part of Search (owner decision 2026-10-19). */

    private val _online = MutableStateFlow(OnlineUi())
    val online: StateFlow<OnlineUi> = _online.asStateFlow()

    /** Where the listener is and what their phone speaks (read from the phone, never asked). */
    private val regionProfile = RegionDetector.detect(application)

    /** The first-run "start with your artists" card. */
    private val welcomeStore = WelcomeStore(application)
    private val _welcomeDone = MutableStateFlow(welcomeStore.isDone())
    private val _pickedArtists = MutableStateFlow(welcomeStore.picked())

    /**
     * True only for a new listener on the first launch: onboarding was never finished AND there is no listening
     * history yet. Somebody who updates from an older version already has history and never sees it.
     */
    private val _onboarding = MutableStateFlow(
        !welcomeStore.isDone() && historyStore.topArtists(1).isEmpty() && historyStore.recent(1).isEmpty(),
    )
    val onboarding: StateFlow<Boolean> = _onboarding.asStateFlow()

    /** The online zone of Home: related to the last online play + shelves of the most-played artists. */
    private val homeCache = HomeCache(application).also { it.resetFor(regionProfile.key) }

    /** Local subscriptions (F-129): channel list + last merged feed, painted from disk at once. */
    private val subscriptionStore = SubscriptionStore(application)
    private val _subs = MutableStateFlow(
        SubsUi(
            channels = subscriptionStore.channels(),
            feed = subscriptionStore.feed()?.songs.orEmpty(),
        ),
    )
    val subs: StateFlow<SubsUi> = _subs.asStateFlow()

    /** Home starts from the last fetched shelves (cache), so a cold start never opens on an empty page. */
    private val _home = MutableStateFlow(initialHome())
    private val _avatars = MutableStateFlow<Map<String, String>>(emptyMap())

    val home: StateFlow<HomeUi> = combine(_home, _subs, _avatars, _welcomeDone) { h, s, av, welcomeDone ->
        h.copy(
            subFeed = s.feed,
            subCount = s.channels.size,
            avatars = av,
            welcomeArtists = if (welcomeDone || h.history.isNotEmpty() || h.topArtists.isNotEmpty()) {
                emptyList()
            } else {
                welcomeCandidates(h.trending)
            },
        )
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        _home.value.copy(subFeed = _subs.value.feed, subCount = _subs.value.channels.size),
    )

    /** The tiles for this phone and this listener; the played languages (read from song names) lead. */
    private fun currentCategories(): List<HomeCategorySpec> {
        val learned = ScriptLanguages.learn(historyStore.recent(LEARN_HISTORY_ITEMS).map { "${it.title} ${it.artist}" })
        return HomeCategories.plan(regionProfile, learned)
    }

    /** One song per different artist of the trending songs: the choices of the first-run card. */
    private fun welcomeCandidates(trending: List<Song>): List<Song> {
        val seen = HashSet<String>()
        val out = ArrayList<Song>()
        for (song in trending) {
            val name = song.artist.trim()
            if (name.isBlank() || name.length > WELCOME_MAX_NAME || !seen.add(name.lowercase())) continue
            out += song
            if (out.size == WELCOME_CHOICES) break
        }
        return if (out.size >= WELCOME_MIN_CHOICES) out else emptyList()
    }

    override fun finishWelcome(picked: List<String>) {
        welcomeStore.finish(picked)
        _pickedArtists.value = welcomeStore.picked()
        _welcomeDone.value = true
        _onboarding.value = false
        // The picks now seed the artist shelves, so fetch them at once instead of waiting for the cache to age.
        homeRefresh.value++
    }

    /** Rising counter: new history re-reads the local play log (cheap). */
    private val homeRequests = MutableStateFlow(0L)

    /** Rising counter: an explicit refresh re-fetches the online shelves even when the cache is fresh. */
    private val homeRefresh = MutableStateFlow(0L)

    /** What the S-10 online list screen shows (a category or an artist shelf). */
    private val _onlineList = MutableStateFlow(OnlineListUi())
    val onlineList: StateFlow<OnlineListUi> = _onlineList.asStateFlow()

    // ---- Music Core (owner spec, 2026-10-20): moods, radio, autoplay, feedback ----

    private val tasteStore = TasteStore(appContext)

    // ---- F-103: playlists ----

    private val playlistStore = PlaylistStore(appContext)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()

    /** Refreshes the playlist catalogue off the main thread. Cheap: one small JSON file. */
    private fun refreshPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _playlists.value = playlistStore.loadAll()
        }
    }

    /** Resolves queue mediaIds (or playlist snapshots) back to play Songs; misses are dropped. */
    private fun resolveSongs(ids: List<String>): List<Song> {
        val byLocal = _songs.value.associateBy { it.id }
        val byOnline = onlineRepository.cachedSongs().associateBy { it.id }
        return ids.mapNotNull { id -> id.toLongOrNull()?.let { byLocal[it] ?: byOnline[it] } }
    }

    // ---- F-98: the shared song action menu's targets ----

    private val _detailSong = MutableStateFlow<Song?>(null)
    val detailSong: StateFlow<Song?> = _detailSong.asStateFlow()

    // ---- F-102: undo of the last queue removal (in-memory, short-lived by design) ----

    data class QueueRemoval(val song: Song, val index: Int)

    private var lastQueueRemoval: QueueRemoval? = null

    // ---- Downloads (F-111): engine state + the storage numbers ----

    /** Active downloads and the ids of finished ones, as the screens need them. */
    val downloads: StateFlow<DownloadsUi> = combine(
        DownloadCenter.items,
        downloadStore.songs,
    ) { active, saved -> DownloadsUi(active, saved.mapNotNull { it.videoId }.toSet()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DownloadsUi())

    data class StorageUi(
        val downloadCount: Int = 0,
        val downloadBytes: Long = 0L,
        val appDataBytes: Long = 0L,
        /** F-193: size of the cached lyrics (part of [appDataBytes]). */
        val lyricsBytes: Long = 0L,
        val freeBytes: Long = 0L,
    )

    private val _storage = MutableStateFlow(StorageUi())
    val storage: StateFlow<StorageUi> = _storage.asStateFlow()

    /** "Save to device": the song waiting for the system file saver (MainActivity launches it). */
    private val _pendingSave = MutableStateFlow<Song?>(null)
    val pendingSave: StateFlow<Song?> = _pendingSave.asStateFlow()

    fun clearPendingSave() {
        _pendingSave.value = null
    }

    /** Backup export / import: which system file dialog MainActivity should open (null = none). */
    private val _backupRequest = MutableStateFlow<BackupRequest?>(null)
    val backupRequest: StateFlow<BackupRequest?> = _backupRequest.asStateFlow()

    fun clearBackupRequest() {
        _backupRequest.value = null
    }

    override fun requestBackup(export: Boolean) {
        _backupRequest.value = if (export) BackupRequest.Export else BackupRequest.Import
    }

    fun onBackupExportTarget(uri: android.net.Uri?) {
        _backupRequest.value = null
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val playlists = playlistStore.loadAll().associate { it.name to it.songs }
                val text = com.dopavin.data.Backup.encode(_liked.value, playlists, settingsStore.exportPrefs())
                appContext.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) } != null
            } catch (e: Exception) {
                false
            }
            _messages.tryEmit(UiMessage(if (ok) R.string.backup_exported else R.string.backup_failed))
        }
    }

    fun onBackupImportSource(uri: android.net.Uri?) {
        _backupRequest.value = null
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val content = try {
                appContext.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                    ?.let { com.dopavin.data.Backup.decode(it) }
            } catch (e: Exception) {
                null
            }
            if (content == null) {
                _messages.tryEmit(UiMessage(R.string.backup_invalid))
                return@launch
            }
            val existing = playlistStore.loadAll().map { it.name }.toSet()
            var added = 0
            for ((name, songs) in content.playlists) {
                if (name !in existing && playlistStore.create(name, songs)) added++
            }
            val mergedLikes = _liked.value + content.likedIds
            _liked.value = mergedLikes
            settingsStore.likedIds = mergedLikes
            content.prefs?.let { settingsStore.importPrefs(it) }
            _settings.value = settingsStore.load()
            val restored = _settings.value
            PlaybackRuntime.applyPauseOnUnplug(restored.pauseOnUnplug)
            PlaybackRuntime.applyKeepPitch(restored.keepPitch)
            PlaybackRuntime.applySleepFade(restored.sleepFade)
            PlaybackRuntime.applySkipSilence(restored.skipSilence)
            _playlists.value = playlistStore.loadAll()
            _messages.tryEmit(UiMessage(R.string.backup_imported, listOf(content.likedIds.size, added)))
        }
    }


    /** The generated batch behind S-11 (a mood or a radio), fresh per screen. */
    private val _suggestions = MutableStateFlow(SuggestionsUi())
    val suggestions: StateFlow<SuggestionsUi> = _suggestions.asStateFlow()

    /** D-27: why each mediaId is in the queue (session-only provenance). */
    private val _origins = MutableStateFlow<Map<String, QueueOrigin>>(emptyMap())

    /** F-102: why each song entered the queue this session ("You" vs "Auto"), for the queue chips. */
    val origins: StateFlow<Map<String, QueueOrigin>> = _origins.asStateFlow()
    private var generationCounter = 0L

    /** One autoplay extension at a time, with a cooldown between attempts. */
    private var autoplayBusy = false
    private var lastAutoplayAttemptAt = 0L

    /** Feedback capture (§7.5): the previous queue entry and its last position sample. */
    private var previousFeedback: Pair<String, Song>? = null
    private var lastSample: Pair<String, Long>? = null
    private var lastEventWasFailure = false

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var restoreDone = false

    init {
        connection.connect()

        // The library follows the downloads: a finished or deleted download changes the lists.
        viewModelScope.launch {
            downloadStore.songs.collect { songs ->
                _songs.value = songs
                tryRestore()
                // The Storage screen shows live numbers while a download finishes or is deleted.
                if (_view.value.route == Route.Storage) {
                    withContext(Dispatchers.IO) { refreshStorage() }
                }
            }
        }

        viewModelScope.launch {
            connection.events.collect { event ->
                val message = when (event) {
                    is PlayerEvent.SongFailed -> {
                        lastEventWasFailure = true
                        UiMessage(R.string.msg_song_failed, listOf(event.title))
                    }
                    PlayerEvent.AllFailed -> UiMessage(R.string.msg_all_failed)
                }
                _messages.tryEmit(message)
            }
        }
        viewModelScope.launch {
            connection.state.map { it.connected }.distinctUntilChanged().collect { connected ->
                if (connected) tryRestore()
            }
        }
        // If the queue becomes empty (last item removed), Now Playing and Queue have nothing to show.
        viewModelScope.launch {
            connection.state.map { it.connected && it.queue.isEmpty() }.distinctUntilChanged()
                .collect { emptyQueue -> if (emptyQueue) closePlayerScreens() }
        }

        observeOnlineSearch()
        observeHome()
        loadSubscriptions(force = false)
        observeAutoplay()
        autoCheckForUpdates()
        refreshPlaylists()

        // Position sampler: `PlaybackUi` has no position, so completion/skip decisions read the
        // freshest polled sample (owner spec §7.5). Cheap: one read every few seconds.
        viewModelScope.launch {
            while (true) {
                delay(SAMPLE_PERIOD_MS)
                val id = connection.state.value.mediaId
                if (id != null) lastSample = id to connection.position()
            }
        }

        // History (F-86) + feedback (§7.5): every queue transition writes both stores.
        viewModelScope.launch {
            currentSong.collect { song ->
                val key = song?.let { identityOf(it) }
                val previous = previousFeedback
                if (previous != null && previous.first != key) {
                    judgeLeft(previous.second, previous.first)
                    if (song != null) tasteStore.event(song, TasteStore.Event.Started)
                }
                previousFeedback = song?.let { identityOf(it) to it }
                if (song != null && historyStore.record(song)) {
                    homeRequests.value++
                }
                lastEventWasFailure = false
            }
        }
    }

    /** Why the queue entry that just ended deserves: completion is a win, an abrupt stop with a
     * failing stream counts as neither, everything else is a soft skip (§7.5). */
    private fun judgeLeft(song: Song, key: String) {
        val playedMs = lastSample?.takeIf { it.first == key }?.second ?: 0L
        val duration = song.durationMs
        val event = when {
            lastEventWasFailure -> TasteStore.Event.Started
            duration > 0 && playedMs >= duration * 9 / 10 -> TasteStore.Event.Completed
            else -> TasteStore.Event.Skipped
        }
        tasteStore.event(song, event)
        // F-190: a listen is counted when the song is left after enough playing time.
        if (!lastEventWasFailure && listenLog.record(song, playedMs)) _recap.value = listenLog.all()
    }

    private fun identityOf(song: Song): String = song.id.toString()

    /** YouTube side of Search: debounced so typing does not fire a request per keystroke (F-80). */
    @OptIn(FlowPreview::class)
    /** Bumped by Retry so the same search runs again. */
    private val searchRetry = MutableStateFlow(0)
    private var searchCursor: OnlineRepository.SearchCursor? = null
    private var moreJob: Job? = null

    override fun loadMoreOnline() {
        val cursor = searchCursor ?: return
        if (_online.value.loadingMore || moreJob?.isActive == true) return
        _online.value = _online.value.copy(loadingMore = true)
        moreJob = viewModelScope.launch {
            val page = try {
                onlineRepository.searchMore(cursor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // A newer search replaced the list meanwhile: this page belongs to the old one.
            if (searchCursor !== cursor) return@launch
            val current = _online.value
            if (page == null) {
                // Keep the cursor so the same button retries.
                _online.value = current.copy(loadingMore = false)
                return@launch
            }
            val known = current.songs.mapTo(HashSet()) { it.id }
            searchCursor = page.more
            _online.value = current.copy(
                songs = current.songs + page.songs.filter { it.id !in known },
                hasMore = page.more != null,
                loadingMore = false,
            )
        }
    }

    /** "Try again" on a failed online screen: runs the same request again for the screen that is open. */
    override fun retryOnline() {
        when (val r = _view.value.route) {
            is Route.OnlineList -> {
                _onlineList.value = OnlineListUi(r.title, r.query, OnlineStage.Loading)
                loadOnlineList(r.title, r.query)
            }
            is Route.ArtistProfile -> {
                _artist.value = _artist.value.copy(stage = OnlineStage.Loading)
                loadArtist(r.name, r.url)
            }
            is Route.MoodQueue -> {
                _suggestions.value = SuggestionsUi(stage = OnlineStage.Loading, origin = QueueOrigin.MOOD_QUEUE)
                loadMood(r.mood)
            }
            Route.PersonalMix -> {
                _suggestions.value = SuggestionsUi(stage = OnlineStage.Loading, origin = QueueOrigin.PERSONAL_MIX)
                loadPersonalMix()
            }
            is Route.SongRadio -> lastRadioSeed?.let {
                _suggestions.value = SuggestionsUi(stage = OnlineStage.Loading, origin = QueueOrigin.SONG_RADIO)
                loadRadio(it)
            }
            else -> searchRetry.value++
        }
    }

    private fun observeOnlineSearch() {
        viewModelScope.launch {
            combine(
                _view.map { it.query.trim() }.distinctUntilChanged().debounce(ONLINE_DEBOUNCE_MS),
                _settings.map { it.onlinePlayback }.distinctUntilChanged(),
                searchRetry,
            ) { query, allow, _ -> query to allow }
                .collectLatest { (query, allow) ->
                    when {
                        !allow -> { searchCursor = null; _online.value = OnlineUi(OnlineStage.Off) }
                        query.length < ONLINE_MIN_QUERY -> { searchCursor = null; _online.value = OnlineUi(OnlineStage.Idle) }
                        else -> {
                            _online.value = _online.value.copy(stage = OnlineStage.Loading)
                            val artistsJob = viewModelScope.async { onlineRepository.searchArtists(query) }
                            searchCursor = null
                            val page = try {
                                onlineRepository.searchPage(query)
                            } catch (e: CancellationException) {
                                artistsJob.cancel()
                                throw e
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                null
                            }
                            val artists = try {
                                artistsJob.await()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                emptyList()
                            }
                            currentCoroutineContext().ensureActive()
                            searchCursor = page?.more
                            _online.value = if (page == null && artists.isEmpty()) {
                                OnlineUi(OnlineStage.Unreachable)
                            } else {
                                OnlineUi(OnlineStage.Ready, page?.songs.orEmpty(), artists, hasMore = page?.more != null)
                            }
                        }
                    }
                }
        }
    }

    /** Builds the online Home zone (F-87): one related walk + the top-3 artists as searches.
     * Per-shelf failures leave that shelf empty; nothing here ever surfaces an error. */
    private fun observeHome() {
        // History is local and cheap, so it stays live: every new play refreshes it at once.
        viewModelScope.launch {
            homeRequests.collect {
                val history = withContext(Dispatchers.Default) { historyStore.recent(HOME_HISTORY_ITEMS) }
                val top = withContext(Dispatchers.Default) { historyStore.topArtistSongs(HOME_TOP_ARTISTS) }
                val categories = withContext(Dispatchers.Default) { currentCategories() }
                _home.update { it.copy(history = history, topArtists = top, categories = categories) }
                loadArtistAvatars(top)
            }
        }
        // The online shelves follow a stale-while-revalidate rule: paint the cache, refresh only
        // when it is older than HOME_CACHE_TTL_MS, on pull/refresh, or when the toggle changes.
        viewModelScope.launch {
            combine(
                _settings.map { it.onlinePlayback }.distinctUntilChanged(),
                homeRefresh,
            ) { allow, tick -> allow to tick }
                .collectLatest { (allow, tick) ->
                    if (!allow) {
                        val history = withContext(Dispatchers.Default) { historyStore.recent(HOME_HISTORY_ITEMS) }
                        val top = withContext(Dispatchers.Default) { historyStore.topArtistSongs(HOME_TOP_ARTISTS) }
                        _home.value = HomeUi(history = history, topArtists = top, categories = currentCategories())
                        loadArtistAvatars(top)
                        return@collectLatest
                    }
                    val cached = withContext(Dispatchers.IO) { homeCache.load() }
                    if (cached != null && _home.value.trending.isEmpty() && _home.value.artists.isEmpty()) {
                        _home.update { applySnapshot(it, cached) }
                    }
                    prefetchTop()
                    val fresh = cached != null && tick == 0L &&
                        System.currentTimeMillis() - cached.savedAt < HOME_CACHE_TTL_MS
                    if (fresh) {
                        _home.update { it.copy(active = true, loading = false) }
                        return@collectLatest
                    }
                    val empty = _home.value.trending.isEmpty() && _home.value.relatedSongs.isEmpty() &&
                        _home.value.artists.isEmpty()
                    _home.update { it.copy(active = true, loading = empty) }
                    fetchHome()
                }
        }
    }

    /** Fetches every online shelf in parallel; a shelf that comes back empty keeps its previous content. */
    private suspend fun fetchHome() = coroutineScope {
        val lastOnline = withContext(Dispatchers.Default) { historyStore.lastOnlineSong() }
        val topArtists = withContext(Dispatchers.Default) {
            // Real plays first; the artists picked on the first-run card fill the rest (they are all there is
            // for a new listener).
            (historyStore.topArtists(HOME_ARTIST_SHELVES) + _pickedArtists.value)
                .distinctBy { it.lowercase() }
                .take(HOME_ARTIST_SHELVES)
        }
        val trendingJob = async { safeOnline { onlineRepository.search(HOME_TRENDING_QUERY, HOME_TRENDING_ITEMS) } }
        val relatedJob = async {
            lastOnline?.videoId?.let { videoId -> safeOnline { onlineRepository.related(videoId) } }.orEmpty()
        }
        val artistJobs = topArtists.map { name ->
            async { HomeArtistShelf(name, safeOnline { onlineRepository.search(name, 8) }) }
        }
        val trending = trendingJob.await()
        if (trending.isNotEmpty()) {
            _home.update { it.copy(trending = trending, active = true, loading = false) }
            prefetchTop()
        }
        val related = relatedJob.await()
        val artists = artistJobs.map { it.await() }.filter { it.songs.isNotEmpty() }
        _home.update { current ->
            current.copy(
                active = true,
                loading = false,
                relatedTitle = if (related.isNotEmpty()) lastOnline?.title.orEmpty() else current.relatedTitle,
                relatedSongs = related.ifEmpty { current.relatedSongs },
                artists = artists.ifEmpty { current.artists },
            )
        }
        val now = _home.value
        if (now.trending.isNotEmpty() || now.artists.isNotEmpty() || now.relatedSongs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                homeCache.save(
                    HomeSnapshot(
                        savedAt = System.currentTimeMillis(),
                        trending = now.trending,
                        relatedTitle = now.relatedTitle,
                        related = now.relatedSongs,
                        artists = now.artists.map { it.name to it.songs },
                    ),
                )
            }
        }
        Unit
    }

    private fun applySnapshot(base: HomeUi, snapshot: HomeSnapshot): HomeUi = base.copy(
        active = true,
        loading = false,
        trending = snapshot.trending,
        relatedTitle = snapshot.relatedTitle,
        relatedSongs = snapshot.related,
        artists = snapshot.artists.map { (name, songs) -> HomeArtistShelf(name, songs) },
    )

    /** The Home state painted before the first frame: cached shelves when YouTube is on, else empty. */
    private fun initialHome(): HomeUi {
        val categories = currentCategories()
        if (!_settings.value.onlinePlayback) return HomeUi(categories = categories)
        val cached = homeCache.load() ?: return HomeUi(active = true, loading = true, categories = categories)
        return applySnapshot(HomeUi(categories = categories), cached)
    }

    /** Resolves the first few Home songs' stream urls in the background so the first tap is instant. */
    private fun prefetchTop() {
        val quality = _settings.value.effectiveStreamQuality(appContext)
        val ids = (_home.value.trending + _home.value.relatedSongs)
            .mapNotNull { it.videoId }
            .filterNot { downloadStore.isDownloaded(it) }
            .take(PREFETCH_COUNT)
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { ids.forEach { onlineRepository.prefetch(it, quality) } }
    }

    /** Runs [block]; cancelled runs rethrow, every other failure is an empty list. */
    private suspend fun <T> safeCall(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun safeOnline(block: suspend () -> List<Song>): List<Song> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun safeOnlineListing(block: suspend () -> OnlineRepository.Listing): OnlineRepository.Listing = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        OnlineRepository.Listing(emptyList(), null)
    }

    // ---- S-10 online list (a Home shelf opened in full) ----

    override fun openOnlineList(title: String, query: String) {
        _onlineList.value = OnlineListUi(title, query, OnlineStage.Loading)
        _view.update { it.copy(stack = it.stack + Route.OnlineList(title, query)) }
        loadOnlineList(title, query)
    }

    private fun loadOnlineList(title: String, query: String) {
        viewModelScope.launch {
            var cover: String? = null
            val songs = if (query.startsWith(CHANNEL_QUERY_PREFIX)) {
                safeOnline { onlineRepository.channelUploads(query.removePrefix(CHANNEL_QUERY_PREFIX), 30, title) }
            } else if (query.startsWith(RADIO_QUERY_PREFIX)) {
                val listing = safeOnlineListing { onlineRepository.radio(query.removePrefix(RADIO_QUERY_PREFIX), 50) }
                cover = listing.cover
                listing.songs
            } else if (query.startsWith(PLAYLIST_QUERY_PREFIX)) {
                val listing = safeOnlineListing { onlineRepository.playlist(query.removePrefix(PLAYLIST_QUERY_PREFIX), 80, title) }
                cover = listing.cover
                listing.songs
            } else if (query == HISTORY_QUERY) {
                withContext(Dispatchers.Default) { historyStore.recent(HISTORY_LIST_ITEMS) }
            } else if (query.startsWith(SUBSONIC_QUERY_PREFIX)) {
                withContext(Dispatchers.IO) { subsonicSongs(query.removePrefix(SUBSONIC_QUERY_PREFIX)) }
            } else if (query == com.dopavin.data.YouTubeAccount.LIKED_QUERY) {
                withContext(Dispatchers.IO) {
                    com.dopavin.data.YouTubeAccount.likedSongs(accountStore.youTubeCookie).orEmpty().map { ytSong(it) }
                        .also { onlineRepository.remember(it) }
                }
            } else {
                safeOnline { onlineRepository.search(query, 25) }
            }
            // A newer open (or the shared search) may have replaced this meanwhile; then stand down.
            if (_onlineList.value.query != query) return@launch
            _onlineList.value = if (songs.isEmpty()) {
                OnlineListUi(title, query, OnlineStage.Unreachable)
            } else {
                OnlineListUi(title, query, OnlineStage.Ready, songs, cover)
            }
        }
    }

    override fun refreshHome() {
        homeRefresh.value++
        loadSubscriptions(force = true)
    }

    // ---- Subscriptions (F-129) ----

    private var subsJob: kotlinx.coroutines.Job? = null

    private val _artist = MutableStateFlow(ArtistUi())
    val artist: StateFlow<ArtistUi> = _artist.asStateFlow()

    private var artistJob: Job? = null
    private var avatarJob: Job? = null

    /** Real profile pictures of the top artists (NewPipe): remembered ones at once, the rest one by one. */
    private fun loadArtistAvatars(top: List<Song>) {
        if (!_settings.value.onlinePlayback && top.none { onlineRepository.peekIdentity(it.artist) != null }) return
        avatarJob?.cancel()
        avatarJob = viewModelScope.launch {
            for (song in top) {
                val key = song.artist.trim().lowercase()
                if (_avatars.value.containsKey(key)) continue
                val identity = onlineRepository.peekIdentity(song.artist)
                    ?: if (_settings.value.onlinePlayback) {
                        safeCall { onlineRepository.artistIdentity(song.artist, song.channelUrl) }
                    } else null
                val avatar = identity?.avatar ?: continue
                _avatars.update { it + (key to avatar) }
            }
        }
    }

    /**
     * Opens the artist profile. [url] may be blank (a name from a downloaded file): the channel is
     * then looked up by name. With no network the page falls back to the artist's local songs.
     */
    override fun openArtistProfile(name: String, url: String) {
        val known = _subs.value.channels.firstOrNull { it.url == url }
        val remembered = onlineRepository.peekIdentity(name)
        _artist.value = ArtistUi(
            name = name,
            url = url.ifBlank { remembered?.channelUrl.orEmpty() },
            avatar = known?.avatar ?: remembered?.avatar,
            stage = OnlineStage.Loading,
        )
        _view.update { it.copy(stack = it.stack + Route.ArtistProfile(name, url)) }
        loadArtist(name, url)
    }

    private fun loadArtist(name: String, url: String) {
        val known = _subs.value.channels.firstOrNull { it.url == url }
        artistJob?.cancel()
        artistJob = viewModelScope.launch {
            val identity = if (url.isBlank()) safeCall { onlineRepository.artistIdentity(name) } else null
            val channel = url.ifBlank { identity?.channelUrl.orEmpty() }
            val page = if (channel.isBlank()) null else safeCall { onlineRepository.artistPage(channel, name) }
            val local = groups.value.artistsByKey[name.trim().lowercase()]?.songs.orEmpty()
            _artist.value = if (page == null || (page.songs.isEmpty() && page.albums.isEmpty())) {
                if (local.isNotEmpty()) {
                    _artist.value.copy(url = channel, avatar = _artist.value.avatar ?: identity?.avatar,
                        stage = OnlineStage.Ready, songs = local)
                } else {
                    _artist.value.copy(url = channel, avatar = _artist.value.avatar ?: identity?.avatar,
                        stage = OnlineStage.Unreachable)
                }
            } else {
                ArtistUi(
                    name = page.name.ifBlank { name },
                    url = channel,
                    avatar = page.avatar ?: identity?.avatar ?: known?.avatar,
                    stage = OnlineStage.Ready,
                    songs = page.songs,
                    albums = page.albums,
                )
            }
        }
    }

    override fun openDownloads() {
        _view.update { it.copy(stack = it.stack + Route.Downloads) }
    }

    override fun openFavourites() {
        _view.update { it.copy(stack = it.stack + Route.Favourites) }
    }

    override fun openHistory() {
        openOnlineList(getApplication<Application>().getString(R.string.home_history), HISTORY_QUERY)
    }

    override fun openSubscriptions() {
        _view.update { it.copy(stack = it.stack + Route.Subscriptions) }
        loadSubscriptions(force = false)
    }

    override fun refreshSubscriptions() = loadSubscriptions(force = true)

    /** Re-reads every channel's latest uploads in parallel and merges them round-robin (newest of
     * each channel first); skipped when the cached feed is fresh unless [force]. */
    private fun loadSubscriptions(force: Boolean) {
        val channels = _subs.value.channels
        if (channels.isEmpty() || !_settings.value.onlinePlayback) return
        val saved = subscriptionStore.feed()
        if (!force && saved != null && System.currentTimeMillis() - saved.savedAt < SUBS_TTL_MS) return
        subsJob?.cancel()
        subsJob = viewModelScope.launch {
            _subs.update { it.copy(loading = true) }
            val lists = coroutineScope {
                channels.map { ch ->
                    async { safeOnline { onlineRepository.channelUploads(ch.url, SUBS_PER_CHANNEL, ch.name) } }
                }.map { it.await() }
            }
            val merged = ArrayList<Song>()
            var round = 0
            while (merged.size < SUBS_FEED_MAX && lists.any { round < it.size }) {
                lists.forEach { list ->
                    list.getOrNull(round)?.let { song -> if (merged.none { it.id == song.id }) merged += song }
                }
                round++
            }
            val feed = merged.take(SUBS_FEED_MAX)
            if (feed.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    subscriptionStore.saveFeed(SubscriptionFeed(System.currentTimeMillis(), feed))
                }
            }
            _subs.update { it.copy(feed = feed.ifEmpty { it.feed }, loading = false) }
        }
    }

    private fun setChannels(list: List<Subscription>) {
        _subs.update { state ->
            val urls = list.map { it.url }.toSet()
            state.copy(channels = list, feed = state.feed.filter { it.channelUrl == null || it.channelUrl in urls })
        }
        viewModelScope.launch(Dispatchers.IO) { subscriptionStore.saveChannels(list) }
    }

    override fun toggleSubscription(song: Song) {
        val url = song.channelUrl ?: return
        toggleChannelSubscription(url, song.artist)
    }

    override fun toggleChannelSubscription(url: String, name: String) {
        if (_subs.value.channels.any { it.url == url }) {
            unsubscribe(url)
            return
        }
        val shownName = name.ifBlank { url }
        setChannels(_subs.value.channels + Subscription(url, shownName, _artist.value.avatar.takeIf { _artist.value.url == url }))
        _messages.tryEmit(UiMessage(R.string.sub_added, listOf(shownName)))
        viewModelScope.launch {
            // Fill in the channel's real name and avatar, then fetch its uploads.
            val info = onlineRepository.channelInfo(url)
            if (info != null) {
                setChannels(
                    _subs.value.channels.map {
                        if (it.url == url) it.copy(name = info.name, avatar = info.avatar) else it
                    },
                )
            }
            loadSubscriptions(force = true)
        }
    }

    override fun unsubscribe(url: String) {
        val name = _subs.value.channels.firstOrNull { it.url == url }?.name.orEmpty()
        setChannels(_subs.value.channels.filterNot { it.url == url })
        _messages.tryEmit(UiMessage(R.string.sub_removed, listOf(name)))
    }

    // ---- Music Core: scorer feature assembly + Smart Autoplay + S-11 generation ----

    /**
     * The one way every generator (autoplay, mood, radio, personal mix) turns a song into a pipeline
     * candidate. Missing data stays null: an unknown song gets no taste, no novelty reward.
     */
    private fun cand(
        song: Song,
        ctx: ScoringContext,
        related: Int? = null,
        earlier: Int? = null,
        newest: Int? = null,
        mood: Double? = null,
    ): SmartQueue.Cand<Song> {
        val t = tasteStore.taste(song)
        val artist = song.artist.trim().lowercase()
        val liked = song.id in ctx.likedIds
        return SmartQueue.Cand(
            item = song, title = song.title, artist = song.artist, durationMs = song.durationMs,
            relatedRank = related, earlierRank = earlier, newestRank = newest,
            sameArtist = ctx.currentArtist.isNotEmpty() && artist == ctx.currentArtist,
            moodMatch = mood,
            taste = AffinityMath.taste(t.plays, t.completes, t.skips, t.lastAt, liked, ctx.now),
            favouriteArtist = artist in ctx.topArtists,
            rediscovery = AffinityMath.rediscovery(t.completes, t.skips, t.lastAt, ctx.now),
            heardRecently = (song.videoId ?: "l${song.id}") in ctx.recentlyHeard,
            blocked = t.blocked,
        )
    }

    /** Context every generation needs: taste, likes, what is queued and what was heard lately. */
    private fun scoringContext(): ScoringContext {
        val now = System.currentTimeMillis()
        val state = connection.state.value
        val listens = listenLog.all()
        val top = if (listens.isNotEmpty()) {
            AffinityMath.topArtists(listens.map { it.artist to it.at }, now, 5)
        } else {
            historyStore.topArtists(5).map { it.trim().lowercase() }
        }
        return ScoringContext(
            now = now,
            likedIds = _liked.value,
            topArtists = top.toSet(),
            queuedIds = state.queue.mapNotNull { it.mediaId.toLongOrNull() }.toSet(),
            currentArtist = state.artist.trim().lowercase(),
            recentlyHeard = if (_settings.value.autoplayFresh) listens.takeLast(AUTOPLAY_FRESH_WINDOW).mapTo(HashSet()) { it.key } else emptySet(),
        )
    }

    private data class ScoringContext(
        val now: Long,
        val likedIds: Set<Long>,
        val topArtists: Set<String>,
        val queuedIds: Set<Long>,
        val currentArtist: String,
        val recentlyHeard: Set<String>,
    )

    /** Rises whenever the queue is replaced: an autoplay batch built for the old queue is then dropped. */
    private var queueEpoch = 0L

    /** Rises with every mood / radio / mix request: an older, slower answer never overwrites a newer one. */
    private var suggestionSeq = 0L

    /** What is queued now, as the pipeline wants it (title, artist, length). */
    private fun queuedKeys(): List<Triple<String, String, Long>> =
        connection.state.value.queue.map { q -> Triple(q.title, q.artist, songOfMediaId(q.mediaId)?.durationMs ?: 0L) }

    /** Smart Autoplay (F-92, §8.2): append one batch near the queue's end; never reorders. */
    private fun observeAutoplay() {
        viewModelScope.launch {
            combine(
                connection.state,
                _settings.map { it.autoplay }.distinctUntilChanged(),
            ) { state, auto -> state to auto }
                .collect { (state, auto) ->
                    if (!auto || !state.isPlaying) return@collect
                    if (state.repeatMode != Player.REPEAT_MODE_OFF) return@collect
                    val remaining = state.queue.size - state.currentIndex - 1
                    val now = System.currentTimeMillis()
                    if (remaining > AUTOPLAY_THRESHOLD) return@collect
                    if (state.queue.size >= MAX_QUEUE) return@collect
                    if (now - lastAutoplayAttemptAt < AUTOPLAY_COOLDOWN_MS) return@collect
                    lastAutoplayAttemptAt = now
                    if (autoplayBusy) return@collect
                    autoplayBusy = true
                    val epoch = queueEpoch
                    val need = (AUTOPLAY_TARGET - remaining).coerceAtLeast(AUTOPLAY_MIN_NEED)
                    viewModelScope.launch {
                        try {
                            val built = buildAutoplayBatch(need)
                            // Revalidate against the queue as it is NOW: a newer generation or a replaced queue
                            // drops this result, and songs that arrived meanwhile are not added twice.
                            val live = connection.state.value
                            val queuedNow = live.queue.mapNotNull { it.mediaId.toLongOrNull() }.toSet()
                            val batch = if (epoch == queueEpoch && _settings.value.autoplay && live.queue.isNotEmpty()) {
                                built.filter { it.id !in queuedNow }
                            } else {
                                emptyList()
                            }
                            if (batch.isNotEmpty()) {
                                generationCounter++
                                _origins.update {
                                    it + batch.associate { song -> song.id.toString() to QueueOrigin.SMART_AUTOPLAY }
                                }
                                connection.appendBatch(batch)
                                // It worked: a short batch may be topped up again soon instead of after the full cooldown.
                                lastAutoplayAttemptAt = System.currentTimeMillis() - AUTOPLAY_COOLDOWN_MS + AUTOPLAY_AFTER_SUCCESS_MS
                            }
                        } finally {
                            autoplayBusy = false
                        }
                    }
                }
        }
    }

    /** The song behind a queue row (library first, then the online metadata memory). */
    private fun songOfMediaId(mediaId: String?): Song? {
        val id = mediaId?.toLongOrNull() ?: return null
        return _songs.value.firstOrNull { it.id == id } ?: onlineRepository.peek(id)
    }

    /**
     * Autoplay batch built around what plays NOW: the platform's related list of the current and the two
     * previous songs and the artist's newest uploads. [SmartQueue] ranks and removes repeats.
     *
     * Always aims for [need] songs: when the first pass comes up short (repeats removed, a related list that
     * failed or was small) a second pass adds YouTube's radio of the playing song and relaxes the
     * one-artist limit, so the queue does not drift between a handful and a lot of songs.
     *
     * Downloaded songs are NOT mixed into a streamed autoplay: they join only when playback is offline
     * (online playback off, or nothing could be fetched at all), so the queue never runs dry.
     */
    private suspend fun buildAutoplayBatch(need: Int): List<Song> = withContext(Dispatchers.IO) {
        val ctx = scoringContext()
        val state = connection.state.value
        val current = songOfMediaId(state.mediaId)
        val title = current?.title ?: state.title
        val pool = ArrayList<SmartQueue.Cand<Song>>()
        val online = _settings.value.onlinePlayback
        val seeds = ArrayList<Song>()
        if (online) {
            current?.takeIf { it.videoId != null }?.let { seeds += it }
            for (back in 1..2) {
                songOfMediaId(state.queue.getOrNull(state.currentIndex - back)?.mediaId)
                    ?.takeIf { it.videoId != null && seeds.none { s -> s.id == it.id } }?.let { seeds += it }
            }
            if (seeds.isEmpty()) historyStore.lastOnlineSong()?.let { seeds += it }
            seeds.forEachIndexed { i, seed ->
                safeOnline { onlineRepository.related(seed.videoId!!, 14) }.forEachIndexed { rank, song ->
                    pool += if (i == 0) cand(song, ctx, related = rank) else cand(song, ctx, earlier = rank)
                }
            }
            val channel = current?.channelUrl
            if (!channel.isNullOrBlank()) {
                safeOnline { onlineRepository.channelUploads(channel, 8, current.artist) }
                    .forEachIndexed { rank, song -> pool += cand(song, ctx, newest = rank) }
            }
        }
        fun candidates() = pool.filter { it.item.id !in ctx.queuedIds }
        var picked = SmartQueue.pick(candidates(), queuedKeys(), title, need)
        if (online && picked.size < need) {
            // Second pass: the seed's radio is a much longer list than "related", and a looser artist limit.
            val seed = seeds.firstOrNull()
            if (seed?.videoId != null) {
                safeOnline { onlineRepository.radio(seed.videoId, 30, seed.artist).songs }
                    .forEachIndexed { rank, song -> pool += cand(song, ctx, related = rank + 4) }
            }
            picked = SmartQueue.pick(candidates(), queuedKeys(), title, need, maxArtistRun = 4)
        }
        if (picked.isEmpty()) {
            // Offline (or online playback off) and nothing streamed: the downloaded songs are all there is.
            val library = _songs.value.map { cand(it, ctx) }.filter { it.item.id !in ctx.queuedIds }
            picked = SmartQueue.pick(library, queuedKeys(), title, need, maxArtistRun = 3)
        }
        picked
    }

    /** S-11 mood batch (§8.2 Mood Queue): retrieval by seed query biases the online side. */
    override fun openMood(mood: Mood) {
        _suggestions.value = SuggestionsUi(stage = OnlineStage.Loading, origin = QueueOrigin.MOOD_QUEUE)
        _view.update { it.copy(stack = it.stack + Route.MoodQueue(mood)) }
        loadMood(mood)
    }

    private fun loadMood(mood: Mood) {
        val seq = ++suggestionSeq
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = scoringContext()
            val pool = ArrayList<SmartQueue.Cand<Song>>()
            if (_settings.value.onlinePlayback) {
                // The mood signal is retrieval only (the search the mood stands for), so it is capped
                // well below 1.0 and falls with the result rank: it is never "a perfect mood match".
                mood.seedQueries.take(2).forEach { query ->
                    safeOnline { onlineRepository.search(query, 12) }.forEachIndexed { rank, song ->
                        pool += cand(song, ctx, mood = MOOD_RETRIEVAL_CAP / (1.0 + rank * 0.1))
                    }
                }
            }
            // Downloaded songs only when nothing could be streamed: a mood queue is not mixed with the library.
            if (pool.isEmpty()) _songs.value.forEach { pool += cand(it, ctx) }
            publishSuggestions(seq, QueueOrigin.MOOD_QUEUE, SmartQueue.rank(pool.filter { it.item.id !in ctx.queuedIds }, queuedKeys(), "", MOOD_BATCH))
        }
    }

    /** S-11 Song Radio (§8.2): the seed's related tracks keep their retrieval rank; the seed's artist helps. */
    override fun openRadio(song: Song) {
        _suggestions.value = SuggestionsUi(stage = OnlineStage.Loading, origin = QueueOrigin.SONG_RADIO)
        _view.update { it.copy(stack = it.stack + Route.SongRadio(song.videoId ?: "l${song.id}", song.title)) }
        lastRadioSeed = song
        loadRadio(song)
    }

    private var lastRadioSeed: Song? = null

    private fun loadRadio(seed: Song) {
        val seq = ++suggestionSeq
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = scoringContext().let { it.copy(currentArtist = seed.artist.trim().lowercase()) }
            val pool = ArrayList<SmartQueue.Cand<Song>>()
            val online = when {
                seed.videoId != null -> safeOnline { onlineRepository.related(seed.videoId!!, 14) }
                _settings.value.onlinePlayback -> safeOnline { onlineRepository.search("${seed.artist} ${seed.title}".trim(), 12) }
                else -> emptyList()
            }
            online.forEachIndexed { rank, track -> pool += cand(track, ctx, related = rank) }
            // The seed's downloaded songs by the same artist only fill in when nothing could be streamed.
            if (pool.isEmpty()) {
                _songs.value.filter { it.artist.trim().equals(seed.artist.trim(), ignoreCase = true) }
                    .forEach { pool += cand(it, ctx) }
            }
            publishSuggestions(seq, QueueOrigin.SONG_RADIO, SmartQueue.rank(pool.filter { it.item.id != seed.id && it.item.id !in ctx.queuedIds }, queuedKeys(), seed.title, RADIO_BATCH, maxArtistRun = 3))
        }
    }

    /** Personal Mix: long-term taste, recent listening, favourite artists, rediscovery and a little new music. */
    override fun openPersonalMix() {
        _suggestions.value = SuggestionsUi(stage = OnlineStage.Loading, origin = QueueOrigin.PERSONAL_MIX)
        _view.update { it.copy(stack = it.stack + Route.PersonalMix) }
        loadPersonalMix()
    }

    private fun loadPersonalMix() {
        val seq = ++suggestionSeq
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = scoringContext().copy(currentArtist = "")
            val pool = ArrayList<SmartQueue.Cand<Song>>()
            // 1. What the user already likes: liked songs (library + remembered online) and the newest history.
            val known = (_songs.value + onlineRepository.cachedSongs() + historyStore.recent(40)).distinctBy { it.id }
            for (song in known) {
                val t = tasteStore.taste(song)
                if (song.id in ctx.likedIds || t.completes > 0 || song.artist.trim().lowercase() in ctx.topArtists) pool += cand(song, ctx)
            }
            // 2. A little new music near the most-listened songs (related lists keep their rank).
            if (_settings.value.onlinePlayback) {
                val seeds = listenLog.all().filter { it.videoId != null }
                    .groupingBy { it.key }.eachCount().entries.sortedByDescending { it.value }.take(3)
                    .mapNotNull { e -> listenLog.all().lastOrNull { it.key == e.key }?.toSong() }
                seeds.forEach { seed ->
                    safeOnline { onlineRepository.related(seed.videoId!!, 10) }.forEachIndexed { rank, song ->
                        pool += cand(song, ctx, earlier = rank)
                    }
                }
            }
            publishSuggestions(seq, QueueOrigin.PERSONAL_MIX, SmartQueue.rank(pool.filter { it.item.id !in ctx.queuedIds }, queuedKeys(), "", PERSONAL_MIX_BATCH))
        }
    }

    /** Publishes a result only when it is still the newest generation (a slower, older one is dropped). */
    private fun publishSuggestions(seq: Long, origin: QueueOrigin, batch: List<SmartQueue.Ranked<Song>>) {
        if (seq != suggestionSeq) return
        _suggestions.value = if (batch.isEmpty()) {
            SuggestionsUi(OnlineStage.Unreachable, origin)
        } else {
            SuggestionsUi(OnlineStage.Ready, origin, batch.map { SuggestedSong(it.item, it.reason) })
        }
    }

    override fun playSuggestions() {
        val ui = _suggestions.value
        val songs = ui.list.map { it.song }
        if (songs.isEmpty()) return
        val origin = ui.origin ?: QueueOrigin.MANUAL
        generationCounter++
        _origins.value = songs.associate { it.id.toString() to origin }
        rememberOnline(songs)
        queueEpoch++
        connection.play(songs, 0, null)
    }

    override fun appendSuggestions() {
        val ui = _suggestions.value
        val songs = ui.list.map { it.song }
        if (songs.isEmpty()) return
        val origin = ui.origin ?: QueueOrigin.MANUAL
        generationCounter++
        _origins.update { it + songs.associate { song -> song.id.toString() to origin } }
        connection.appendBatch(songs)
        _messages.tryEmit(UiMessage(R.string.msg_added))
    }

    override fun blockSuggestion(song: Song) {
        tasteStore.setBlocked(song)
        _messages.tryEmit(UiMessage(R.string.msg_not_interested))
    }

    override fun clearPersonalization() {
        historyStore.clear()
        listenLog.clear()
        _recap.value = emptyList()
        tasteStore.clear()
        // A reset is not a new install: the onboarding stays done; only the picked artists are forgotten.
        welcomeStore.clear()
        welcomeStore.finish(emptyList())
        _pickedArtists.value = emptyList()
        homeRequests.value++
        _messages.tryEmit(UiMessage(R.string.msg_personalization_cleared))
    }

    override fun onCleared() {
        connection.release()
        super.onCleared()
    }

    fun shouldAskNotifications(): Boolean = !store.notificationAsked

    fun markNotificationsAsked() {
        store.notificationAsked = true
    }

    /** Puts the last queue back (paused) once, after both the library and the player connection are ready. */
    private fun tryRestore() {
        if (restoreDone) return
        val songs = _songs.value
        if (!connection.isConnected) return
        restoreDone = true
        if (!connection.isQueueEmpty()) return

        val saved = store.load() ?: return
        val byLocalId = songs.associateBy { it.id }
        val byOnlineId = onlineRepository.cachedSongs().associateBy { it.id }
        val queue = saved.ids.mapNotNull { byLocalId[it] ?: byOnlineId[it] }
        if (queue.isEmpty()) return

        val savedIndex = queue.indexOfFirst { it.id == saved.currentId }
        val index = if (savedIndex >= 0) savedIndex else 0
        val position = if (savedIndex >= 0) saved.positionMs else 0L
        connection.restore(queue, index, position, saved.shuffle, saved.repeatMode)
    }

    // ---- Navigation ----

    private fun push(route: Route) = _view.update { it.copy(stack = it.stack + route) }

    override fun openAlbum(key: Long) = push(Route.Album(key))

    override fun openArtist(key: String) {
        val name = groups.value.artistsByKey[key]?.name ?: key
        openArtistProfile(name, "")
    }

    override fun openArtistOf(song: Song) {
        val name = song.artist.trim()
        if (name.isBlank()) return
        val uploader = song.channelUrl ?: onlineRepository.peek(song.id)?.channelUrl
        closePlayerScreens() // from Now Playing the page must not open underneath the player
        // The channel NewPipe gave for this very song is the artist page; nothing is guessed by name.
        if (!song.isOnline || !uploader.isNullOrBlank()) {
            openArtistProfile(name, uploader.orEmpty())
            return
        }
        // The uploader of an online song may be a small re-upload channel; the page belongs to the
        // genuine artist channel when one carries the name, else the uploader stays as the fallback.
        viewModelScope.launch {
            val genuine = try {
                onlineRepository.genuineChannel(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            openArtistProfile(genuine?.name ?: name, genuine?.channelUrl ?: uploader.orEmpty())
        }
    }

    override fun openNowPlaying() = push(Route.NowPlaying)

    override fun openQueue() = push(Route.Queue)

    override fun openSettings() = push(Route.Settings)

    override fun openRecap() {
        _recap.value = listenLog.all()
        push(Route.Recap)
    }

    override fun clearRecap() {
        listenLog.clear()
        _recap.value = emptyList()
    }

    override fun openSettingsCategory(name: String) = push(Route.SettingsPage(name))

    /** Opens the Equalizer screen; from Now Playing the player is closed first (back returns underneath). */
    override fun openEqualizer() = _view.update { state ->
        val rest = state.stack.filterNot { it == Route.NowPlaying || it == Route.Queue || it == Route.Equalizer }
        state.copy(stack = rest.ifEmpty { listOf(Route.Root) } + Route.Equalizer)
    }

    override fun back() = _view.update { state ->
        when {
            state.stack.size > 1 -> state.copy(stack = state.stack.dropLast(1))
            state.destination != Destination.Home -> state.copy(destination = Destination.Home)
            else -> state
        }
    }

    private fun closePlayerScreens() = _view.update { state ->
        val remaining = state.stack.filterNot { it == Route.NowPlaying || it == Route.Queue }
        state.copy(stack = remaining.ifEmpty { listOf(Route.Root) })
    }

    // ---- Library view ----

    override fun setDestination(destination: Destination) =
        _view.update { it.copy(destination = destination, stack = listOf(Route.Root)) }


    override fun setSort(sort: SortOrder) = _view.update { it.copy(sort = sort) }

    override fun setQuery(query: String) = _view.update { it.copy(query = query) }

    override fun commitSearch(query: String) { _recents.value = searchHistory.add(query) }
    override fun removeRecentSearch(query: String) { _recents.value = searchHistory.remove(query) }
    override fun clearRecentSearches() { _recents.value = searchHistory.clear() }

    // ---- Starting playback ----

    override fun playSongs(list: List<Song>, index: Int) {
        if (index !in list.indices) return
        val (window, start) = windowAround(list, index)
        _origins.value = window.associate { it.id.toString() to QueueOrigin.MANUAL }
        rememberOnline(window)
        queueEpoch++
        connection.play(window, start, null)
    }

    override fun playCollection(songs: List<Song>, shuffle: Boolean) {
        if (songs.isEmpty()) return
        if (shuffle) {
            val (window, start) = windowAround(songs, Random.nextInt(songs.size))
            _origins.value = window.associate { it.id.toString() to QueueOrigin.MANUAL }
            rememberOnline(window)
            queueEpoch++
            connection.play(window, start, true)
        } else {
            val (window, start) = windowAround(songs, 0)
            _origins.value = window.associate { it.id.toString() to QueueOrigin.MANUAL }
            rememberOnline(window)
            queueEpoch++
            connection.play(window, start, false)
        }
    }

    override fun playNext(song: Song) {
        _origins.update { it + (song.id.toString() to QueueOrigin.MANUAL) }
        rememberOnline(listOf(song))
        connection.playNext(song)
        _messages.tryEmit(UiMessage(R.string.msg_play_next))
    }

    override fun addToQueue(song: Song) {
        _origins.update { it + (song.id.toString() to QueueOrigin.MANUAL) }
        rememberOnline(listOf(song))
        connection.addToQueue(song)
        _messages.tryEmit(UiMessage(R.string.msg_added))
    }

    /** Keeps at most [MAX_QUEUE] items, centred on the tapped one. */
    /**
     * Makes every online song that is about to play known to [currentSong] / the queue (artwork, like,
     * download). Songs from Home history, the subscription feed or a cached shelf were never in the
     * repository's memory, so Now Playing showed no artwork or buttons for them.
     */
    private fun rememberOnline(songs: List<Song>) {
        if (songs.none { it.videoId != null }) return
        onlineRepository.remember(songs)
    }

    private fun windowAround(list: List<Song>, index: Int): Pair<List<Song>, Int> {
        if (list.size <= MAX_QUEUE) return list to index
        val start = (index - MAX_QUEUE / 2).coerceIn(0, list.size - MAX_QUEUE)
        return list.subList(start, start + MAX_QUEUE) to (index - start)
    }

    // ---- Transport ----

    override fun togglePlayPause() = connection.togglePlayPause()

    override fun next() = connection.next()

    override fun previous() = connection.previous()

    override fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    override fun toggleShuffle() = connection.toggleShuffle()

    override fun cycleRepeat() = connection.cycleRepeat()

    override fun position(): Long = connection.position()

    // ---- Queue ----

    override fun jumpTo(index: Int) = connection.jumpTo(index)

    override fun removeFromQueue(index: Int) {
        // F-102: keep the entry for Undo; provenance stays on the mediaId, so a restored entry
        // keeps its origin chip and a background fill never changes what the user removed.
        val id = connection.state.value.queue.getOrNull(index)?.mediaId
        val song = id?.toLongOrNull()?.let { songsById()[it] }
            ?: id?.let { onlineRepository.peek(it.toLongOrNull() ?: -1L) }
        connection.removeAt(index)
        if (song != null) {
            lastQueueRemoval = QueueRemoval(song, index)
            _messages.tryEmit(
                UiMessage(
                    R.string.queue_removed,
                    listOf(song.title),
                    actionLabel = appContext.getString(R.string.undo),
                    onAction = { undoLastQueueRemoval() },
                ),
            )
        }
    }

    /** Online songs the app has seen (search, shelves, queue), by id; the queue shows their artwork. */
    fun onlineSongsById(): Map<Long, Song> = onlineRepository.cachedSongs().associateBy { it.id }

    /** Songs by local id, cheap map for removal undo and queue-save lookups. */
    private fun songsById(): Map<Long, Song> =
        _songs.value.associateBy { it.id } + onlineRepository.cachedSongs().associateBy { it.id }

    override fun moveInQueue(from: Int, to: Int) = connection.moveTo(from, to)

    /** F-102: re-puts the exact song at its exact old index. Never touches the playing item. */
    override fun undoLastQueueRemoval() {
        val removal = lastQueueRemoval ?: return
        lastQueueRemoval = null
        connection.insertAt(removal.index, removal.song)
    }

    /** F-102 "Clear upcoming": only the entries after the playing song go; playback continues. */
    override fun clearUpcomingQueue() {
        val removed = connection.clearUpcoming()
        _messages.tryEmit(UiMessage(R.string.queue_clear_done, listOf(removed)))
    }

    override fun addToQueueAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        _origins.update { it + songs.associate { song -> song.id.toString() to QueueOrigin.MANUAL } }
        val state = connection.state.value
        if (state.hasItem) {
            connection.appendBatch(songs)
            _messages.tryEmit(UiMessage(R.string.msg_selected_added, listOf(songs.size)))
        } else {
            rememberOnline(songs)
            queueEpoch++
            connection.play(songs, 0, null)
        }
    }

    override fun likeSeveral(songs: List<Song>) {
        if (songs.isEmpty()) return
        val next = songs.fold(_liked.value) { acc, song -> acc + song.id }
        _liked.value = next
        settingsStore.likedIds = next
        _messages.tryEmit(UiMessage(R.string.msg_selected_liked, listOf(songs.size)))
    }

    // ---- F-98: universal song actions ----

    /** Song details are a dialog over whatever screen is open; [detailSong] non-null = shown. */
    override fun openSongDetails(song: Song) {
        _detailSong.value = song
    }

    override fun closeSongDetails() {
        _detailSong.value = null
    }

    // ---- F-103: playlists ----

    override fun openPlaylists() {
        refreshPlaylists()
        push(Route.Playlists)
    }

    override fun openPlaylist(name: String) {
        _playlistSongs.value = _playlists.value.firstOrNull { it.name == name }?.songs.orEmpty()
        push(Route.PlaylistDetail(name))
    }

    override fun createPlaylist(name: String, songs: List<Song>, cover: String?) {
        viewModelScope.launch {
            val ok = playlistStore.create(name, songs, cover)
            if (ok) {
                refreshPlaylists()
                _messages.tryEmit(UiMessage(R.string.msg_playlist_saved, listOf(name)))
            } else {
                _messages.tryEmit(UiMessage(R.string.pl_name_taken))
            }
        }
    }

    override fun appendToPlaylist(name: String, songs: List<Song>) {
        viewModelScope.launch {
            val ok = playlistStore.append(name, songs)
            if (ok) {
                refreshPlaylists()
                _messages.tryEmit(UiMessage(R.string.msg_added))
            } else {
                _messages.tryEmit(UiMessage(R.string.pl_name_taken))
            }
        }
    }

    override fun removeSongFromPlaylist(name: String, index: Int) {
        viewModelScope.launch {
            if (playlistStore.removeSong(name, index)) {
                _messages.tryEmit(UiMessage(R.string.msg_song_removed_playlist))
                refreshPlaylists()
                _openPlaylistSongs(name)
            }
        }
    }

    private fun _openPlaylistSongs(name: String) {
        viewModelScope.launch {
            _playlistSongs.value = playlistStore.load(name)?.songs.orEmpty()
            _playlists.value = playlistStore.loadAll()
        }
    }

    override fun deletePlaylist(name: String) {
        viewModelScope.launch {
            if (playlistStore.delete(name)) {
                refreshPlaylists()
                _messages.tryEmit(UiMessage(R.string.msg_playlist_removed))
            }
        }
    }

    // ---- Downloads: actions ----

    override fun downloadSong(song: Song) {
        if (song.videoId == null || song.isDownloaded) return
        if (DownloadCenter.enqueue(appContext, song)) {
            _messages.tryEmit(UiMessage(R.string.dl_msg_started, listOf(song.title)))
        }
    }

    /** F-195: queues every online song of a list that is not saved yet; one message for the whole batch. */
    override fun downloadAll(songs: List<Song>) {
        var started = 0
        songs.forEach { song ->
            if (song.videoId != null && !song.isDownloaded && DownloadCenter.enqueue(appContext, song)) started++
        }
        _messages.tryEmit(
            if (started > 0) UiMessage(R.string.dl_all_started, listOf(started)) else UiMessage(R.string.dl_all_nothing),
        )
    }


    // ---- Accounts (F-217..F-221) ----

    private fun publishAccounts(busy: Boolean = false) {
        _accounts.value = accountStore.snapshot(busy)
    }

    override fun refreshAccounts() = publishAccounts(_accounts.value.busy)

    override fun setScrobbling(enabled: Boolean) {
        if (enabled && accountStore.listenBrainzToken.isBlank()) return
        accountStore.scrobbling = enabled
        publishAccounts()
    }

    /** Checks the token with ListenBrainz first; only a valid one is kept, and scrobbling turns on with it. */
    override fun connectListenBrainz(token: String) {
        val clean = token.trim()
        if (clean.isEmpty() || accountJob?.isActive == true) return
        publishAccounts(busy = true)
        accountJob = viewModelScope.launch {
            val check = withContext(Dispatchers.IO) { com.dopavin.data.ListenBrainz.validate(clean) }
            when (check) {
                is com.dopavin.data.ListenBrainz.Check.Valid -> {
                    accountStore.listenBrainzToken = clean
                    accountStore.listenBrainzUser = check.user
                    accountStore.scrobbling = true
                    _messages.tryEmit(UiMessage(R.string.acc_lb_connected, listOf(check.user)))
                }
                com.dopavin.data.ListenBrainz.Check.Invalid -> _messages.tryEmit(UiMessage(R.string.acc_lb_invalid))
                com.dopavin.data.ListenBrainz.Check.Unreachable -> _messages.tryEmit(UiMessage(R.string.acc_unreachable))
            }
            publishAccounts()
        }
    }

    override fun disconnectListenBrainz() {
        accountStore.clearListenBrainz()
        publishAccounts()
    }

    /** Stores the server, tests it with a ping, and forgets it again when the login does not work. */
    override fun connectSubsonic(url: String, user: String, password: String) {
        if (accountJob?.isActive == true) return
        val server = com.dopavin.data.SubsonicServer(url, user.trim(), password)
        if (com.dopavin.data.Subsonic.baseUrl(url).isEmpty() || server.user.isEmpty() || password.isEmpty()) return
        publishAccounts(busy = true)
        accountJob = viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) { com.dopavin.data.Subsonic.ping(server) }
            when (reply) {
                com.dopavin.data.Subsonic.Reply.Ok -> {
                    accountStore.subsonicUrl = com.dopavin.data.Subsonic.baseUrl(url)
                    accountStore.subsonicUser = server.user
                    accountStore.subsonicPassword = password
                    _messages.tryEmit(UiMessage(R.string.acc_sub_connected))
                }
                is com.dopavin.data.Subsonic.Reply.Failed ->
                    _messages.tryEmit(UiMessage(R.string.acc_sub_failed, listOf(reply.message.ifBlank { "?" })))
                com.dopavin.data.Subsonic.Reply.Unreachable -> _messages.tryEmit(UiMessage(R.string.acc_unreachable))
            }
            publishAccounts()
        }
    }

    override fun disconnectSubsonic() {
        accountStore.clearSubsonic()
        publishAccounts()
    }

    /** [kind]: "random", "starred" or "s:<text>". Opens the normal online list screen. */
    override fun openSubsonic(title: String, kind: String) {
        if (!accountStore.subsonicConfigured) return
        openOnlineList(title, SUBSONIC_QUERY_PREFIX + kind)
    }

    private fun subsonicSongs(kind: String): List<Song> {
        val server = com.dopavin.data.SubsonicServer(accountStore.subsonicUrl, accountStore.subsonicUser, accountStore.subsonicPassword)
        val tracks = when {
            kind == "random" -> com.dopavin.data.Subsonic.random(server)
            kind == "starred" -> com.dopavin.data.Subsonic.starred(server)
            kind.startsWith("s:") -> com.dopavin.data.Subsonic.search(server, kind.removePrefix("s:"))
            else -> emptyList()
        }
        return tracks.map { t ->
            val videoId = com.dopavin.data.Subsonic.ID_PREFIX + t.id
            Song(
                id = com.dopavin.data.stableOnlineId(videoId),
                title = t.title,
                artist = t.artist,
                album = t.album,
                albumKey = com.dopavin.data.UNKNOWN_ALBUM_KEY,
                durationMs = t.durationSec * 1000L,
                dateAdded = 0L,
                track = 0,
                isOnline = true,
                videoId = videoId,
                thumbUri = t.coverArt?.let { android.net.Uri.parse(com.dopavin.data.Subsonic.coverUrl(server, it)) },
            )
        }.also { onlineRepository.remember(it) }
    }

    private fun ytSong(t: com.dopavin.data.YtTrack) = Song(
        id = com.dopavin.data.stableOnlineId(t.videoId),
        title = t.title,
        artist = t.artist,
        album = "",
        albumKey = com.dopavin.data.UNKNOWN_ALBUM_KEY,
        durationMs = t.durationSec * 1000L,
        dateAdded = 0L,
        track = 0,
        isOnline = true,
        videoId = t.videoId,
        thumbUri = t.thumb?.let(android.net.Uri::parse),
    )

    override fun signOutYouTube() {
        accountStore.signOutYouTube()
        com.dopavin.YouTubeSignInActivity.clearWebSession()
        publishAccounts()
    }

    override fun openYouTubeLikes() {
        if (!accountStore.youTubeSignedIn) return
        openOnlineList(appContext.getString(R.string.acc_yt_likes), com.dopavin.data.YouTubeAccount.LIKED_QUERY)
    }

    /** Copies the owner's YouTube Music library playlists into Dopavin playlists (names already taken are skipped). */
    override fun importYouTubePlaylists() {
        if (accountJob?.isActive == true || !accountStore.youTubeSignedIn) return
        publishAccounts(busy = true)
        _messages.tryEmit(UiMessage(R.string.acc_yt_importing))
        accountJob = viewModelScope.launch {
            val cookie = accountStore.youTubeCookie
            var created = 0
            val lists = withContext(Dispatchers.IO) { com.dopavin.data.YouTubeAccount.libraryPlaylists(cookie) }
            if (lists == null) {
                _messages.tryEmit(UiMessage(R.string.acc_yt_failed))
            } else {
                val existing = playlistStore.loadAll().map { it.name }.toSet()
                for (list in lists.take(YT_IMPORT_MAX_PLAYLISTS)) {
                    if (list.title in existing) continue
                    val songs = withContext(Dispatchers.IO) {
                        com.dopavin.data.YouTubeAccount.playlistSongs(cookie, list.browseId)?.map { ytSong(it) }.orEmpty()
                    }
                    if (songs.isEmpty()) continue
                    onlineRepository.remember(songs)
                    if (withContext(Dispatchers.IO) { playlistStore.create(list.title, songs, songs.first().thumbUri?.toString()) }) created++
                }
                refreshPlaylists()
                _messages.tryEmit(UiMessage(R.string.acc_yt_imported, listOf(created)))
            }
            publishAccounts()
        }
    }

    override fun setFadeSeconds(seconds: Int) = updateSettings { it.copy(fadeSeconds = seconds.coerceIn(0, 12)) }

    // ---- Updates (F-206) ----

    private fun installedVersion(): String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }
            .getOrNull().orEmpty()

    override fun dismissUpdateDialog() {
        _updateDialog.value = false
    }

    override fun skipUpdateVersion() {
        _update.value.release?.tag?.let { updatePrefs.edit().putString(KEY_SKIPPED_UPDATE, it).apply() }
        _updateDialog.value = false
    }

    /** Manual check from About: always asks GitHub and always reports the outcome. */
    override fun checkForUpdates() = runUpdateCheck(manual = true)

    /** F-207: downloads the release APK into the app cache; progress is shown on the Updates page. */
    override fun startUpdateDownload() {
        val release = _update.value.release ?: return
        val url = release.apkUrl
        if (url == null) {
            // No APK attached to the release: the release page is the only thing there is to open.
            runCatching {
                appContext.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(release.pageUrl))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        if (updateDownloadJob?.isActive == true) return
        _update.update { it.copy(step = com.dopavin.data.UpdateStep.Downloading, progress = 0f, errorRes = 0) }
        updateDownloadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.dopavin.data.UpdateInstaller.download(appContext, url, release.tag, release.sizeBytes) { p ->
                    _update.update { it.copy(progress = p) }
                }
            }
            when (result) {
                is com.dopavin.data.UpdateInstaller.Download.Ok -> {
                    val problem = withContext(Dispatchers.IO) { com.dopavin.data.UpdateInstaller.verify(appContext, result.file) }
                    if (problem == null) {
                        _update.update { it.copy(step = com.dopavin.data.UpdateStep.Ready, progress = 1f, errorRes = 0) }
                    } else {
                        result.file.delete()
                        _update.update { it.copy(step = com.dopavin.data.UpdateStep.Failed, errorRes = updateErrorText(problem)) }
                    }
                }
                is com.dopavin.data.UpdateInstaller.Download.Failed ->
                    _update.update { it.copy(step = com.dopavin.data.UpdateStep.Failed, errorRes = updateErrorText(result.reason)) }
            }
        }
    }

    override fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _update.update { it.copy(step = com.dopavin.data.UpdateStep.None, progress = 0f, errorRes = 0) }
    }

    /** Opens the system installer for the downloaded file (or the "allow installs" setting the first time). */
    override fun installUpdate() {
        val release = _update.value.release ?: return
        val file = com.dopavin.data.UpdateInstaller.existing(appContext, release.tag)
        if (file == null) {
            _update.update { it.copy(step = com.dopavin.data.UpdateStep.Failed, errorRes = R.string.update_err_invalid) }
            return
        }
        when (com.dopavin.data.UpdateInstaller.install(appContext, file)) {
            com.dopavin.data.UpdateInstaller.Install.Started -> Unit
            com.dopavin.data.UpdateInstaller.Install.NeedsPermission -> _messages.tryEmit(UiMessage(R.string.update_allow_installs))
            com.dopavin.data.UpdateInstaller.Install.Failed ->
                _update.update { it.copy(step = com.dopavin.data.UpdateStep.Failed, errorRes = R.string.update_err_install) }
        }
    }

    private fun updateErrorText(reason: com.dopavin.data.UpdateInstaller.Reason): Int = when (reason) {
        com.dopavin.data.UpdateInstaller.Reason.Network -> R.string.update_err_network
        com.dopavin.data.UpdateInstaller.Reason.Storage -> R.string.update_err_storage
        com.dopavin.data.UpdateInstaller.Reason.Invalid -> R.string.update_err_invalid
        com.dopavin.data.UpdateInstaller.Reason.NotUpdate -> R.string.update_err_not_update
        com.dopavin.data.UpdateInstaller.Reason.Signature -> R.string.update_err_signature
    }

    override fun setAutoUpdateCheck(enabled: Boolean) = updateSettings { it.copy(autoUpdateCheck = enabled) }

    /** At app start: only when enabled and the last check is older than 12 hours; failures stay silent. */
    private fun autoCheckForUpdates() {
        if (!_settings.value.autoUpdateCheck) return
        val now = System.currentTimeMillis()
        if (now - updatePrefs.getLong("last", 0L) < UPDATE_CHECK_INTERVAL_MS) return
        runUpdateCheck(manual = false)
    }

    private fun runUpdateCheck(manual: Boolean) {
        if (updateJob?.isActive == true) return
        if (_update.value.step == com.dopavin.data.UpdateStep.Downloading) return
        if (manual) {
            _updateDialog.value = false // a manual check is made on Settings > Updates, which shows the result itself
            _update.value = com.dopavin.data.UpdateUi(status = com.dopavin.data.UpdateStatus.Checking)
        }
        updateJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { com.dopavin.data.UpdateCheck.fetchLatest() }
            if (result !is com.dopavin.data.UpdateCheck.Fetch.Error) {
                updatePrefs.edit().putLong("last", System.currentTimeMillis()).apply()
            }
            val installed = installedVersion()
            val before = _update.value
            val next = when (result) {
                is com.dopavin.data.UpdateCheck.Fetch.Ok ->
                    if (com.dopavin.data.UpdateCheck.isNewer(result.release.tag, installed)) {
                        // A finished download of this very release from an earlier run is offered straight away.
                        val kept = withContext(Dispatchers.IO) { com.dopavin.data.UpdateInstaller.existing(appContext, result.release.tag) }
                        val sameRelease = before.release?.tag == result.release.tag
                        com.dopavin.data.UpdateUi(
                            status = com.dopavin.data.UpdateStatus.Available,
                            release = result.release,
                            step = when {
                                kept != null -> com.dopavin.data.UpdateStep.Ready
                                sameRelease && before.step == com.dopavin.data.UpdateStep.Downloading -> com.dopavin.data.UpdateStep.Downloading
                                else -> com.dopavin.data.UpdateStep.None
                            },
                            progress = if (kept != null) 1f else if (sameRelease) before.progress else 0f,
                        )
                    } else {
                        withContext(Dispatchers.IO) { com.dopavin.data.UpdateInstaller.clear(appContext) }
                        com.dopavin.data.UpdateUi(com.dopavin.data.UpdateStatus.UpToDate)
                    }
                com.dopavin.data.UpdateCheck.Fetch.None -> com.dopavin.data.UpdateUi(com.dopavin.data.UpdateStatus.UpToDate)
                com.dopavin.data.UpdateCheck.Fetch.Error ->
                    if (manual) com.dopavin.data.UpdateUi(com.dopavin.data.UpdateStatus.Failed) else before
            }
            _update.value = next
            // The automatic check found a newer release: ask with a dialog, unless this very release was skipped.
            // (A manual check is made from Settings > Updates, which already shows everything.)
            val found = next.release?.tag
            if (!manual && next.status == com.dopavin.data.UpdateStatus.Available && found != null &&
                found != updatePrefs.getString(KEY_SKIPPED_UPDATE, null)
            ) {
                _updateDialog.value = true
            }
        }
    }

    /** F-204: Spotify playlist/album link -> songs found on YouTube Music -> a new playlist. Progress is reported by messages. */
    override fun importSpotify(link: String) {
        val parsed = com.dopavin.data.SpotifyImport.parseLink(link)
        if (parsed == null) {
            _messages.tryEmit(UiMessage(R.string.spotify_import_bad_link))
            return
        }
        if (spotifyJob?.isActive == true) return
        _messages.tryEmit(UiMessage(R.string.spotify_import_reading))
        spotifyJob = viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { com.dopavin.data.SpotifyImport.fetch(parsed.first, parsed.second) }
            if (list == null) {
                _messages.tryEmit(UiMessage(R.string.spotify_import_failed))
                return@launch
            }
            _messages.tryEmit(UiMessage(R.string.spotify_import_matching, listOf(list.tracks.size)))
            val found = ArrayList<Song>()
            for (chunk in list.tracks.take(SPOTIFY_IMPORT_MAX).chunked(4)) {
                val matches = coroutineScope {
                    chunk.map { track ->
                        async { safeOnline { onlineRepository.search("${track.title} ${track.artist}".trim(), 3) }.firstOrNull() }
                    }.map { it.await() }
                }
                matches.filterNotNull().filter { s -> found.none { it.id == s.id } }.forEach { found += it }
            }
            if (found.isEmpty()) {
                _messages.tryEmit(UiMessage(R.string.spotify_import_none))
                return@launch
            }
            var saved: String? = null
            for (suffix in listOf("", " (2)", " (3)")) {
                val name = list.name + suffix
                if (playlistStore.create(name, found, null)) {
                    saved = name
                    break
                }
            }
            refreshPlaylists()
            _messages.tryEmit(
                if (saved != null) UiMessage(R.string.spotify_import_done, listOf(saved, found.size, list.tracks.size))
                else UiMessage(R.string.pl_name_taken),
            )
        }
    }

    override fun shareArtist(name: String, url: String) {
        val text = if (url.isBlank()) name else "$name\n$url"
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        val chooser = android.content.Intent.createChooser(send, appContext.getString(R.string.menu_share))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(chooser)
        } catch (_: android.content.ActivityNotFoundException) {
        }
    }

    override fun setHaptics(enabled: Boolean) = updateSettings { it.copy(haptics = enabled) }

    override fun cancelDownload(videoId: String) = DownloadCenter.cancel(videoId)

    override fun deleteDownload(song: Song) {
        val videoId = song.videoId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            downloadStore.remove(videoId)
            _messages.tryEmit(UiMessage(R.string.dl_msg_deleted, listOf(song.title)))
            refreshStorage()
        }
    }

    override fun deleteAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = downloadStore.removeAll()
            _messages.tryEmit(UiMessage(R.string.dl_msg_deleted_all, listOf(count)))
            refreshStorage()
        }
    }

    /** Step 1 of "Save to device": ask MainActivity to open the system file saver. */
    override fun saveToDevice(song: Song) {
        val videoId = song.videoId ?: return
        if (downloadStore.fileFor(videoId) == null) return
        _pendingSave.value = song
    }

    /** Step 2: the user picked a place; copy the private file there. The private copy stays. */
    fun onSaveTarget(uri: android.net.Uri?) {
        val song = _pendingSave.value
        _pendingSave.value = null
        val videoId = song?.videoId
        if (uri == null || song == null || videoId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val source = downloadStore.fileFor(videoId)
            val ok = try {
                if (source == null) {
                    false
                } else {
                    appContext.contentResolver.openOutputStream(uri, "w")?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out) }
                    } != null
                }
            } catch (e: Exception) {
                false
            }
            _messages.tryEmit(
                UiMessage(if (ok) R.string.dl_msg_saved else R.string.dl_msg_save_failed, listOf(song.title)),
            )
        }
    }

    // ---- Storage ----

    override fun openDriving() {
        _view.update { v -> v.copy(stack = v.stack.filter { it != Route.NowPlaying && it != Route.Queue } + Route.Driving) }
    }

    override fun openCrashLog() {
        _view.update { it.copy(stack = it.stack + Route.CrashLog) }
    }

    override fun openStorage() {
        _view.update { it.copy(stack = it.stack + Route.Storage) }
        viewModelScope.launch(Dispatchers.IO) { refreshStorage() }
    }

    private fun refreshStorage() {
        _storage.value = StorageUi(
            downloadCount = downloadStore.songs.value.size,
            downloadBytes = downloadStore.totalBytes(),
            appDataBytes = appDataBytes(),
            lyricsBytes = lyricsCacheBytes(),
            freeBytes = downloadStore.freeBytes(),
        )
    }

    private fun lyricsCacheBytes(): Long =
        runCatching { File(appContext.filesDir, "lyrics").listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)

    override fun clearLyricsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(appContext.filesDir, "lyrics").listFiles()?.forEach { it.delete() } }
            refreshStorage()
            _messages.tryEmit(UiMessage(R.string.msg_lyrics_cache_cleared))
        }
    }

    /** The app's own data footprint: preference files, the playlist JSON and cached lyrics. */
    private fun appDataBytes(): Long {
        var total = 0L
        runCatching {
            File(appContext.applicationInfo.dataDir, "shared_prefs")
                .listFiles()?.forEach { total += it.length() }
        }
        total += runCatching { File(appContext.filesDir, "playlists.json").length() }.getOrDefault(0L)
        runCatching {
            File(appContext.filesDir, "lyrics").listFiles()?.forEach { total += it.length() }
        }
        return total
    }

    // ---- Sleep timer ----

    override fun startSleepTimer(minutes: Int) = PlaybackRuntime.startSleepTimer(minutes)

    override fun sleepAtEndOfTrack() = PlaybackRuntime.sleepAtEndOfTrack()

    override fun cancelSleep() = PlaybackRuntime.cancelSleep()

    // ---- Sound ----

    override fun setEqEnabled(enabled: Boolean) = PlaybackRuntime.setEqEnabled(enabled)

    override fun setEqBand(band: Int, levelMb: Int) = PlaybackRuntime.setEqBand(band, levelMb)

    override fun setEqPreset(index: Int) = PlaybackRuntime.setEqPreset(index)

    override fun setBass(strength: Int) = PlaybackRuntime.setBass(strength)

    override fun setLoudness(gainMb: Int) = PlaybackRuntime.setLoudness(gainMb)

    override fun resetSound() = PlaybackRuntime.resetEffects()

    // ---- Settings ----

    private fun updateSettings(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value)
        _settings.value = next
        settingsStore.save(next)
    }

    override fun setTheme(theme: ThemeMode) = updateSettings { it.copy(theme = theme) }

    override fun setSkipSilence(enabled: Boolean) {
        updateSettings { it.copy(skipSilence = enabled) }
        PlaybackRuntime.applySkipSilence(enabled)
    }

    override fun setPauseOnUnplug(enabled: Boolean) {
        updateSettings { it.copy(pauseOnUnplug = enabled) }
        PlaybackRuntime.applyPauseOnUnplug(enabled)
    }

    override fun setKeepScreenOn(enabled: Boolean) = updateSettings { it.copy(keepScreenOn = enabled) }

    override fun setPureBlack(enabled: Boolean) = updateSettings { it.copy(pureBlack = enabled) }
    override fun setAccentTint(value: Int) = updateSettings { it.copy(accentTint = value) }

    override fun setKeepPitch(enabled: Boolean) {
        updateSettings { it.copy(keepPitch = enabled) }
        PlaybackRuntime.applyKeepPitch(enabled)
    }

    override fun setSleepFade(enabled: Boolean) {
        updateSettings { it.copy(sleepFade = enabled) }
        PlaybackRuntime.applySleepFade(enabled)
    }

    override fun setStreamQuality(quality: AudioQuality) = updateSettings { it.copy(streamQuality = quality) }

    override fun setStreamQualityMobile(quality: AudioQuality) = updateSettings { it.copy(streamQualityMobile = quality) }

    override fun setDownloadQuality(quality: AudioQuality) = updateSettings { it.copy(downloadQuality = quality) }

    override fun setDownloadWifiOnly(enabled: Boolean) = updateSettings { it.copy(downloadWifiOnly = enabled) }

    override fun setAutoDownloadLiked(enabled: Boolean) = updateSettings { it.copy(autoDownloadLiked = enabled) }

    override fun setAmbience(enabled: Boolean) = updateSettings { it.copy(ambience = enabled) }

    override fun setDownloadLimit(megabytes: Int) = updateSettings { it.copy(downloadLimitMb = megabytes) }

    override fun setResumeOnConnect(enabled: Boolean) = updateSettings { it.copy(resumeOnConnect = enabled) }

    override fun updateGlass(change: (AppSettings) -> AppSettings) = updateSettings(change)

    override fun setSpeed(speed: Float) = PlaybackRuntime.setSpeed(speed)

    /** F-106 Restore defaults: preferences go back to stock; library curation (likes/hidden) stays. */
    override fun resetSettings() {
        settingsStore.restoreDefaults()
        _settings.value = settingsStore.load()
        PlaybackRuntime.resetEffects()
        PlaybackRuntime.applySkipSilence(_settings.value.skipSilence)
        PlaybackRuntime.applyPauseOnUnplug(_settings.value.pauseOnUnplug)
        PlaybackRuntime.applyKeepPitch(_settings.value.keepPitch)
        PlaybackRuntime.applySleepFade(_settings.value.sleepFade)
        _messages.tryEmit(UiMessage(R.string.settings_restore_done))
    }

    // ---- Lyrics ----

    private val lyricsRepository = LyricsRepository(application)
    private val _lyrics = MutableStateFlow(LyricsUi())
    val lyrics: StateFlow<LyricsUi> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null

    override fun setLyricsOnline(enabled: Boolean) = updateSettings { it.copy(lyricsOnline = enabled) }

    override fun setOnlinePlayback(enabled: Boolean) = updateSettings { it.copy(onlinePlayback = enabled) }

    override fun setAutoplay(enabled: Boolean) = updateSettings { it.copy(autoplay = enabled) }

    override fun setAutoplayFresh(enabled: Boolean) = updateSettings { it.copy(autoplayFresh = enabled) }

    override fun setLyricStrip(enabled: Boolean) = updateSettings { it.copy(lyricStrip = enabled) }

    private val lyricsOffsets = LyricsOffsetStore(application)
    private var lyricsToken = 0

    override fun setLyricsProvider(id: String) =
        updateSettings { it.copy(lyricsProvider = LyricsProviders.normalise(id)) }

    override fun loadLyrics(song: Song) = startLyrics(song, _settings.value.lyricsProvider, refresh = false)

    override fun retryLyrics(song: Song) {
        val shown = _lyrics.value
        val selection = if (shown.songId == song.id) shown.selection else _settings.value.lyricsProvider
        startLyrics(song, selection, refresh = true)
    }

    /** One-off: fetch this song's lyrics from [provider] ("choose" = race) without changing the setting. */
    override fun loadLyricsFrom(song: Song, provider: String) = startLyrics(song, provider, refresh = true)

    override fun setLyricsOffset(song: Song, offsetMs: Long) {
        lyricsOffsets.set(song.id, offsetMs)
        _lyrics.update { if (it.songId == song.id) it.copy(offsetMs = lyricsOffsets.get(song.id)) else it }
    }

    private fun startLyrics(song: Song, selection: String, refresh: Boolean) {
        lyricsJob?.cancel()
        val token = ++lyricsToken
        val offset = lyricsOffsets.get(song.id)
        _lyrics.value = LyricsUi(songId = song.id, status = LyricsStatus.Loading, offsetMs = offset, selection = selection)
        lyricsJob = viewModelScope.launch {
            val result = lyricsRepository.load(song, _settings.value.lyricsOnline, selection, refresh)
            // A newer request (another song, a retry, a provider switch) owns the screen now.
            if (token != lyricsToken) return@launch
            _lyrics.value = when (result) {
                is LyricsResult.Found -> LyricsUi(
                    song.id, LyricsStatus.Found, result.synced, result.plain,
                    source = result.source, kind = result.kind, offsetMs = offset, selection = selection,
                )
                LyricsResult.Instrumental -> LyricsUi(song.id, LyricsStatus.Instrumental, offsetMs = offset, selection = selection)
                LyricsResult.NotFound -> LyricsUi(song.id, LyricsStatus.NotFound, offsetMs = offset, selection = selection)
                LyricsResult.Disabled -> LyricsUi(song.id, LyricsStatus.Disabled, offsetMs = offset, selection = selection)
                is LyricsResult.Failed -> {
                    // Technical detail stays out of the UI; keep it for whoever debugs.
                    android.util.Log.w("Lyrics", "lyrics failed kind=${result.kind} detail=${result.detail}")
                    LyricsUi(
                    song.id,
                    LyricsStatus.Failed,
                    error = appContext.getString(
                        when (result.kind) {
                            LyricsResult.Failed.Kind.ServerBusy -> R.string.lyrics_err_server
                            LyricsResult.Failed.Kind.Offline -> R.string.lyrics_err_offline
                            LyricsResult.Failed.Kind.Timeout -> R.string.lyrics_err_timeout
                            LyricsResult.Failed.Kind.Other -> R.string.lyrics_err_other
                        },
                    ),
                    offsetMs = offset,
                    selection = selection,
                    )
                }
            }
        }
    }
}
