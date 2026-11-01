package com.dopavin.ui

import com.dopavin.data.ArtistPick
import androidx.annotation.StringRes
import com.dopavin.data.Song
import com.dopavin.data.SortOrder
import com.dopavin.data.ThemeMode

/** Active downloads and the video ids already saved in the app (F-111). */
data class DownloadsUi(
    val active: List<com.dopavin.data.DownloadItem> = emptyList(),
    val savedIds: Set<String> = emptySet(),
) {
    fun item(videoId: String?): com.dopavin.data.DownloadItem? = active.firstOrNull { it.videoId == videoId }
    fun isSaved(videoId: String?): Boolean = videoId != null && videoId in savedIds
}

/** Stage of the YouTube part of a search. */
enum class OnlineStage { Off, Idle, Loading, Ready, Unreachable }

/** YouTube results shown under the library results in Search. */
data class OnlineUi(
    val stage: OnlineStage = OnlineStage.Idle,
    val songs: List<Song> = emptyList(),
    /** Genuine-looking artist channels matching the query (tap opens the artist page). */
    val artists: List<ArtistPick.Hit> = emptyList(),
    /** YouTube has another page of songs for this query. */
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

/** One artist shelf of the Home online zone: a name and the YouTube songs found for it. */
data class HomeArtistShelf(val name: String, val songs: List<Song>)

/** The online zone of Home (owner F-87, F-97): categories are static; history is offline-solid. */
data class HomeUi(
    /** True while the YouTube toggle is on; the screen also hides shelves that came back empty. */
    val active: Boolean = false,
    val loading: Boolean = false,
    /** Trending songs (F-117); also cached so a cold start shows them at once. */
    val trending: List<Song> = emptyList(),
    val relatedTitle: String = "",
    val relatedSongs: List<Song> = emptyList(),
    val artists: List<HomeArtistShelf> = emptyList(),
    /** Newest plays from the local log, local and online alike (F-97). */
    val history: List<Song> = emptyList(),
    /** Merged latest uploads of the subscribed channels (F-129) and how many channels there are. */
    val subFeed: List<Song> = emptyList(),
    /** The most-played artists, each as their newest played song (name + artwork); local, works offline. */
    val topArtists: List<Song> = emptyList(),
    val subCount: Int = 0,
    /** Real channel picture per top artist (lower-cased name), filled in as NewPipe answers. */
    val avatars: Map<String, String> = emptyMap(),
)

/** What the artist profile screen shows. */
data class ArtistUi(
    val name: String = "",
    val url: String = "",
    val avatar: String? = null,
    val stage: OnlineStage = OnlineStage.Idle,
    val songs: List<Song> = emptyList(),
    val albums: List<com.dopavin.data.OnlineRepository.ArtistAlbum> = emptyList(),
)

/** The subscriptions screen: channels (artists) and their merged latest uploads. */
data class SubsUi(
    val channels: List<com.dopavin.data.Subscription> = emptyList(),
    val feed: List<Song> = emptyList(),
    val loading: Boolean = false,
)

/** What S-10 (an online list screen) is showing. */
data class OnlineListUi(
    val title: String = "",
    val query: String = "",
    val stage: OnlineStage = OnlineStage.Idle,
    val songs: List<Song> = emptyList(),
    /** Cover image url of a YouTube playlist / album / radio, when YouTube gave one. */
    val cover: String? = null,
)

/** One row of a generated batch: the song, its reason word and where it came from. */
data class SuggestedSong(val song: Song, val reason: com.dopavin.data.ReasonCode)

/** What S-11 (mood batch / song radio) is showing. */
data class SuggestionsUi(
    val stage: OnlineStage = OnlineStage.Idle,
    val origin: com.dopavin.data.QueueOrigin? = null,
    val list: List<SuggestedSong> = emptyList(),
)

/** The three top-level destinations in the bottom navigation. */
enum class Destination { Home, Search, Library }

/** Screens on the back stack. [Root] (showing the current [Destination]) is always at the bottom. */
sealed interface Route {
    data object Root : Route
    data class Album(val key: Long) : Route
    data class Artist(val key: String) : Route
    data object NowPlaying : Route
    data object Queue : Route
    data object Settings : Route

    /** F-190: listening statistics. */
    data object Recap : Route

    /** One settings category opened from the hub as its own page, so back (button, gesture) pops to the hub. */
    data class SettingsPage(val name: String) : Route
    data object Equalizer : Route
    /** Full view of one Home shelf (a category or an artist) as an online song list. */
    data class OnlineList(val title: String, val query: String) : Route

    /** S-11: a generated mood batch (owner spec §8.2). */
    data class MoodQueue(val mood: com.dopavin.data.Mood) : Route

    /** The generated Personal Mix. */
    data object PersonalMix : Route

    /** S-11: a Song Radio around one seed song. */
    data class SongRadio(val seedKey: String, val title: String) : Route

    /** F-98: metadata, source and size of one song. The Song itself is held in the ViewModel. */

    /** F-103: the playlists list, and one playlist opened in full. */
    data object Playlists : Route
    data class PlaylistDetail(val name: String) : Route

    /** F-104: the honest storage dashboard (downloads + app data). */
    data object Storage : Route

    /** Debug builds only: the stored crash reports. */
    data object CrashLog : Route

    /** Driving mode: big, glanceable transport controls. */
    data object Driving : Route

    /** F-129: subscribed channels and their latest uploads. */
    data object Subscriptions : Route

    /** F-133: one artist (YouTube channel) with songs, albums and a Subscribe button. */
    data class ArtistProfile(val name: String, val url: String) : Route

    /** The downloaded songs (the old Library tab), opened from the Library hub. */
    data object Downloads : Route

    /** The liked songs; separate from [Downloads]. */
    data object Favourites : Route
}

data class ViewState(
    val sort: SortOrder = SortOrder.Title,
    val query: String = "",
    val destination: Destination = Destination.Home,
    val stack: List<Route> = listOf(Route.Root),
) {
    val route: Route get() = stack.last()
}

/** Search helpers for the screen: recent searches and a spelling suggestion valid for [forQuery]. */
data class SearchUi(
    val recents: List<String> = emptyList(),
    val suggestion: String? = null,
    val forQuery: String = "",
)

/** One-shot text for the snackbar. [actionLabel]/[onAction] enable one inline action, e.g. queue Undo. */
data class UiMessage(
    @StringRes val res: Int,
    val args: List<Any> = emptyList(),
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** Which backup file dialog MainActivity should open. */
enum class BackupRequest { Export, Import }

/** Everything the screens can ask the app to do. Implemented by [MusicViewModel]. */
interface MusicActions {
    // Navigation
    fun openAlbum(key: Long)
    fun openArtist(key: String)

    /** Opens the artist page of [song]: the YouTube channel when known, else the library artist, else a search. */
    fun openArtistOf(song: Song)
    fun openArtistProfile(name: String, url: String)
    fun toggleChannelSubscription(url: String, name: String)
    fun openDownloads()

    /** All liked songs (downloaded or not) on their own page. */
    fun openFavourites()

    fun openPersonalMix()
    fun openHistory()
    fun openNowPlaying()
    fun openQueue()
    fun toggleLike(songId: Long)
    fun openSettings()
    fun openRecap()
    fun clearRecap()
    fun openSettingsCategory(name: String)
    fun openEqualizer()

    // Subscriptions (F-129)
    fun openSubscriptions()
    fun refreshSubscriptions()
    fun toggleSubscription(song: Song)
    fun unsubscribe(url: String)
    fun back()

    // Top-level destinations and library view
    fun setDestination(destination: Destination)
    fun setSort(sort: SortOrder)
    fun setQuery(query: String)
    fun commitSearch(query: String)
    fun removeRecentSearch(query: String)
    fun clearRecentSearches()

    // Home online discovery (owner F-87/F-88)
    fun openOnlineList(title: String, query: String)
    fun refreshHome()
    fun openDriving()

    // Music Core (owner spec 2026-10-20): moods, radio, autoplay, feedback
    fun setAutoplay(enabled: Boolean)
    fun setAutoplayFresh(enabled: Boolean)
    fun setAutoUpdateCheck(enabled: Boolean)
    fun checkForUpdates()
    // Accounts (F-217..F-221)
    fun refreshAccounts()
    fun setScrobbling(enabled: Boolean)
    fun connectListenBrainz(token: String)
    fun disconnectListenBrainz()
    fun connectSubsonic(url: String, user: String, password: String)
    fun disconnectSubsonic()
    fun openSubsonic(title: String, kind: String)
    fun signOutYouTube()
    fun openYouTubeLikes()
    fun importYouTubePlaylists()
    fun setFadeSeconds(seconds: Int)
    fun startUpdateDownload()
    fun cancelUpdateDownload()
    fun installUpdate()
    fun setLyricStrip(enabled: Boolean)
    fun openMood(mood: com.dopavin.data.Mood)
    fun openRadio(song: com.dopavin.data.Song)
    fun playSuggestions()
    fun appendSuggestions()
    fun blockSuggestion(song: com.dopavin.data.Song)
    fun clearPersonalization()

    // F-98: universal song actions (the shared context menu)
    fun openSongDetails(song: com.dopavin.data.Song)
    fun closeSongDetails()

    // F-100: multi-select bulk actions
    fun addToQueueAll(songs: List<com.dopavin.data.Song>)
    fun importSpotify(link: String)
    fun downloadAll(songs: List<com.dopavin.data.Song>)
    fun shareArtist(name: String, url: String)
    fun setHaptics(enabled: Boolean)
    fun likeSeveral(songs: List<com.dopavin.data.Song>)

    // F-102: queue integrity and management
    fun moveInQueue(from: Int, to: Int)
    fun undoLastQueueRemoval()
    fun clearUpcomingQueue()

    // F-103: playlists
    fun openPlaylists()
    fun openPlaylist(name: String)
    fun createPlaylist(name: String, songs: List<com.dopavin.data.Song>, cover: String? = null)
    fun appendToPlaylist(name: String, songs: List<com.dopavin.data.Song>)
    fun removeSongFromPlaylist(name: String, index: Int)
    fun deletePlaylist(name: String)

    // F-104: storage dashboard
    fun openStorage()
    fun clearLyricsCache()
    fun openCrashLog()
    fun retryOnline()
    /** Search: fetch and append the next page of YouTube songs. */
    fun loadMoreOnline()

    // F-111: downloads. Saved songs live inside the app; "save to device" makes an optional copy.
    fun downloadSong(song: Song)
    fun cancelDownload(videoId: String)
    fun deleteDownload(song: Song)
    fun deleteAllDownloads()
    fun saveToDevice(song: Song)

    // Starting playback
    fun playSongs(list: List<Song>, index: Int)
    fun playCollection(songs: List<Song>, shuffle: Boolean)
    fun playNext(song: Song)
    fun addToQueue(song: Song)

    // Transport
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun position(): Long

    // Queue
    fun jumpTo(index: Int)
    fun removeFromQueue(index: Int)

    // Sleep timer
    fun startSleepTimer(minutes: Int)
    fun sleepAtEndOfTrack()
    fun cancelSleep()

    // Sound
    fun setEqEnabled(enabled: Boolean)
    fun setEqBand(band: Int, levelMb: Int)
    fun setEqPreset(index: Int)
    fun setBass(strength: Int)
    fun setLoudness(gainMb: Int)
    fun resetSound()

    // Settings
    fun setTheme(theme: ThemeMode)
    fun setSkipSilence(enabled: Boolean)
    fun setPureBlack(enabled: Boolean)
    fun setAccentTint(value: Int)
    fun setAmbience(enabled: Boolean)
    fun setKeepPitch(enabled: Boolean)
    fun setSleepFade(enabled: Boolean)
    fun setStreamQuality(quality: com.dopavin.data.AudioQuality)
    fun setStreamQualityMobile(quality: com.dopavin.data.AudioQuality)
    fun setDownloadQuality(quality: com.dopavin.data.AudioQuality)
    fun setDownloadWifiOnly(enabled: Boolean)
    fun setAutoDownloadLiked(enabled: Boolean)
    fun setDownloadLimit(megabytes: Int)
    fun setResumeOnConnect(enabled: Boolean)
    /** Opens the system file picker to export (true) or import (false) a backup file. */
    fun requestBackup(export: Boolean)
    /** Liquid-glass tweaks: applies [change] to the settings and saves. */
    fun updateGlass(change: (com.dopavin.data.AppSettings) -> com.dopavin.data.AppSettings)
    fun setPauseOnUnplug(enabled: Boolean)
    fun setKeepScreenOn(enabled: Boolean)
    fun setSpeed(speed: Float)
    fun setLyricsOnline(enabled: Boolean)
    fun setOnlinePlayback(enabled: Boolean)
    fun loadLyrics(song: Song)
    fun retryLyrics(song: Song)
    fun loadLyricsFrom(song: Song, provider: String)
    fun setLyricsProvider(id: String)
    fun setLyricsOffset(song: Song, offsetMs: Long)
    fun resetSettings()
}
