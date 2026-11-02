package com.dopavin.ui
import com.dopavin.data.ArtistPick
import androidx.annotation.StringRes
import com.dopavin.data.Song
import com.dopavin.data.SortOrder
import com.dopavin.data.ThemeMode
data class DownloadsUi(
    val active: List<com.dopavin.data.DownloadItem> = emptyList(),
    val savedIds: Set<String> = emptySet(),
) {
    fun item(videoId: String?): com.dopavin.data.DownloadItem? = active.firstOrNull { it.videoId == videoId }
    fun isSaved(videoId: String?): Boolean = videoId != null && videoId in savedIds
}
enum class OnlineStage { Off, Idle, Loading, Ready, Unreachable }
data class OnlineUi(
    val stage: OnlineStage = OnlineStage.Idle,
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistPick.Hit> = emptyList(),
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)
data class HomeArtistShelf(val name: String, val songs: List<Song>)
data class HomeUi(
    val active: Boolean = false,
    val loading: Boolean = false,
    val trending: List<Song> = emptyList(),
    val relatedTitle: String = "",
    val relatedSongs: List<Song> = emptyList(),
    val artists: List<HomeArtistShelf> = emptyList(),
    val history: List<Song> = emptyList(),
    val subFeed: List<Song> = emptyList(),
    val topArtists: List<Song> = emptyList(),
    val subCount: Int = 0,
    val avatars: Map<String, String> = emptyMap(),
)
data class ArtistUi(
    val name: String = "",
    val url: String = "",
    val avatar: String? = null,
    val stage: OnlineStage = OnlineStage.Idle,
    val songs: List<Song> = emptyList(),
    val albums: List<com.dopavin.data.OnlineRepository.ArtistAlbum> = emptyList(),
)
data class SubsUi(
    val channels: List<com.dopavin.data.Subscription> = emptyList(),
    val feed: List<Song> = emptyList(),
    val loading: Boolean = false,
)
data class OnlineListUi(
    val title: String = "",
    val query: String = "",
    val stage: OnlineStage = OnlineStage.Idle,
    val songs: List<Song> = emptyList(),
    val cover: String? = null,
)
data class SuggestedSong(val song: Song, val reason: com.dopavin.data.ReasonCode)
data class SuggestionsUi(
    val stage: OnlineStage = OnlineStage.Idle,
    val origin: com.dopavin.data.QueueOrigin? = null,
    val list: List<SuggestedSong> = emptyList(),
)
enum class Destination { Home, Search, Library }
sealed interface Route {
    data object Root : Route
    data class Album(val key: Long) : Route
    data class Artist(val key: String) : Route
    data object NowPlaying : Route
    data object Queue : Route
    data object Settings : Route
    data object Recap : Route
    data class SettingsPage(val name: String) : Route
    data object Equalizer : Route
    data class OnlineList(val title: String, val query: String) : Route
    data class MoodQueue(val mood: com.dopavin.data.Mood) : Route
    data object PersonalMix : Route
    data class SongRadio(val seedKey: String, val title: String) : Route
    data object Playlists : Route
    data class PlaylistDetail(val name: String) : Route
    data object Storage : Route
    data object CrashLog : Route
    data object Driving : Route
    data object Subscriptions : Route
    data class ArtistProfile(val name: String, val url: String) : Route
    data object Downloads : Route
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
data class SearchUi(
    val recents: List<String> = emptyList(),
    val suggestion: String? = null,
    val forQuery: String = "",
)
data class UiMessage(
    @StringRes val res: Int,
    val args: List<Any> = emptyList(),
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
enum class BackupRequest { Export, Import }
interface MusicActions {
    fun openAlbum(key: Long)
    fun openArtist(key: String)
    fun openArtistOf(song: Song)
    fun openArtistProfile(name: String, url: String)
    fun toggleChannelSubscription(url: String, name: String)
    fun openDownloads()
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
    fun openSubscriptions()
    fun refreshSubscriptions()
    fun toggleSubscription(song: Song)
    fun unsubscribe(url: String)
    fun back()
    fun setDestination(destination: Destination)
    fun setSort(sort: SortOrder)
    fun setQuery(query: String)
    fun commitSearch(query: String)
    fun removeRecentSearch(query: String)
    fun clearRecentSearches()
    fun openOnlineList(title: String, query: String)
    fun refreshHome()
    fun openDriving()
    fun setAutoplay(enabled: Boolean)
    fun setAutoplayFresh(enabled: Boolean)
    fun setAutoUpdateCheck(enabled: Boolean)
    fun checkForUpdates()
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
    fun openSongDetails(song: com.dopavin.data.Song)
    fun closeSongDetails()
    fun addToQueueAll(songs: List<com.dopavin.data.Song>)
    fun importSpotify(link: String)
    fun downloadAll(songs: List<com.dopavin.data.Song>)
    fun shareArtist(name: String, url: String)
    fun setHaptics(enabled: Boolean)
    fun likeSeveral(songs: List<com.dopavin.data.Song>)
    fun moveInQueue(from: Int, to: Int)
    fun undoLastQueueRemoval()
    fun clearUpcomingQueue()
    fun openPlaylists()
    fun openPlaylist(name: String)
    fun createPlaylist(name: String, songs: List<com.dopavin.data.Song>, cover: String? = null)
    fun appendToPlaylist(name: String, songs: List<com.dopavin.data.Song>)
    fun removeSongFromPlaylist(name: String, index: Int)
    fun deletePlaylist(name: String)
    fun openStorage()
    fun clearLyricsCache()
    fun openCrashLog()
    fun retryOnline()
    fun loadMoreOnline()
    fun downloadSong(song: Song)
    fun cancelDownload(videoId: String)
    fun deleteDownload(song: Song)
    fun deleteAllDownloads()
    fun saveToDevice(song: Song)
    fun playSongs(list: List<Song>, index: Int)
    fun playCollection(songs: List<Song>, shuffle: Boolean)
    fun playNext(song: Song)
    fun addToQueue(song: Song)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun position(): Long
    fun jumpTo(index: Int)
    fun removeFromQueue(index: Int)
    fun startSleepTimer(minutes: Int)
    fun sleepAtEndOfTrack()
    fun cancelSleep()
    fun setEqEnabled(enabled: Boolean)
    fun setEqBand(band: Int, levelMb: Int)
    fun setEqPreset(index: Int)
    fun setBass(strength: Int)
    fun setLoudness(gainMb: Int)
    fun resetSound()
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
    fun requestBackup(export: Boolean)
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
