# 06 — Architecture

Skill phase 7 input: how the blueprint becomes code. Respects the CodeAssist constraints in `AGENTS.md`.

## 1. Stack and versions

All versions below build together on the on-device toolchain (Gradle 8.14, JDK 17, `compileSdk` 34) — see `07_IMPLEMENTATION_LOG.md`, build verification B-01/B-02. They are still unconfirmed on CodeAssist, which is what the owner builds with.

| Item | Value |
|---|---|
| Language | Kotlin 1.9.24, JVM target 17 |
| Compose compiler | `kotlinCompilerExtensionVersion '1.5.14'` (paired with Kotlin 1.9.24; 1.5.13 belongs to Kotlin 1.9.23 and the build fails the version check with it) |
| Android Gradle Plugin | 8.5.2 |
| Gradle (wrapper properties only) | 8.7 |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 34 / 34 |
| Compose UI, foundation | 1.7.5 (bumped from 1.6.8: vendored Backdrop needs the GraphicsLayer API) |
| Material3 | 1.3.1 (colours mapped from our tokens; used for Slider, menus, snackbar, text field, progress) |
| Activity Compose | 1.9.0 |
| Lifecycle (runtime-ktx, viewmodel-compose, runtime-compose) | 2.8.3 |
| Core KTX | 1.13.1 |
| Media3 exoplayer + session | 1.3.1 (`media3-common` and Guava arrive transitively) |
| Coroutines android | 1.8.1 |

Build files are Groovy, every dependency has an explicit version, there is no BOM and no version catalog.

## 2. Project tree

```
Dopavin/
├─ AGENTS.md
├─ docs/                      (this documentation set)
├─ settings.gradle
├─ build.gradle               (plugin versions)
├─ gradle.properties
├─ gradle/wrapper/gradle-wrapper.properties
└─ app/
   ├─ build.gradle
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/dopavin/
      │  ├─ MainActivity.kt
      │  ├─ data/
      │  │  ├─ Song.kt              Song, Album, Artist models + grouping helpers
      │  │  ├─ MusicRepository.kt   MediaStore query + change flow
      │  │  ├─ ArtworkLoader.kt     thumbnail loading + LRU cache
      │  │  ├─ ArtworkTint.kt       dominant colour of album art (own picker)
      │  │  └─ PlaybackStore.kt     SharedPreferences: queue ids, position, modes, flags
      │  ├─ playback/
      │  │  ├─ PlaybackService.kt   MediaSessionService + ExoPlayer + persistence
      │  │  ├─ ArtworkBitmapLoader.kt  Media3 BitmapLoader backed by ArtworkLoader
      │  │  └─ PlayerConnection.kt  MediaController wrapper exposing StateFlow
      │  └─ ui/
      │     ├─ MusicApp.kt          root composable: access gate, route switch, snackbar, mini player
      │     ├─ UiModels.kt          Access, Route, ViewState, UiMessage, MusicActions interface
      │     ├─ MusicViewModel.kt    implements MusicActions
      │     ├─ theme/  Color.kt  Type.kt  Dimens.kt  Motion.kt  Theme.kt
      │     ├─ components/  Artwork.kt  AlphabetScroller.kt  BottomNav.kt  Controls.kt  Equalizer.kt  Format.kt  MiniPlayer.kt  Position.kt  Rows.kt  States.kt  TopBar.kt
      │     └─ screens/  AccessScreen.kt  HomeScreen.kt  SearchScreen.kt  LibraryScreen.kt  CollectionScreen.kt  NowPlayingScreen.kt  QueueScreen.kt
      └─ res/  drawable/ (ic_*.xml)  font/ (Plus Jakarta Sans)  mipmap-anydpi-v26/  values/ (strings, colors, themes)  values-night/ (themes)  xml/
```

## 3. Manifest and permissions

| Declaration | Why | Notes |
|---|---|---|
| `READ_MEDIA_AUDIO` | Read audio on Android 13+ | Runtime permission |
| `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"` | Read audio on Android 12L and below | Runtime permission |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground service (Android 14 requires the type permission; missing it throws `SecurityException`) | Normal permissions, auto-granted |
| `POST_NOTIFICATIONS` | Show the media notification on Android 13+ | Runtime; denial must not stop playback |
| `WAKE_LOCK` | `setWakeMode(WAKE_MODE_LOCAL)` keeps the CPU awake while playing | Normal |
| *(no `INTERNET`)* | App never uses the network | Checked in the audit |

Service element: `PlaybackService`, `foregroundServiceType="mediaPlayback"`, `exported="true"`, intent filter action `androidx.media3.session.MediaSessionService` (exactly as in the Media3 background-playback guide). `allowBackup="false"` because saved queue ids are only meaningful on this device.

## 4. Playback

**Ownership.** The player lives in the service, never in the UI. The UI is a remote control (`MediaController`). This is what makes background playback, notification, lock screen, headset buttons and "swipe app away while playing" work.

**Service (`PlaybackService`).**
- `ExoPlayer` with music `AudioAttributes` and `handleAudioFocus = true` (pause on focus loss, resume on return), `setHandleAudioBecomingNoisy(true)` (pause on headphone unplug), `WAKE_MODE_LOCAL`.
- `MediaSession` with `ArtworkBitmapLoader` and a session activity that opens `MainActivity` when the notification is tapped.
- Default `onTaskRemoved`: keeps running if playback is ongoing, stops otherwise (documented Media3 behaviour; not overridden).
- A `Player.Listener` saves state to `PlaybackStore` on: timeline change, media item transition, play/pause change, shuffle/repeat change, and in `onDestroy`.
- `onDestroy` releases player and session.

**Controller (`PlayerConnection`).**
- Built once by the ViewModel with `MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()`.
- Commands issued before the controller is ready are queued and run on connect.
- Exposes `StateFlow<PlaybackUi>`: current id/title/artist/album, `isPlaying`, `isBuffering`, duration, shuffle, repeat, queue items, current index, `hasNext`, `hasPrevious`, `connected`.
- Queue list is rebuilt only on timeline change events, not on every position update.
- Position is **not** in the flow. Composables that need it poll `PlayerConnection.position()` every 500 ms while visible and playing, keeping recomposition local to the seek bar / mini-player progress line.
- Error handling: `onPlayerError` increments a failure streak, shows "Couldn't play …, skipped", moves to the next item and prepares/plays. When the streak reaches the queue size or there is no next item, it stops and shows "None of these songs could be played." The streak resets when playback reaches `STATE_READY`.

**MediaItem shape.** `mediaId` = MediaStore `_ID` as a string; `uri` = audio content URI; metadata = title, artist, album, `artworkUri` = the same audio URI (resolved by `ArtworkBitmapLoader`).

**Queue building rule (F-45).** UI passes the visible list and the tapped index; the ViewModel windows it to at most 2000 items around the index before sending it to the controller.

**Restore (T-07).** `PlaybackStore` holds: queue ids (comma-separated), current id, position, shuffle, repeat, plus a "notification permission asked" flag. After the library loads **and** the controller is connected **and** the player queue is empty, the ViewModel resolves ids to songs (dropping vanished ones), sets items with the saved index and position, applies modes, calls `prepare()` and does **not** call `play()`. It runs once per process.

## 5. Library data

- `MusicRepository.loadSongs()` runs on `Dispatchers.IO`. Query: `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`, projection `_ID, TITLE, ARTIST, ALBUM, ALBUM_ID, DURATION, DATE_ADDED, TRACK`, selection `IS_MUSIC != 0 AND DURATION >= 10000` (named constant `MIN_DURATION_MS`).
- Unknown artist/album: MediaStore's `<unknown>` or blank becomes the resource strings "Unknown artist" / "Unknown album". All unknown-album songs share one album bucket (key −1).
- Track number: `TRACK % 1000` (MediaStore encodes disc × 1000 + track).
- `changes()`: a `callbackFlow` registering a `ContentObserver` on the audio URI (descendants included), debounced 1000 ms.
- Grouping and sorting are pure functions in `Song.kt`, executed on `Dispatchers.Default` in the ViewModel (`combine(songs, query, sort)`), so the UI collects ready-made lists.
- Sort: title and artist use a case-insensitive comparator; entries beginning with a non-letter sort after letters; recently added = `dateAdded` descending then title.
- Search: lowercase substring over title, artist and album.

## 6. State model (`MusicViewModel`)

| State | Type | Notes |
|---|---|---|
| `access` | `Access` {NotAsked, Denied, Blocked, Granted} | Set by the Activity from permission results and `onResume` checks |
| `library` | `LibraryStatus` {Loading, Ready, Error} | Plus `songs` |
| `tab`, `sort`, `query` | enums / strings | Session-scoped; `query` drives `results` (Search only), not the Library |
| `destination` | `Destination` {Home, Search, Library} | Bottom navigation; changing it resets the stack |
| `stack` | `List<Route>` | `Root` at bottom (shows the destination); `Album(key)`, `Artist(key)`, `NowPlaying`, `Queue` pushed above |
| `visible` | `Visible` (whole library, sorted) | Derived flow |
| `results` | `Visible` (query matches; empty while the query is blank) | Derived flow |
| `playback` | `PlaybackUi` | From `PlayerConnection` |
| messages | `SharedFlow<UiMessage>` | One-shot snackbar texts |

Screens receive state plus a `MusicActions` interface (implemented by the ViewModel), so they never touch MediaStore or the player. `BackHandler` is enabled whenever search is open or the stack has more than one entry.

## 7. Artwork

`ArtworkLoader` holds an `LruCache<String, Bitmap>` sized to 1/16 of the app's max memory (by bitmap bytes) and a set of keys known to have no artwork (so scrolling back does not retry). Android 10+: `ContentResolver.loadThumbnail(uri, Size(px, px), null)`; Android 8–9: `MediaMetadataRetriever.embeddedPicture` decoded with `inSampleSize`. Sizes in use: 96 px (rows), 512 px (headers, Now Playing, notification). `ArtworkImage` loads in a `LaunchedEffect(uri, size)` and shows the placeholder until a bitmap arrives or if none exists.

## 8. Persistence and error handling

| Data | Where | When written |
|---|---|---|
| Queue, position, modes | `PlaybackStore` (SharedPreferences, `apply()`) | Service listener events and `onDestroy` |
| Notification-asked flag | `PlaybackStore` | After first request |
| Library | Not stored (MediaStore is the source of truth) | — |
| Tab, sort, search | In memory only | — |

Errors: every MediaStore and artwork call is wrapped; failures degrade (empty/Error state, placeholder artwork) and never crash the UI. The ViewModel reports a library failure as `LibraryStatus.Error` with a retry.

## 9. Dependencies deliberately left out

| Library | Reason |
|---|---|
| Hilt / Dagger, KSP, kapt | Annotation processing is slow and fragile on-device; manual wiring is enough for one module |
| Room | Nothing needs a database in v1; MediaStore is the library; playlists (later) can use a JSON file |
| Coil / Glide | Artwork needs are small and handled by `ArtworkLoader` |
| Navigation-Compose | Four screens, one back stack list |
| `material-icons-extended` | Huge to compile; ~23 vector drawables generated from the official SVGs instead |
| Compose BOM | Project rule: explicit versions |
| Palette | Replaced by `ArtworkTint` (own hue-binned picker, ~60 lines) |
| Media3 UI module | Own UI is built to the design system |
| Accompanist | Not needed on Compose 1.6 |

## 10. Known limitations (design-level)

- Gapless playback depends on file format and device decoder.
- Foreground service survival differs per OEM battery policy.
- The queue is capped at a 2000-item window around the tapped song.
- Notification artwork depends on the platform returning a thumbnail; otherwise the notification shows no art.
- With shuffle on, "Play next" inserts after the current item in the list, but the player's shuffle order decides when it is heard, so it may not be the very next song.
- Search and sort are recomputed on every keystroke (off the main thread); very large libraries may show a short delay.
- The three library tabs never scroll horizontally; at large font scale their labels truncate.
- Reduce-motion is read once when the UI starts; changing the system setting needs an app restart to take effect.
- Reorder-by-drag, playlists, equaliser, sleep timer: not in v1 (see scope).

## 11. v2 UI architecture notes

- **Insets.** The root applies only the keyboard inset. Each screen applies status-bar padding itself (so tints can draw behind the bar); screens without the bottom nav apply navigation-bar padding; the bottom nav applies its own.
- **Screen switching.** `AnimatedContent` keyed by the destination (for roots) or the route (for pushed screens), with `SaveableStateProvider` per key. Now Playing slides up/down; everything else fades.
- **Position polling.** `rememberPlaybackPosition` (250 ms) lives in the seek section only; the mini player polls at 500 ms locally.
- **Tint.** `ArtworkTint` reads the cached 96px bitmap from `ArtworkLoader`, samples a 24x24 grid, bins by hue weighted by saturation x value, and caches the result per URI. `rememberArtworkTint` keeps the previous colour until the next is ready.
- **Gestures.** Pure `pointerInput` (`detectVerticalDragGestures` / `detectHorizontalDragGestures` / `detectTapGestures`); no extra dependency.
- **Known v2 limitations.** Album/artist rows stretch edge-to-edge on tablets; the nav and mini player are hidden (not shifted) while the keyboard is open; tint contrast on unusual artwork is unchecked.

## Addendum — Phase 1 architecture
- `PlaybackRuntime` (singleton): same-process bridge between PlaybackService and UI. Owns android.media.audiofx Equalizer/BassBoost/LoudnessEnhancer (rebuilt on audio session id change), sleep timer, skip-silence.
- `SettingsStore`: SharedPreferences-backed AppSettings + EQ persistence.
- `BottomSheet`: custom Animatable sheet; Queue route renders as an overlay while AnimatedContent key stays NowPlaying.
- `MainActivity` collects settings to drive theme and system-bar style.

## Addendum — lyrics
`data/Lyrics.kt` (models + LRC parser), `data/LyricsRepository.kt` (disk cache + LRCLIB `/api/get`, fallback `/api/search`), `MusicViewModel.lyrics: StateFlow<LyricsUi>` (one job, cancelled on song change), `ui/screens/LyricsSheet.kt`.

## Addendum — YouTube streaming (owner-approved 2026-10-19)

**Data.** `Song` grows three fields: `isOnline` (default false), `videoId`, `thumbUri`; `Song.uri` becomes what the player opens (MediaStore content uri, or the marker `dopavin-yt://stream/<videoId>` for online rows), and `Song.artworkUri` (`thumbUri ?: uri`) is what the UI and notification display. `OnlineRepository` owns everything online: NewPipe Extractor v0.26.5 (JitPack, pinned) with a plain `HttpURLConnection` `Downloader`; `search(query)` runs the YouTube service with the `MUSIC_SONGS` filter (InnerTube music search — the extractor has no separate Music service), maps `StreamInfoItem`s to songs (skips live, shorts, and under-a-minute items), gives each a stable id `0x7E000000 | (videoId.hashCode() & 0x00FFFFFF)`; `resolve(videoId)` returns the highest-bitrate audio-only url; a SharedPreferences JSON keeps ≤300 rows of search metadata so a restored queue can still list online rows (stream urls are never stored — they expire, so the resolver fetches a fresh one at open time). `ArtworkLoader` gains an http(s) branch that decodes YouTube thumbnails with the same `inSampleSize` math, and treats `dopavin-yt:` as "no artwork on this uri".

**Playback.** `PlaybackService` builds the player with `DefaultMediaSourceFactory(DefaultDataSource.Factory(context, ResolvingDataSource.Factory(DefaultHttpDataSource.Factory(15 s timeouts, cross-protocol redirects), resolver)))`. The resolver rewrites `dopavin-yt://` markers via `OnlineRepository.resolveBlocking` (a `runBlocking` bridge on the player's loader thread — never the main thread); local `content://` uris pass through untouched. `PlayerConnection.Song.toMediaItem()` now sets `setArtworkUri(song.artworkUri)`. Playback failures skip normally; there is no retry loop.

**State.** `MusicViewModel` holds `_online: MutableStateFlow<OnlineUi>` (`OnlineStage` Off/Idle/Loading/Ready/Unreachable + songs). A single collector: `[query.trim().distinctUntilChanged().debounce(400ms), settings.onlinePlayback] → collectLatest` — stale searches are cancelled (CancellationException re-thrown, everything else maps to Unreachable). `currentSong: StateFlow<Song?>` is a new VM-level lookup (local map first, then `OnlineRepository.peek`) so Now Playing / Lyrics / mini player / tint work for online rows too. Queue restore merges saved ids against local songs and the online metadata cache. `MusicActions.setOnlinePlayback(Boolean)` lands in Settings like every other toggle.

**Threading.** Search/resolve run on `Dispatchers.IO`; the resolver runs on ExoPlayer's loading thread by design (blocking there is the accepted cost); SharedPreferences writes stay on `apply()`. Artwork decode is unchanged except the http branch, which also runs inside the existing background loader.

## Addendum — Online-first Home + provider-chain lyrics (owner-approved 2026-10-20)

**History.** `data/HistoryStore.kt` (new): a small SharedPreferences-backed JSON log of what played — title, artist, optional videoId/thumb/id/duration, timestamp. One `record(song)` per playback change from the ViewModel's `currentSong` flow (same-id entries closer than 90 s update the timestamp instead of appending), capped at 100 entries. Exposes `topArtists(n)` (counted over history), `lastOnlineSong()` and a version counter the Home driver watches. Nothing leaves the device.

**Suggestions.** `OnlineRepository` gains `related(videoId)` (the watch page's related `StreamInfoItem`s through the same mapper as search) and factorises the item→`Song` mapping (`toSong`) so search and related share filters (live/shorts/<60 s). `MusicViewModel` drives a `home: StateFlow<HomeUi>` by `collectLatest` on `[settings.onlinePlayback distinct, historyVersion]`: latest online song → one related search; up to three `topArtists` → one `search(artist)`. Per-shelf failures leave that shelf empty; only when everything fails does the zone show nothing. `loadOnlineList(query)` + `_onlineList` back `Route.OnlineList(title, query)` (`ui/screens/OnlineListScreen.kt`): thin top bar, `SongRow`s played top-down, the same Loading / Unreachable lines as Search. Categories are six static seed queries from `strings.xml`.

> **Third pass (owner, 2026-10-20):** `HistoryStore.recent(limit)` returns the newest plays (sorted by timestamp, identity-upserted) rebuilt as `Song`s; `HomeUi.history` carries twelve of them to the Home shelf, and the artist shelves widened to the top **five** played artists. The local library shelves and their derivations (recent albums, local artists, latest songs) left `HomeScreen.kt` — the components stay for Search/Library use.

**Lyrics chain.** `LyricsRepository.fetch` gains a third provider after the LRCLIB steps: `GET https://api.betterlyrics.org/getLyrics?s=<title>&a=<artist>&al=<album>&d=<seconds>` (+ `&v=<videoId>` when the song has one). Response JSON's `ttml` field is parsed with the platform `XmlPullParser` (namespace-blind, `<p begin>` paragraphs → `LyricLine`), so a Better Lyrics hit becomes ordinary line-sync for the existing sheet and cache. 401/422/444-class answers act as a miss (that API returns 401 on cache misses without its extension's key); per-source failures are swallowed so the chain continues. Result surface (`LyricsResult.*`), cache format, and the sheet are untouched.

**Threading.** Home shelves build on `Dispatchers.IO` inside the same blocking pattern as search; history writes are `apply()` on a background thread; TTML parsing is CPU-light and runs inside the existing lyrics IO context.

## Addendum — Music Core engine (owner spec Stage 3–5 on this codebase, 2026-10-20)

**Stage-1 audit findings (this repo).** Playback is Media3 end-to-end: `PlaybackService` (session authority) ← `PlayerConnection` (controller wrapper, raw-timeline `PlaybackUi.queue: List<QueueItem(mediaId,title,artist)>`, mutations `setMediaItems` replace / `addMediaItem` single / `removeMediaItem`). There is no provenance concept, no feedback store, no batch append, no autoplay. Likes live as `SettingsStore.likedIds` (MediaStore ids only). `PlayerConnection.events` already exposes `SongFailed`/`AllFailed`, so automatic skip-on-error is distinguishable from user skips. `PlaybackUi` has no position; it is polled (`position()`), which shapes the feedback sampler below. `HistoryStore` (F-86) holds plays but no completion/skip semantics. No language anything, anywhere — the spec's §3.9 is already satisfied.

**New files, no new runtime dependencies (except one test dep, F-96):**
- `data/Mood.kt` (pure): `enum class Mood(seedQueries: List<String>)` — six presets; labels live in `strings.xml` (UI maps res ids; the enum stays unit-testable).
- `data/TasteStore.kt`: SharedPreferences JSON keyed by identity (`videoId ?: "l<songId>"`) → `{plays, completes, skips, lastAt, blocked}`. Events: `Started/Completed/Skipped` written from the ViewModel's transition collector; `setBlocked(id)` from "Not interested"; `clear()` from the reset control. Likes stay in `SettingsStore` (single source, reused by the scorer).
- `data/RecScorer.kt` (pure): `RecFeatures(moodMatch?, taste, artistAffinity?, session?, novelty)` + `ScorerWeights` carrying the spec's table (mood .25 / taste .25 / similarity .15 / artist .10 / session .10 / novelty .10 / context .05); `score()` normalises over present features only (§7.4 formula) and returns `Scored(value, reason)` where the reason is the arg-max weighted feature — `MOOD_MATCH` / `TASTE` / `FAVORITE_ARTIST` / `SESSION` / `REDISCOVERY`. Notably **no** language field and **no** fabricated zeros for absent metadata.
- `data/QueuePlanner.kt` (pure): `QueueOrigin` enum (MANUAL/PLAYLIST/ALBUM/SMART_AUTOPLAY/MOOD_QUEUE/SONG_RADIO/PERSONAL_MIX), `QueueItemMeta(mediaId, origin, reason, generationId)`, `planBatch(candidates, exclude, size, maxArtistRun = 2)` — greedy artist-run-diversified selection; deterministic (sort stable, no RNG) and unit-tested.
- `app/src/test/java/.../RecScorerTest.kt`, `QueuePlannerTest.kt`: normalisation, missing-feature handling, hard-block exclusion, artist-run cap, dedupe. `testImplementation "junit:junit:4.13.2"` (F-96, does not touch `assembleDebug`).

**Playback integration (unchanged service).**
- `PlayerConnection.appendBatch(songs)`: `c.addMediaItems(...)` without `prepare()`/`play()` — atomic batch at the very end; current item and manual order untouched; still one `withController`.
- `SettingsStore`: `KEY_SMART_AUTOPLAY` (default true) → `AppSettings.autoplay`.
- ViewModel: a `feedbackSampler` (`while(true) { delay(5 s); store (mediaId, position()) }`) — on a `currentSong` transition the previous song gets `Completed` (last sample ≥ 90 %), `Skipped` (otherwise, unless the last event was `SongFailed` → recorded as *neither*), and the new one `Started`. Numerators stay small (100-entry caps everywhere).
- Autoplay driver: `combine(playback, settings.autoplay).collect` → when repeat mode is OFF, playing, and remaining (queue − currentIndex − 1) ≤ 2 and queue < `MAX_QUEUE`, one generation runs (a `pending` guard prevents double-fire; the next passage re-arms it). Candidates: local library minus blocked/dedup + optional online `related(videoId)`; features per `RecScorer`; publish via `appendBatch` + provenance map + `MOOD`/`RADIO` reuse.
- Suggestion generation (`openMood(mood)`, `openRadio(seed)`) builds `_suggestions: StateFlow<SuggestionsUi>` (stage Ready/Loading/Unreachable, `List<ScoredSong>` with reasons, source distinct from the search flow) and pushes `Route.MoodQueue(mood)` / `Route.SongRadio(seedKey, title)`; `playSuggestions()` = explicit `play()`, `appendSuggestions()` = `appendBatch()`; both stamp provenance.
- `blockSuggestion(song)` → TasteStore + snackbar; next top-of-house refresh (`homeRequests++ / suggestionsVersion++`) re-runs shelves so blocked items vanish from future batches. `clearPersonalization()` clears HistoryStore + TasteStore and refreshes.

**Threading.** Generation + scoring run under `viewModelScope` on `Dispatchers.IO` (already the house pattern); the scorer/planner are pure functions the JVM can test without any Android type; the sampler and collectors stay cheap (no per-frame work); the service keeps being the only playback authority.

## Addendum — UX & Feature Upgrade architecture (F-98…F-107, 2026-10-20)

Files and responsibilities (no new dependency, no KSP/Hilt/Room/Coil/Nav; Groovy DSL unchanged):

- `data/PlaylistStore.kt` (new): `Playlist(name, songs)`; one JSON file `filesDir/playlists.json`; pure `PlaylistCodec` (encode/decode/normaliseName) is JVM-testable; writes are atomic (tmp + rename, direct-write fallback). API: `loadAll/load/create/append/removeSong/delete`.
- `data/Duplicates.kt` (new, pure): `findDuplicates(songs) → DuplicateGroup`s — normalised `artist|title` key (punctuation/case folded, Unknown-artists never group), ±2 s duration agreement, ≥45 s, local only. `compareNames` lives in `Song.kt` (locale-aware, reused).
- `data/Song.kt`: +`sizeBytes` (MediaStore SIZE; 0 unknown) — feeds F-99/F-104.
- `data/MusicRepository.kt`: SIZE in the projection, `deletableRequest(ids): PendingIntent?` (`MediaStore.createDeleteRequest`, null below API 30 — never a direct delete), `sharedStorageFreeBytes()` via `StatFs`.
- `data/SettingsStore.kt`: +`hiddenIds` (curation — survives Restore Defaults), +`restoreDefaults()` resetting only preference-backed keys; `Settings`/`AppSettings` untouched elsewhere.
- `playback/PlayerConnection.kt`: queue ops `moveTo(from, to)` / `insertAt(index, song)` (undo re-inserts at the exact old index) / `clearUpcoming(): Int`; `PlaybackUi` +`speed` (from `playbackParameters` in the state snapshot — one source of truth, the session itself).
- `ui/UiModels.kt`: `Route.SongDetails/Playlists/PlaylistDetail(name)/Storage/Duplicates`; `UiMessage` +`actionLabel/onAction` (Undo); `MusicActions` +`openSongDetails/shareLocalFile/toggleHideSong/addToQueueAll/likeSeveral/moveInQueue/undoLastQueueRemoval/clearUpcomingQueue/openPlaylists/openPlaylist/createPlaylist/appendToPlaylist/removeSongFromPlaylist/deletePlaylist/openStorage/openDuplicates/deleteDuplicates/resetSettings`.
- `ui/MusicViewModel.kt`: state `detailSong/playlists/playlistSongs/duplicates/storage/origins(StateFlow of the session provenance map)/pendingDeleteRequest`; `loadLibrary` filters `hiddenIds`; `removeFromQueue` remembers the last removal for Undo; `clearUpcomingQueue` reports the removed count; `onDuplicateDeleteResult(deleted)` (called by MainActivity) emits the snackbar + rescans; `appDataBytes()` walks prefs + playlists + lyrics dir off-main.
- `ui/components/Rows.kt`: `SongRow` long-press/selected/conditional menu params; `SongActionsMenu` (the one universal menu; +`onMoveUp/onMoveDown` queue-only); `SongActionsOverflow` +modifier; `MenuScrimOverlay` closes the open menu on outside taps.
- `ui/MusicApp.kt`: collects the new flows; `pendingDeleteRequest` no longer launches from Compose — MainActivity owns the `StartIntentSenderForResult` launcher and reports back via `onDuplicateDeleteResult` (Compose cannot register activity-result callbacks); snackbar with one action (Undo).
- `MainActivity.kt`: delete-consent launcher + `PlaybackUi`-independent startup theme pre-read (window background = canvas colour before `setContent`).
- Tests: `app/src/test/java/com/dopavin/data/DuplicatesTest.kt` (pure), `PlaylistCodecTest.kt` (round-trip `Assume`-guarded — org.json is a stub in the mockable android.jar on the JVM; skipped rather than failed there).

---

## Addendum (2026-10-20): downloads-first architecture

- **Library = downloads.** `data/DownloadStore.kt` (singleton): files + `index.json` in `filesDir/downloads/`; exposes `songs: StateFlow<List<Song>>`. `MusicRepository`/MediaStore, `Duplicates.kt`, the Access screen and the media permissions are gone.
- **Engine.** `data/DownloadCenter.kt` (process-wide object): 2 parallel jobs, 1 MiB ranged `HttpURLConnection` requests, `.part` file then `DownloadStore.commit`, Wi-Fi-only check via `ConnectivityManager`, cover saved next to the audio. `playback/DownloadService.kt`: foreground service (`dataSync`) that only mirrors the engine in one notification and stops itself.
- **Stream resolution.** `OnlineRepository.resolve(videoId, quality)` / `resolveForDownload(...)` pick the audio stream by quality (High = best, Standard ≤ 128 kbps, Data saver ≤ 64 kbps; M4A preferred).
- **Playback.** `PlaybackService` wraps `DefaultDataSource.Factory` in `ResolvingDataSource`: marker URI `dopavin-yt://stream/<videoId>` → downloaded file if present, else the live stream URL at the chosen stream quality. A song therefore plays offline the moment its download completes, even if it is already queued.
- **Save to device.** SAF `CreateDocument` launched by `MainActivity` (no storage permission); `MusicViewModel.pendingSave` / `onSaveTarget(uri)` copy the private file; the private copy stays.
- **UI plumbing.** `LocalActions`, `LocalDownloads`, `LocalSheetHost` (CompositionLocals in `MusicApp`) let `SongRow` and cards offer download/sheet actions without extra parameters.
- **New settings** (`SettingsStore`): pure_black, keep_pitch, sleep_fade, stream_quality, download_quality, download_wifi_only, auto_download_liked. Removed: min_duration, hidden ids.
- **Manifest.** Added `FOREGROUND_SERVICE_DATA_SYNC`, `DownloadService`; removed media read permissions. (`INTERNET` is now also used for downloads and YouTube streaming — see docs/09.)
- **Migration.** Playlist and history entries without a `videoId` (old local files) are dropped when read.

### Addendum (2026-10-21): start-up path
`data/HomeCache.kt` stores the last Home shelves (metadata only) in SharedPreferences `home_cache`. `MusicViewModel.initialHome()` reads it synchronously (one small file) so the first frame already has shelves. `observeHome` has two collectors: local history (live, cheap) and online shelves (refresh only if cache older than 30 min, on explicit refresh, or when the YouTube toggle changes); a shelf that returns empty keeps its previous content. `OnlineRepository.resolve` memoises stream URLs for 20 min (process-wide `urlCache`), `prefetch()` warms the first two Home songs; `NewPipe.init` now runs once per process.

## Liquid glass (2026-10-22)
- Vendored sources (Apache 2.0, owner-approved): Kyant0 Backdrop v2.0.0 in `com.dopavin.ui.glass.backdrop` (package renamed, KMP merged, `backdropScale` added) and Capsule in `com.kyant.capsule` (unchanged). Licence texts and notice: `docs/licenses/`; Settings > About credits them.
- Layers in `MusicApp`: `GlassAmbient` (canvas + accent blobs, recorded into `ambientBackdrop`) sits behind everything; screens + Now Playing are recorded into `contentBackdrop`; overlays (floating nav, mini player, sheets) sample `ambient + content` (`rememberCombinedBackdrop`). In-screen controls sample only the ambient layer so a surface never samples itself.
- `Modifier.glassSurface(shape, fallback, tint, floating)` is the single entry point: blur + colour boost + lens + rim light + shadow, parameters from `AppSettings.glass*` via `GlassStyle`. Disabled / API < 31 / animations off => solid `fallback` fill.
- Blur needs Android 12 (API 31), lens/refraction Android 13 (API 33); minSdk stays 26.


## Subscriptions, feed, word-synced lyrics (F-129..F-131)
**Data.** `Song.channelUrl` (nullable) is filled from `StreamInfoItem.uploaderUrl` and persisted in the online metadata, Home cache and subscription feed cache. `data/SubscriptionStore.kt` keeps the subscribed channels (url, name, avatar) and the last merged feed in SharedPreferences `subscriptions` (JSON, display metadata only). `OnlineRepository.channelInfo(url)` and `channelUploads(url, limit)` use `ChannelInfo.getInfo` then `ChannelTabInfo.getInfo` on the VIDEOS tab (TRACKS, then the first tab, as fallbacks); items go through the same `toSong` filters. No account, no sync.
**State.** `MusicViewModel.subscriptions: StateFlow<SubsUi>` (channels, feed, loading); the feed refreshes every channel in parallel (max 12 uploads each), merges, drops duplicates and keeps the newest ~60 (order = channel order then position, NewPipe gives uploads newest first). Actions: `toggleSubscription(song)`, `unsubscribe(url)`, `openSubscriptions()`, `refreshSubscriptions()`; `Route.Subscriptions` shows `SubscriptionsScreen`; a channel opens as `Route.OnlineList(name, "ch:" + url)`.
**Lyrics.** `LyricLine` gains `words: List<LyricWord>` (start, end, text). `parseTtml` keeps `<span begin end>` word timing. Cache entries are written as JSON (`v` = 2) including words; entries without `v` are refetched once. `LyricsPanel` highlights the sung word progressively (fill sweep) and shows a three-dot count-in for long gaps.

- F-133/F-134: `Destination.Library` now renders `LibraryHubScreen`; the old library list is `Route.Downloads`. Artist profile state lives in `MusicViewModel.artist` (one page at a time).
