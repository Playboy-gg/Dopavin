# 07 — Implementation Log

Newest information last within each section.

## Decisions during implementation

| # | Decision | Reason |
|---|---|---|
| I-01 | Documents written before any code | Scope and design settled first, as requested |
| I-02 | Nothing could be compiled in the authoring sandbox | Maven Central, Google Maven, the Gradle plugin portal and Gradle distributions were unreachable through the egress proxy (HTTP 000) and no Android SDK is installed. Only parse-level and cross-reference checks were possible (see audit). Later superseded: the build was run on the device toolchain — see "Build verification" below |
| I-03 | Screens take a `MusicActions` interface instead of many lambdas | One place lists everything the UI can do; the ViewModel implements it; fewer signature mismatches |
| I-04 | System-bar and keyboard insets applied once at the root (`systemBarsPadding().imePadding()`) | Every screen is safe by construction; the canvas colour still draws behind the bars |
| I-05 | `SaveableStateProvider` per route inside `Crossfade` | Library scroll position survives opening an album and coming back |
| I-06 | Position polled every 500 ms inside the seek bar / mini player only | Keeps recomposition local; the player flow carries no position |
| I-07 | `Play` on a collection forces shuffle off; tapping a song leaves the shuffle mode as it is | Matches the blueprint (Play = in order) while respecting the user's current mode when picking one song |
| I-08 | Compose, `Slider`, `DropdownMenu`, `Snackbar` come from Material3 with colours mapped from our tokens; everything else is custom | Fewer experimental APIs and no extra dependency |
| I-09 | `BasicTextField` for search, not Material `TextField` | Full control with tokens; no experimental opt-ins |
| I-10 | Icon path data was typed from the Material Icons set (no network to download it) | Glyphs must be eyeballed on a device before release |
| I-11 | Compose compiler moved from `1.5.13` to `1.5.14` | `1.5.13` is the pair for Kotlin 1.9.23 and the compiler aborts the build on any other Kotlin version: *"This version (1.5.13) of the Compose Compiler requires Kotlin version 1.9.23 but you appear to be using Kotlin version 1.9.24"*. Kotlin stays at 1.9.24 (the rule), only the compiler half of the pair moved. `1.5.14` is the matching build and was already in the local Gradle cache, so nothing new has to download |

## Build verification (on-device toolchain)

| # | Command | Result |
|---|---|---|
| B-01 | `:app:compileDebugKotlin` | **BUILD SUCCESSFUL** — the version check no longer fires and all 28 Kotlin files compile |
| B-02 | `:app:assembleDebug` | **BUILD SUCCESSFUL** — APK produced |

Environment: the same runner the MobileIDE build button uses — Gradle 8.14, JDK 17, `compileSdk` 34, `aapt2FromMavenOverride=/usr/local/bin/aapt2`, project synced to a workspace under `/root/mobileide_workspaces`. The `build-tools`/`platform-tools` "inconsistent location" lines and the `aapt2FromMavenOverride` experimental warning are SDK-layout noise from the device image, not project errors.


## Deviations from the first draft of the specs (docs were updated to match)

| Area | Draft said | Built | Why |
|---|---|---|---|
| Title sort | "natural order" | Plain case-insensitive, symbols last | Natural numeric ordering needs a custom comparator; low value |
| Album row subtitle | Artist only if different, else album | Artist in an album, album in an artist view | Repeating the album name on every row of that album carries no information |
| Now Playing top bar | Small centred title | Standard title style at the start | Cross-screen consistency rule |
| Buffering | Ring around play button | Ring replaces the glyph | Simpler, same meaning |
| Tabs at 200% font | Scroll | Fixed three columns, ellipsis | Simpler; noted as a limitation |

## Files

Project root: `AGENTS.md`, `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`.

Docs: `docs/01_DISCOVERY_AND_SCOPE.md` … `docs/08_UI_UX_AUDIT.md`.

App module (`app/`):

| Area | Files |
|---|---|
| Build | `build.gradle`, `proguard-rules.pro`, `src/main/AndroidManifest.xml` |
| Entry | `MainActivity.kt` |
| Data | `data/Song.kt`, `data/MusicRepository.kt`, `data/ArtworkLoader.kt`, `data/PlaybackStore.kt` |
| Playback | `playback/PlaybackService.kt`, `playback/PlayerConnection.kt`, `playback/ArtworkBitmapLoader.kt` |
| UI core | `ui/MusicApp.kt`, `ui/UiModels.kt`, `ui/MusicViewModel.kt` |
| Theme | `ui/theme/Color.kt`, `Type.kt`, `Dimens.kt`, `Motion.kt`, `Theme.kt` |
| Components | `ui/components/Artwork.kt`, `Controls.kt`, `Rows.kt`, `MiniPlayer.kt`, `States.kt`, `TopBar.kt`, `Format.kt` |
| Screens | `ui/screens/AccessScreen.kt`, `LibraryScreen.kt`, `CollectionScreen.kt`, `NowPlayingScreen.kt`, `QueueScreen.kt` |
| Resources | `res/values/strings.xml`, `colors.xml`, `themes.xml`; `res/values-night/themes.xml`; `res/mipmap-anydpi-v26/ic_launcher.xml`; 18 vector drawables in `res/drawable/` |

## v2 — UI/UX redesign and rename (this change)

Base: the owner's uploaded zip (which already contained the Compose compiler 1.5.14 fix, I-11). Worked on a copy; the owner's build changes were preserved.

| # | Decision | Reason |
|---|---|---|
| I-12 | Renamed app to Dopavin, package/applicationId/namespace `com.dopavin`, theme `Theme.Dopavin`, `rootProject.name = "Dopavin"` | Owner's chosen name |
| I-13 | Bottom nav destinations replace the single Library screen; `Route.Library` became `Route.Root` + `Destination` | Professional navigation model |
| I-14 | `visible` no longer filtered by query; new `results` flow for Search | Library must not change while typing elsewhere |
| I-15 | Bundled Plus Jakarta Sans (5 weights, OFL) and generated all icons from the official Material SVGs | Brand voice; removes hand-typed paths (closes UX-02) |
| I-16 | `ArtworkTint` own colour picker | Palette library not allowed |
| I-17 | Custom canvas `SeekBar` instead of Material `Slider` | Gradient, thickening track, full control; adjustable semantics added |
| I-18 | `ic_notification.xml` and `state_paused` string removed/unused-cleaned (UX-08) | Tidy |
| I-19 | Literals allowed for one-off decorative sizes and white/black scrims over artwork | See AGENTS.md rule |

Files added: `data/ArtworkTint.kt`, `ui/components/{AlphabetScroller,BottomNav,Equalizer,Position}.kt`, `ui/screens/{HomeScreen,SearchScreen}.kt`, `res/font/*`, `res/drawable/ic_launcher_{background,monochrome}.xml`, `docs/licenses/*`. Rewritten: all theme files, all components, all screens, `MusicApp.kt`, `UiModels.kt` (Destination, no `searching`, no open/closeSearch), `MusicViewModel.kt` (destination, results), strings, launcher icon, docs 01–08, `AGENTS.md`.

Unchanged: data layer, playback service, player connection, manifest permissions, build files except names.

## Known limitations

- v1 compiled and packaged (B-01, B-02). **v2 has not been compiled at all** (authoring sandbox has no SDK/Maven), and no screen of either version has been seen running.
- Versions in `06_ARCHITECTURE.md` section 1 are confirmed to build together on this device toolchain; they are still unconfirmed on CodeAssist.
- `ic_notification.xml` and the string `state_paused` are defined but not used yet.
- No unit or UI tests in v1.
- Design-level limitations: `06_ARCHITECTURE.md` section 10.

## Next steps (forward-looking)

1. Build v2 in the owner's toolchain; send the error list first (expect a few compile errors: this was written without a compiler).
2. Then run the device test script in `08_UI_UX_AUDIT.md` section 6 and the v2 visual checklist (section 6b).
2. Judge the provisional palette on a real screen; change `ui/theme/Color.kt` only, then re-run the contrast calculation and update `03_DESIGN_SYSTEM.md`.
3. Next feature to consider: playlists and favourites (stored as a JSON file, no Room), per the scope table (F-26, F-27).

## Phase 1 log
Added SettingsStore, PlaybackRuntime, BottomSheet, SettingRows, Volume, SoundScreens, SettingsScreen; QueueSheet; Now Playing extras; settings entry points; theme wiring. Fixed route scoping in MusicApp. Static checks: 42 Kotlin files parse, resource/token/action/import xref 0 problems, XML well-formed.

## Grey press-rectangle fix
Root cause: `clickableScale` (and `selectable` in BottomNav, `toggleable` in ToggleRow) used the default Material ripple. It is drawn on the clickable's own rectangular bounds; the `.clip(CircleShape)` after it in the chain does not clip it, so round buttons showed a grey rectangle. Fix at the root: `clickableScale` now uses `indication = null` (spring scale is the designed feedback); BottomNav and ToggleRow pass `indication = null` with their own interaction source. Material DropdownMenuItem, Switch and Slider keep their standard bounded feedback.

## Phase 3 log (T06)
SettingsStore: pauseOnUnplug, keepScreenOn, speedPercent. PlaybackRuntime: setSpeed, applyPauseOnUnplug (attach reads saved values). UI: SpeedPicker in SoundSheet, two ToggleRows in Settings, Liked tab in Library, keep-screen-on via View.keepScreenOn in NowPlayingScreen. Static checks passed; unbuilt.

## T06b lyrics
Added INTERNET permission, Lyrics.kt, LyricsRepository.kt, LyricsSheet.kt, lyricsOnline setting, Lyrics pill (FlowRow so four pills wrap). Static checks pass (45 files). Not compiled, not tested against live LRCLIB.

## Build fix: mini-player AnimatedVisibility (CodeAssist error)
CodeAssist reported `ui/MusicApp.kt:181:21` — `'fun ColumnScope.AnimatedVisibility(...)' can't be called in this context by implicit receiver`. Cause: the mini-player overlay sits in a `Box` nested in the root `Column`, so the enclosing `ColumnScope` receiver makes the `ColumnScope.AnimatedVisibility` extension shadow the receiver-less overload, and Kotlin refuses that extension from the nested scope (known Kotlin/Compose trap, KT-48215; confirmed by the Compose team's workaround on Kotlin Slack and the top Stack Overflow answer). Fix: the mini-player call is now fully qualified as `androidx.compose.animation.AnimatedVisibility(...)`; behaviour is unchanged (enter/exit transitions are passed explicitly either way). The second `AnimatedVisibility` (Now Playing layer, line ~206) is directly in the outer `Box`, with no `ColumnScope` in scope, so it keeps the plain import. Not compiled here; owner to rebuild on CodeAssist.

## T06c — lyrics resilience + showcase (owner request 2026-10-19)

The owner reported HTTP 503 from the lyrics sheet. Live check from the sandbox: LRCLIB answered 200 (~6 s per request, both `/api/get` and `/api/search`), so the 503 is the service under load, not a permanent break — but the code turned that transient answer into a total failure. Root causes: the lookup chain aborted on the first non-200/404 (`throw IOException`), each request had an 8 s timeout against an observed ~6 s response, and there was no retry.

Changes:
- `data/LyricsRepository.kt`: every HTTP call is now classified (Ok / Miss / Busy). A Busy reply retries once after 600 ms (429 waits 1500 ms) and the chain continues to the next lookup instead of aborting. Timeout 8 s → 12 s. If every lookup was rebuffed, the result is `Failed(ServerBusy, "HTTP nn")` — never `NotFound`, which would be cached for 7 days and look like "no lyrics". Network exceptions classify as `Failed(Offline, detail)`. Failed loads delete the cache file instead of writing it.
- `data/Lyrics.kt`: `LyricsUi` gained `errorDetail` (raw "HTTP 503", for problem reports) next to `error` (friendly message).
- `ui/MusicViewModel.kt`: Failed reasons now map kind → `lyrics_err_server` / `lyrics_err_offline` / `lyrics_err_other`.
- `res/values/strings.xml`: three failure strings added.

Lyrics showcase (owner asked for extra effort):
- `ui/screens/LyricsSheet.kt` rewritten. The header carries a quiet wash tinted from the artwork (animated in, token alpha; the app's own tint picker), plus the equalizer marker while a synced sheet is open.
- Synced lyrics: the sung line sits at full colour with the accent beat marker beside it; surrounding lines fall off in colour and scale (`animateColorAsState` / `animateFloatAsState`, tween(motion.medium)) so a change reads as movement, not a snap. Falloff: near lines 0.5 alpha, the rest 0.28; inactive lines rest at 0.96 scale.
- Auto-follow keeps the current line high on screen (220 px lead) but yields when the user is dragging the list; the next line change resumes following.
- The list melts into the panel at the top (56 dp) and bottom (112 dp) with `surfaceRaised` fades drawn over it, instead of hard line cuts.
- Plain lyrics render in `type.body` for long-form comfort; tap-to-seek on every line is unchanged.
- One-off decorative sizes (allowed list): beat marker 16 dp, header marker 18 dp, edge fade strips 56/112 dp.
- Reduce motion: all durations come from `MotionSpec`, so animation scale 0 snaps instead of animates; the marker bars stop (existing EqualizerBars behaviour).

Static checks passed (parse, imports, tokens, strings cross-referenced). Not compiled; not yet run on a device. Owner rebuild + retry of the failing song expected next.

## T07 — YouTube streaming (owner-approved 2026-10-19)

Owner decision recorded in `01` (F-65 amended, F-80…F-85), `02` (D-23), `05`, `06` addenda.

Files and what changed:
- `settings.gradle`: JitPack repo added. `app/build.gradle`: `com.github.teamnewpipe:NewPipeExtractor:v0.26.5` pinned plus `androidx.media3:media3-datasource:1.3.1` (the one new module; `ResolvingDataSource` lives there).
- `data/Song.kt`: `isOnline` / `videoId` / `thumbUri` fields; `uri` is now a playback marker per source; `artworkUri` getter; `ONLINE_SCHEME` const; `Visible.allEmpty` helper.
- `data/OnlineRepository.kt` (new): search / resolve / metadata cache / `HttpURLConnection` extractor downloader, exactly as described in the `06` addendum. Keys decision: **there is no `ServiceList.YouTubeMusic` in the extractor** — YouTube Music results come from the plain YouTube service with the `MUSIC_SONGS` content filter (InnerTube music search URL); verified against the extractor sources for v0.26.5 (ServiceList, YoutubeSearchQueryHandlerFactory, SearchQueryHandlerFactory, StreamInfoItem, AudioStream, Stream, Image). Item mapping skips live/shorts/<60 s, parses `v` from the result url, thumbnails fall back to `i.ytimg.com` when the extractor gives nothing. `resolve` prefers the highest `bitrate` URL stream and falls back to `averageBitrate`; `Stream.getUrl()` is deprecated/nullable in 0.26.5, so `isUrl`/`getContent` are used.
- `data/ArtworkLoader.kt`: scheme dispatch added — `http(s)` decodes remote thumbnails, `dopavin-yt:` returns null fast (online artwork lives on `thumbUri`), everything else is the unchanged local path.
- `playback/PlaybackService.kt`: `DefaultMediaSourceFactory` + `ResolvingDataSource` chain; resolver rewrites the marker via `resolveBlocking` (loader thread, runBlocking bridge). Local `content://` still handled by `DefaultDataSource`'s own branch; unknown schemes fall to the base data source.
- `playback/PlayerConnection.kt`: `toMediaItem()` sets the notification artwork from `artworkUri` (thumbnails now work in the media notification).
- `data/SettingsStore.kt`: `onlinePlayback` (default true) + `KEY_ONLINE_PLAYBACK`.
- `ui/UiModels.kt`: `OnlineStage`, `OnlineUi`, `MusicActions.setOnlinePlayback`.
- `ui/MusicViewModel.kt`: `onlineRepository` (second repository), `_online`/`online` flow with the debounced `collectLatest` driver, `currentSong: StateFlow<Song?>` (local map → online cache peek), restore merges the online cache, `setOnlinePlayback`.
- `ui/screens/SearchScreen.kt`: `online` parameter, YouTube section (header / loading hint / unreachable hint / rows keyed `y_<id>`), empty-state condition now considers online state; `OnlineHint` row component.
- `ui/components/Rows.kt`: `SongRow` uses `song.artworkUri`.
- `ui/MusicApp.kt`: collects `online` + the new `currentSong`; passes `online` into `RootContent` → `SearchScreen`.
- `ui/screens/NowPlayingScreen.kt`, `MiniPlayer.kt`, `HomeScreen.kt`, `QueueScreen.kt`, `LyricsSheet.kt`: artwork reads switched from `song.uri` to `song.artworkUri` so online rows show their thumbnail (tint included); Queue rows show the placeholder for online items restarted inside the queue (their data was already in the timeline metadata, which has no thumb) — known cosmetic limitation, fix candidate `OnlineRepository.peek`-based lookup.
- `res/values/strings.xml`: `settings_online_search(_body)`, `search_online_header`, `search_online_loading`, `search_online_unreachable`, `online_song_subtitle`.

Known limitations, deliberate:
- Stream playback integrity is extractor-versus-YouTube forever; bumps are the maintenance path (pin is one version string in `app/build.gradle`).
- No poToken / WebView bot guard (F-84); if Google tightens the `android_vr`/web client flow, first sign is searches or resolves failing wholesale (Unreachable / skip-loops), and the fix is an extractor bump, not app code.
- Online rows restored at queue start need network again at first play; metadata only caches identity, not access.
- Queue-screen artwork for online items is a placeholder (above).

Static checks passed (all 47 Kotlin files parse; new imports cross-check; strings referenced exist; no token literals outside the allowed list). Not compiled — no Android SDK in the sandbox; owner rebuild pending, and streaming can only be truly verified on a device with network.

## T07 build fix (CodeAssist error list 1)

Build reached `compileDebugKotlin` — every framework/extractor reference resolved except four problems, all import-typo tier:

1. `YoutubeSearchQueryHandlerFactory` — the YouTube service's link handler package is `…services.youtube.linkHandler` with a **capital H** (core extractor is lowercase `linkhandler`; the extractor's own naming quirk; package line verified against the v0.26.5 source). Import fixed.
2. `ResolvingDataSource` lives directly in `androidx.media3.datasource` (no `resolving` subpackage) — verified from the 1.3.1 sources jar, along with `Resolver` being a SAM interface, the `Factory(Factory, Resolver)` constructor, and `DataSpec.buildUpon()/Builder.setUri(Uri)`.
3. `debounce` is `kotlinx.coroutines.flow.debounce`; the type-inference/"suspension" cascade in the search collector was downstream of it.
4. `AndroidViewModel.getApplication()` is a generic method, so Kotlin exposes no `application` property; the lyrics error mapping touched it. Replaced with a private `appContext` field.

Bonus: the compiler resolved everything else first-try — `SearchInfo.getInfo`, `StreamInfo`, the extractor getters, `Request`/`Response` constructors, `DefaultHttpDataSource.Factory` setters — so all source-checked API guesses were correct; only the two import paths were wrong.

## T07b — Online-first Home + provider-chain lyrics (owner request 2026-10-20)

Owner asked: online suggestions on Home (artist-wise categories, activity-based), and lyrics syncing like YumaPlayer / Better Lyrics. Scope recorded in `01` (addendum F-86…F-90), `02` (D-24, D-25), `05` (S-02/S-08 addendum + new S-10), `06` (addendum). Without an account, activity can only mean local observation — the play history. Downloads and sign-in stay OUT.

Files and what changed:
- `data/HistoryStore.kt` (new): the F-86 log — what played (title, artist, videoId or songId, thumb, duration, time), upsert-on-same-identity-with-90-s-gap, capped at 100, kept in SharedPreferences JSON (app-private, nothing leaves the device). Reads: `topArtists(3)` (most-played first), `lastOnlineSong()`, `version()` for the Home driver. Main-thread safe via a memory mirror; writes `apply()`.
- `data/OnlineRepository.kt`: item→Song mapping factored to `toSong` (search and relation share the live/shorts/<60 s filters); `related(videoId)` added — related `StreamInfoItem`s of a watch page, failure returns an empty list so a shelf hides silently; results feed the same metadata cache.
- `data/Lyrics.kt`: `parseTtml` + `ttmlClockMs` — TTML paragraphs (`<p begin="…">`) pulled apart with the platform `XmlPullParser` (no dependency), word nodes collapsed to one line, clock values handled in mm:ss.ms / hh:mm:ss.ms / seconds forms; bad input parses to an empty list.
- `data/LyricsRepository.kt`: chain step 3 — Better Lyrics public API (`api.betterlyrics.org/getLyrics?s&a&al&d` + `&v` for online songs), `ttml` field parsed into ordinary line-sync; its documented 401 (keyless cache miss) and 422 count as a quiet miss, 429 retries once; every failure collapses to an empty list so the caller's cache decision is untouched. Class doc updated; `BETTERLYRICS_BASE` added.
- `ui/UiModels.kt`: `Route.OnlineList(title, query)`, `HomeUi` / `HomeArtistShelf`, `OnlineListUi`, actions `openOnlineList(title, query)` / `refreshHome()`.
- `ui/MusicViewModel.kt`: `HistoryStore` field; a `currentSong` collector records every real play (book-keeping bumps `homeRequests`); `home: StateFlow<HomeUi>` built by `combine(settings.onlinePlayback, homeRequests).collectLatest` — last online song → one related walk, top-3 artists → one search each, per-shelf failures empty their shelf silently (`safeOnline` rethrows cancellations, swallows the rest); `_onlineList` + `openOnlineList` (stale-response guard by query) + `refreshHome`.
- `ui/MusicApp.kt`: collects `home` (`homeOnline`) and `onlineList`; `RootContent` passes `homeOnline` to HomeScreen; `RouteContent` handles `Route.OnlineList` → `OnlineListScreen`.
- `ui/screens/HomeScreen.kt`: the online zone sits between hero and the local shelves — six seed-category tiles (`HOMEPAGE_CATEGORIES`, label from strings, query in code), a quiet "Searching YouTube…" line while loading, the "Because you played X" carousel (related, no see-all: related lists cannot be tied to a query), artist shelves (top songs, 6 cards, See all → full list), then the "Your music" divider heading. `totalSongs == 0` now still shows Home when the online zone is active (online-first on an empty library); with the toggle off Home is pixel-identical to before. New private `OnlineSongCard` (AlbumCard-like, YouTube thumbnail, current-song equalizer).
- `ui/screens/OnlineListScreen.kt` (new): S-10 thin top bar (back, shelf title, subtitle) + `SongRow`s played top-down + the same loading / unreachable lines Search uses; empty result gets its own line (`online_list_empty`).
- `res/values/strings.xml`: `home_online_categories`, `home_related` (%1$s), `home_artist_shelf` (%1$s), `home_local_music`, `cat_charts`, `cat_punjabi`, `cat_bollywood`, `cat_romantic`, `cat_lofi`, `cat_english`, `online_list_subtitle`, `online_list_empty`.

Known limitations, deliberate (recorded in `01` and `06`):
- "Related" rides the extractor's `relatedItems` — the same change-with-YouTube risk class as streaming; the mitigation is a hidden shelf, not a crash.
- Artist ranking counts plays (90 s upsert refreshes timestamp, not play count weighting); coarse but honest, and enough for shelves.
- Cold start (no history): only categories.
- Word-by-word karaoke display is NOT in this change (F-90): Better Lyrics TTML is syllable-timed, we render line-sync from it.
- Unison (crowdsourced) deliberately later; its read surface is built around the extension.
- The history log and shelves are a personalisation ceiling — no account → no cross-device taste.

Static checks passed (all Kotlin files parse; new imports cross-checked; strings referenced exist; `SectionHeader`/`SongRow`/`EqualizerBars`/`placeholderBrush(seed)` signatures matched against sources; extractors' `StreamInfo.getRelatedItems` + `StreamInfo.getInfo(ServiceList.YouTube, url)` verified in v0.26.5 sources; Better Lyrics response shape `{"ttml": …}` verified against docs.betterlyrics.org). Not compiled — owner rebuild pending.

## T07b build fix (CodeAssist error list 2)

Build #2 reached `:app:compileDebugKotlin` with exactly one error — the whole online-first surface (HistoryStore, related walk, Home zone, OnlineListScreen, Better Lyrics chain, TTML parser) resolved first-try except:

1. `HomeScreen.kt:301` — the "Your music" divider used a bare `AppText` without a `style` (`AppText` has no default style). Replaced with `SectionHeader(title = home_local_music, top/bottom padding)` — same component the other Online zone headers use, so the look is more consistent than what was there.

## F-97 — Home goes online-only (owner steering "offline ka scene hata do", history + artist sections, 2026-10-20)

The owner called while build #4 was pending: drop the offline scene from Home, keep storing history, and give played artists full sections. Recorded as F-97 (scope `01`), D-28 (`02`), S-02 third pass (`05`), and the `06` Suggestions paragraph. Files:

- `HomeScreen.kt` — the local-library zone and its derivations (`recentSongs`, `recentAlbums`, `topArtists`, `currentAlbumKey`, the three file-private section constants, and the quick-tiles / recent-albums / local-artists / latest-songs items) are gone; artist shelves (now five, eight songs each, YouTube) moved ahead of "Because you played"; a new **history shelf** ("Recently played", 12 cards from the log, newest first) closes the column and is the one zone that survives the YouTube toggle; unused imports and the three constants dropped.
- `MusicViewModel.kt` — `observeHome` reads `historyStore.recent(12)` first (so `HomeUi.history` is present even when the toggle is off), widens artists to `topArtists(5)`, and searches eight songs per shelf; new file-private `HOME_HISTORY_ITEMS`/`HOME_ARTIST_SHELVES`.
- `HistoryStore.kt` — new `recent(limit)`: entries sorted by timestamp (the store upserts in place, so array order is not recency), rebuilt as `Song`s.
- `UiModels.kt` — `HomeUi.history`.
- `strings.xml` — `home_history`. (`home_artists`/`home_recent_albums`/`home_latest_songs`/`home_local_music` are now unreferenced but stay — harmless, and the Library tab still owns several of those concepts.)

Local playback keeps full parity through Search and the Library tab; hero "shuffle all" and the empty-library permission state are untouched. Offline Home = moods + history. Verification: brace/paren balance on the four touched files, no leftover local-zone symbols outside one doc comment. Not compiled — rides on build #4, after which the T08 + T07b device script applies with the Home history/artist additions.

## T08 build fix (CodeAssist error list 3)

Build #3 reached `compileDebugKotlin` again; three real problems, everything else cascade:

1. `MusicViewModel.kt` imports were corrupted by the T08 wiring round: a `import androidx.media3.common.Player` insertion had eaten the newline before `import com.dopavin.data.AppSettings`, producing `…AppSettingsimport com.dopavin.data.PlaybackStore`, and `MusicRepository`/`OnlineRepository` were imported twice. The compiler's "expecting top level declaration", the ambiguous-import lines, and every `AppSettings`/`PlaybackStore`/`ids`/`currentId`/`withContext` error (including the MusicApp delegate error and tryRestore's `saved.*` — the code itself was always fine) were downstream of that one broken block. Import block rebuilt, `kotlinx.coroutines.withContext` added (the class never had it).
2. `TasteStore.kt:72-74` — the cap-trim loop's iterator was named `it`, and the surrounding `repeat { … }` lambda's implicit parameter shadowed it (`it.hasNext()` resolved against Int → "unresolved"). Iterator renamed `dropping`.
3. `SuggestionsScreen.kt:107` — `reasonRes()` was a member of `Scored`, but rows carry a `ReasonCode`. Moved to a top-level `ReasonCode.reasonRes()` extension in `RecScorer.kt` (import added in the screen).

Verification: import paths re-scanned (no merged/dotless `import` lines remain), brace/paren balance on all four touched files, `reasonRes` referenced exactly once. Not compiled — fourth build pending (now carrying F-97 as well; see the F-97 section above).

## T08 — Music Core implementation (owner spec `Dopavin_Music_Core_Architecture.md`, 2026-10-20)

Owner supplied a full staged core spec (moods, recommendations, queue generation, feedback, provenance, adapters). Stage-1 audit of the real repository recorded in the `06` addendum: Media3 timeline is the only queue truth; no provenance/feedback/batch-append existed; likes live in `SettingsStore`; `SongFailed` events distinguish auto-failures from user skips; `PlaybackUi` has no position (polled, which shaped the sampler). The spec's "InnerTube module" maps to the extractor integration already shipped in T07/T07b — Stage 6 needs nothing new.

Files and what changed:
- `data/Mood.kt` (new, pure): six presets with backend seed queries; labels in `strings.xml`.
- `data/TasteStore.kt` (new): plays / completes (≥90 %) / soft skips / timestamps / blocked flag per identity (`videoId ?: "l<id>"`), SharedPreferences JSON, 400 cap, `clear()`. Auto-failures record as plain plays (spec §7.5: a broken stream is not a negative).
- `data/RecScorer.kt` (new, pure): `RecFeatures` (all optional), `ScorerWeights` = the spec's table, `score()` normalises over present features only, `Scored(reason)` = arg-max weighted feature (MOOD_MATCH / TASTE / FAVORITE_ARTIST / SESSION / REDISCOVERY). No language feature is representable — §3.9 is structural. `moodMatch` is set only on online candidates the mood query retrieved (honest retrieval-bias, labelled as such).
- `data/QueuePlanner.kt` (new, pure): `QueueOrigin`, `Candidate/Batched`, `planBatch()` — dedupe by id, queued-id exclusion, best-score-first, soft artist-run cap (2 general / 3 artist radio), deterministic, no retries on exhaustion.
- `app/build.gradle`: `testImplementation 'junit:junit:4.13.2'` (owner-approved via spec §15/§17, F-96; not used by assembleDebug).
- `app/src/test/java/com/dopavin/data/RecScorerTest.kt`, `QueuePlannerTest.kt` (new): normalisation, missing features, reason arg-max, exclusions, runs caps, dedupe, empty ranking.
- `playback/PlayerConnection.kt`: `appendBatch(songs)` — atomic `addMediaItems` at the end, no prepare/play interruption; empty queue starts one.
- `data/SettingsStore.kt`: `KEY_SMART_AUTOPLAY` → `AppSettings.autoplay` (default on).
- `ui/UiModels.kt`: `Route.MoodQueue(mood)`, `Route.SongRadio(seedKey, title)`, `SuggestedSong`/`SuggestionsUi` (stage, origin, batch), actions `setAutoplay/openMood/openRadio/playSuggestions/appendSuggestions/blockSuggestion/clearPersonalization`.
- `ui/MusicViewModel.kt`: `TasteStore`; 4 s position sampler; transition collector that settles the previous song (Completed / Skipped / failure-recorded-as-play) and Starts the new one; Smart-Autoplay driver (`remaining ≤ 2`, repeat OFF, `< MAX_QUEUE`, 25 s cooldown, one flight via `autoplayBusy`) appending an 8-song scored batch; `openMood`/`openRadio` building `Candidate`s from the whole library + online tail and publishing S-11 state (`publishMoodBatch`/`publishRadioBatch`); `playSuggestions` (explicit replace, provenance stamped) / `appendSuggestions` (bottom append); `blockSuggestion` (TasteStore + snackbar; every generator filters blocked) / `clearPersonalization` (both stores + refresh); `setAutoplay`; `_origins` provenance map stamped MANUAL on `playSongs`/`playCollection`/`playNext`/`addToQueue` and MOOD_QUEUE/SONG_RADIO/SMART_AUTOPLAY on generated paths (D-27: session-only).
- `ui/MusicApp.kt`: collects `suggestions`; `RouteContent` handles `MoodQueue`/`SongRadio` → `SuggestionsScreen`.
- `ui/screens/SuggestionsScreen.kt` (new): S-11 — `ScreenTopBar`, Play all / Append pills (accent-filled + 10 % tonal, the only new colour use), reason word in each row subtitle via the existing `song_subtitle` pattern, rows carry Play-next/Add-to-queue/Song radio/Not interested.
- `ui/screens/HomeScreen.kt`: `MoodRow` — six mood pills above the category tiles, visible with or without the YouTube toggle (offline mood queues work from local candidates).
- `ui/screens/SettingsScreen.kt`: Smart Autoplay toggle + "Reset personalization & history" row.
- `ui/components/Rows.kt`: `SongRow`/`SongMenu` gained optional `onRadio` / `onNotInterested` (hidden when null; library/collection/queue rows keep the old menu by design).
- `ui/screens/SearchScreen.kt`, `OnlineListScreen.kt`: rows pass `onRadio`/`onNotInterested`.
- `res/values/strings.xml`: 22 new strings (moods ×6, reasons ×5, menu ×2, settings ×4+2, suggestions ×3, snackbar ×2, radio title).

Known limitations, deliberate:
- Feedback sampling runs while the app process's ViewModel lives; long unattended background sessions are not judged (the service owns playback; a service-side recorder is a later, bigger change).
- Provenance is session-only (D-27): after a restore, generated items read as MANUAL. Queue-sheet origin chips are not built yet.
- No audio-feature metadata exists, so similarity-weight (.15) is unused for now — the scorer leaves it structurally present but un-fed rather than fabricating values.
- Mood batches mix local candidates with online retrieval; with the toggle off they are local-only, which for a thin library can return a short batch (the screen says so via the empty state).
- Unit tests exist as JVM JUnit; running them needs `gradlew :app:testDebugUnitTest` — CodeAssist's assembleDebug path never touches them.

Static checks passed (brace/paren balance on all touched files; every `R.string.*` referenced exists; component signatures matched: `ScreenTopBar`, `SongRow`, `ToggleRow`, `SettingsRow`, `ArtworkImage(uri,sizePx,shape,modifier,seed)`, `SectionHeader`, `AppText`; PlayStore-… spec cross-checked sentence by sentence). Not compiled — no Android SDK in the sandbox; owner rebuild pending.

## F-98…F-107 — Music Player UX & Feature Upgrade (owner spec, 2026-10-20)

Scope: the owner's pasted 8-part spec, reconciled in the `01` addendum. Existing machinery reused
everywhere (`PlaybackRuntime`, `PlayerConnection`, stores); nothing faked; single Media3 authority.

Files and what changed:
- `res/values/strings.xml`: +F-98…F-107 string blocks (menus, details, storage, duplicates, playlists, multi-select, queue, More sheet, settings groups); orphan strings from the redesign removed; `tools` namespace note added; XML validated.
- `data/Duplicates.kt`, `data/PlaylistStore.kt` (new), `data/Song.kt` +sizeBytes, `data/MusicRepository.kt` +SIZE/deletableRequest/StatFs, `data/SettingsStore.kt` +hiddenIds/restoreDefaults — architecture notes in `06`.
- `playback/PlayerConnection.kt`: moveTo / insertAt / clearUpcoming + `PlaybackUi.speed` (read from `playbackParameters` in the snapshot).
- `ui/UiModels.kt`: new routes, UiMessage action pair, ~16 new MusicActions methods.
- `ui/MusicViewModel.kt`: all new state + implementations; `loadLibrary` honours `hiddenIds`; queue Undo (single-step, exact index re-insert, in-memory); duplicates consent flow reports through `onDuplicateDeleteResult` (MainActivity-registered launcher, since Compose cannot own activity results).
- `ui/components/Rows.kt`: `SongRow` rewritten (Box wrapper, selection overlay, long-press, conditional universal menu); `SongActionsMenu` (+queue-only move items); `SongActionsOverflow` +modifier; `MenuScrimOverlay`.
- `ui/MusicApp.kt`: all new flows/routes wired; snackbar action handling; delete consent moved out of Compose to MainActivity.
- `ui/screens/ExtrasScreens.kt` (new): SongDetails (honest facts, "Unknown" when absent), Storage (three real numbers + the no-downloads note), Duplicates (review → confirm → system consent), Playlists + PlaylistDetail + PlaylistNameDialog.
- `ui/screens/QueueScreen.kt`: full rewrite — S-12 (drag reorder with drop-time commit, swipe-remove + Undo, Auto chips, save/clear with confirmations, queue-only menu items).
- `ui/screens/LibraryScreen.kt`: F-100 multi-select (Songs + Liked) with a non-destructive action bar + Select all; Playlists shortcut in the top bar.
- `ui/screens/HomeScreen.kt`: `SongShelfCard` — Home cards gain the universal menu on long-press (hero untouched).
- `ui/screens/SearchScreen.kt` / `OnlineListScreen.kt` / `SuggestionsScreen.kt` / `CollectionScreen.kt`: rows pass the full conditional menu set (Play now now labelled as the explicit queue-replacing action).
- `ui/screens/NowPlayingScreen.kt`: fifth pill + `MoreSheet` (speed chips reuse the now-`internal` `SpeedPicker`; skip-silence toggle; output device label via `AudioManager.getDevices` with graceful empty fallback; system sound settings intent; radio; details; honest offline status). `PlayerSheet.More` added.
- `ui/screens/SettingsScreen.kt`: rewritten — search-in-place, honest categories, Advanced fold, Restore Defaults with confirmation (destructive zone), version/licences text; library group carries Playlists/Duplicates/Refresh shortcuts.
- `MainActivity.kt`: startup theme pre-read (window background = canvas colour before the first frame) + the F-105 delete-consent launcher reporting to the VM.
- Tests: `DuplicatesTest` (pure), `PlaylistCodecTest` (round-trip `Assume`-guarded for the stubbed org.json on the JVM).

Decisions of note: D-29…D-33 in `02`. Play Now is always the explicit replace-queue action and is
labelled as such in the menu. Hidden/liked survive Restore Defaults. Share/details only for real
capabilities (online rows: no share/hide — no file to point at).

Known limitations, deliberate:
- Queue Undo is single-step and in-memory (process death loses it); provenance stays session-only (D-27).
- Drag reorder assumes uniform 72 dp rows; at 200 % font the row content wraps, so drag pauses at the fold (buttons still reorder). No auto-scroll at drag edges in v1.
- Playlist rename not wired in v1 (delete + recreate works).
- Speed chips show the player's real state but there is no separate "default speed" setting (per-session; reset by the service).
- Storage numbers are point-in-time snapshots (no watch).
- The org.json round-trip test skips on the JVM sandbox (stub); format is exercised on device.
- `state_paused`, `access_title`, `cd_search`, `cd_close_search`, `cd_open_queue`, `cd_play_album`, `home_*` group strings remain defined but unreferenced (pre-batch leftovers, harmless).

Static checks passed (triple-quote-aware balance on all touched files; every `R.string.*` and
`R.drawable.*` referenced exists; MusicActions fully overridden; routes used == routes defined;
MusicApp named-arg audit clean). Not compiled — no Android SDK in the sandbox; owner build #5 pending.

## Build #5 fix (CodeAssist error list 4, 2026-10-20)

`compileDebugKotlin` failed on six real issues in the F-98…F-107 batch; resources had already
passed, so every fix is Kotlin-side:

- `PlayerConnection.clearUpcoming()` used the non-existent `removeMediaItemRange` → Media3's `removeMediaItems(current + 1, count)`.
- `MusicApp` called the ExtrasScreens file as an object (`ExtrasScreens.Song…`) — Kotlin top-level functions cannot be referenced through a filename → direct imports (`SongDetailsScreen`, `StorageScreen`, `DuplicatesScreen`, `PlaylistsScreen`, `PlaylistDetailScreen`).
- `RootContent` was not receiving `playlists` for the LibraryScreen call → added to its signature and the call site (LibraryScreen's Playlists shortcut stayed).
- `RouteContent` has no `currentSong` → `DuplicatesScreen` lost its decorative `currentSongId "now playing"` tag (identity nicety, not worth threading playback through the router).
- `HomeContent` / `ResultsContent` helpers use `liked` inside their bodies but did not declare it → parameter added to both helpers and their call sites (Home shelves and Search results keep the Favourite toggle + menu).
- `HomeScreen` had an orphaned `@Composable` floating between KDoc blocks (my `SongShelfCard` insertion split the old doc of `OnlineSongCard`) → the two composables' docs and annotations are straightened out.
- `NowPlayingScreen.MoreSheet` used `heading()` without importing `androidx.compose.ui.semantics.heading`.
- `QueueScreen` imported `QueueOrigin` and `UNKNOWN_ALBUM_KEY` from the wrong package → both live in `com.dopavin.data` (`QueuePlanner.kt` / `Song.kt`); imports corrected.

Also hardened silently before this list: the queue drag commits through `rememberUpdatedState`
callbacks (pointerInput(Unit) captures its block once — without it, drag-end would read stale
lambdas after the list changed mid-drag).

## Downloads-first batch (owner request 2026-10-19/20, F-108…F-116)

Done (written, **not compiled** — no Android SDK in the sandbox):
- Crash fix: `ui/components/States.kt` `EmptyState` is now bounded-height aware (scrolls only when height is bounded; otherwise `heightIn(min = 280.dp)`). Root cause: `EmptyState` with `verticalScroll` was placed in a parent that measured it with infinite height.
- Data/playback: `Song`, `SettingsStore`, `OnlineRepository`, `DownloadStore`, `DownloadCenter`, `DownloadService`, `PlaybackService`, `PlaybackRuntime` (keep pitch, sleep fade), `ArtworkLoader` (file:// covers), manifest, 11 drawables.
- ViewModel/UI: `MusicViewModel` (downloads, storage, save to device, new setters), `UiModels` (`DownloadsUi`, trimmed `MusicActions`/`Route`/`ViewState`), `MainActivity` (no permissions, save-to-device launcher, pure-black startup colour), `MusicApp` (locals + sheet layer), `ActionSheet.kt` (new), `Rows.kt` (`SongActionsMenu` is a sheet), `LibraryScreen` (Downloads), `HomeScreen`, `NowPlayingScreen`, `SearchScreen`, `SettingsScreen` (hub), `ExtrasScreens` (Storage, details), `Theme` (pure black), `BottomNav`.
- Deleted: `MusicRepository`, `Duplicates`, `AccessScreen`, `DuplicatesTest`.
- Tests: `PlaylistCodecTest` updated (legacy local entries are dropped).
- Strings: all referenced strings exist (script check); stale access/duplicate/min-duration/refresh strings removed.

Checks actually run: `kotlinc` 1.9.24 parse of all sources — **no syntax errors**; other compiler output is unresolved Android/Compose/Media3/NewPipe symbols because no SDK/Maven is reachable, so type-checking against real libraries has NOT happened. `strings.xml` is well-formed; every `R.string.*` reference is defined.

Known limitations:
- Download resume after process death is not implemented (partial files are deleted at start).
- Deleting a download that is still in the play queue makes that queue item fail and skip.
- Download streams come from NewPipeExtractor URLs; providers can throttle or rotate them — failures show "cannot be downloaded right now".
- `deliveryMethod != PROGRESSIVE_HTTP` streams (DASH/HLS) are not downloaded.

## Home redesign / landscape / fast start (2026-10-21)
Files: `components/Controls.kt` (`clickableScale(onLongClick)`), `components/Rows.kt` (SongRow long-press opens the sheet by default), `screens/HomeScreen.kt` (rewritten body), `data/HomeCache.kt` (new), `data/OnlineRepository.kt` (URL cache, prefetch, static init), `ui/MusicViewModel.kt` (observeHome, fetchHome, prefetchTop, initialHome), `ui/UiModels.kt` (`HomeUi.trending`), `AndroidManifest.xml` (fullUser + configChanges), `strings.xml`.
Checks run: kotlinc parse (no syntax errors), string-resource coverage. NOT run: compile against Compose/Media3, device test of the tap fix, landscape on a device, cold-start timing.
Known limits: first-ever launch (no cache) still waits for the network; bottom navigation stays at the bottom in landscape (no navigation rail yet); prefetch costs two small requests per start.

## 2026-10-22 - Liquid glass (F-120) and extras (F-121)
- Owner request: liquid glass on every button, switch, navigation and component, controllable from Settings, using the supplied Kyant0 Backdrop and Capsule sources with licences. Owner approved the vendoring and the Compose 1.7.5 / Material3 1.3.1 bump (AGENTS rule "no new dependency without approval" satisfied).
- Added: glass engine, GlassSwitch, floating glass bottom nav, glass mini player/sheets/buttons/chips/search/Now Playing controls, Settings "Glass & style" with presets and live preview, `glass*` fields in AppSettings.
- Extras: `downloadLimitMb` + `DownloadStore.trimTo`, downloaded badge in `SongRow`, `Backup` codec + export/import via SAF, `resumeOnConnect` (AudioDeviceCallback, only within 10 min after a noisy pause).
- Bug fix: `PlaylistCodec.encode/decode` called themselves (member shadows the private top-level function); renamed the top-level helpers `encodeAll/decodeAll`.
- Verification: kotlinc 1.9.24 parse, string/drawable/action cross-check. Nothing compiled or run on a device.

## 2026-10-22 - UI polish (F-122)
- `BottomNav` rebuilt as liquid tabs (sliding glass indicator over a tab-row backdrop, drag to switch); `NavRail` for landscape/tablet (>= 600 dp wide and wider than tall); nav and mini player hidden while Now Playing is open; mini player uses `ContinuousCapsule`.
- `PullToRefreshBox` on Home (calls `refreshHome`), tap haptics in `clickableScale`, `Route.Driving` + `DrivingScreen` (opened from Now Playing > More controls, keeps screen on).
- Verification: kotlinc parse, string and action cross-checks only. Not compiled or run.

## 2026-10-22 - Kyant0 LiquidBottomTabs port (F-123)
- Ported the supplied catalog `LiquidBottomTabs` to `ui/glass/LiquidBottomTabs.kt` (glass panel + accent-tinted tab copy + draggable lens indicator). The upstream helpers `DampedDragAnimation` / `InteractiveHighlight` were not supplied: `DampedDragAnimation` was rewritten (`ui/glass/DampedDragAnimation.kt`), `InteractiveHighlight` is omitted. `com.kyant.shapes.Capsule` replaced by Capsule's `ContinuousCapsule`. Glass settings (blur, lens, refraction, tint, saturation, shadow) drive the look; solid `SimpleBottomNav` stays as the fallback when glass is off.
- Verification: kotlinc parse only. Not compiled or run.

## 2026-10-22 - First real build result (T07 partial)
- MobileIDE assembleDebug (Gradle 8.14, compileSdk 36): the vendored Backdrop/Capsule code and the Compose 1.7.5 bump compiled; only 7 errors, all in new files: missing `positionChange` import, `Int.fastCoerceIn` (does not exist in Compose 1.7.5 -> `coerceIn`), `graphicsLayer(colorFilter=)` (not in 1.7.5 -> `drawWithContent` + `saveLayer`), wrong `PlaybackUi` package in `DrivingScreen`, and `driving_*` strings missing from the user's copy of `strings.xml` (their resources were UP-TO-DATE, i.e. strings.xml was not replaced). All fixed; rebuild pending.

## 2026-10-22 - Runtime crash: lens before layout
- Device crash `IllegalStateException: Size is unspecified` from `LensKt.getCornerRadii` (called from `LiquidBottomTabs`): effects run once before the first layout. Fixed at the source: vendored `lens()` now returns when `size` is unspecified (the earlier guard in `glassSurface` stays). Build itself passed (assembleDebug) before this crash.

## 2026-10-22 - Tab swipe fix
- Owner: tap works, swipe does not. Cause (likely): the indicator moves under the finger, so pointer positions local to it under-report the finger movement and the drag never reached half a tab. `DampedDragAnimation` now measures movement in stable coordinates (local x + the indicator's current x). Not run on a device.

## 2026-10-22 - Now Playing artwork + ambience (F-124)
- Artwork: new `ArtworkSize.Player` (1024 px) for the full-screen player; Google-hosted thumbnails are requested at the drawn size (`=wN-hN`), YouTube `hqdefault` upgrades to `maxresdefault` first (falls back). Previous/next cards are invisible at rest (alpha 0) and only show while swiping toward them.
- Ambience: setting `ambience` (Settings > Appearance > Song ambience, default on). Background = canvas + vertical fade from the top + radial glow centred on the top edge, coloured from the artwork tint; off = plain canvas.
- New strings live in `values/strings_ambience.xml`. Not compiled or run.

## 2026-10-22 - Ambience is app-wide (F-124 correction)
- Owner meant the whole app, not only Now Playing. `GlassAmbient` (the root backdrop behind every screen) now paints the playing song's artwork colour as a wash from the top edge (vertical fade + radial glow at top centre), animated per song, fallback accent when nothing plays. Setting renamed "App ambience" (Appearance). The root layer is drawn when glass OR ambience is on. Now Playing keeps its own, stronger wash. Not compiled or run.

## 2026-10-23 - nav-bar bottom bar + ambience-off + Settings hub (F-125)
- Bottom bar replaced by the owner's nav-bar `FloatingTabBar` (floating glass bar, draggable puck) with the mini player as its accessory; it collapses inline (artwork + play/pause) when scrolling down. Glass off or unsupported (API<31/low RAM): docked bar (`DockedNavBar`) with the mini player on top. Wrapper: `ui/glass/nav/DopavinNavBar.kt`; `BottomNav` maps destinations to it. Landscape keeps `NavRail` and a standalone mini player.
- Glass settings are mapped to the nav-bar `GlassEffectConfig` in `MusicApp` (blur/8, lens/30, refraction*1.2, tint*2.7, highlight*0.9167). Removed `LiquidBottomTabs`/own `DampedDragAnimation`; the owner's backdrop/shapes/interaction packages replaced mine (lens size guard re-applied).
- Ambience off: `GlassAmbient` now draws only the plain canvas (the two accent blobs were the red tint).
- Settings hub: category cards removed (rows only), icons use `textPrimary` instead of accent.
- Syntax-checked with kotlinc and internal imports cross-checked; NOT compiled against real libraries or run on a device.

## 2026-10-23 - Build fixes for vendored nav-bar bar (Compose 1.7.5)
- Build errors fixed: `InverseLayerScope` blendMode/colorFilter are plain vars (not in 1.7 GraphicsLayerScope); `sharedElement(sharedContentState=)` -> `state=` (1.7 name); `graphicsLayer(colorFilter=)` -> drawWithContent + saveLayer tint; `HapticFeedbackType.SegmentTick` (1.8+) -> `TextHandleMove`; K1 smart cast in `FloatingTabBar` inline tab. Not rebuilt yet.

## 2026-10-23 - Nav layout rework (F-126)
- Glass on: floating bar in portrait AND landscape (width capped at 600 dp). Tab group = Home + Library, Search is its own rounded standalone tab. Glass off: docked bar (Home/Search/Library) in portrait, `NavRail` in landscape (`railMode = !navGlass && landscape`).
- Nav package moved to `ui/glass/nav`; the old project name removed from code and docs.
- Red tint: besides the removed accent blobs, the no-artwork fallback header colours of Collection and Now Playing no longer use the accent colour (neutral surface mix). Not compiled or run.

## 2026-10-23 - Glass-off nav, Home glass buttons, Settings polish (F-127)
- Glass off: no docked bar any more. Portrait = floating mini player card + `SolidBottomNav` (plain pill, tinted indicator); landscape = `NavRail` + floating mini player. Glass on keeps the floating glass bar with the mini player inside it.
- Home: search and settings buttons are glass discs. Settings: search field replaced by shared `GlassSearchField` (also used by Search); hub rows re-spaced (icons aligned to the screen edge, no per-row gap). Not compiled or run.

## 2026-10-23 - Player panels, sleep dialog, no drag handles (F-128)
- Drag handles removed: slider thumb (`AppSlider`, so volume + EQ/settings sliders = plain filled track; `thumb = {}`), seek bar circle (track still thickens while dragging), and the sheet grab pill (the 20 dp strip is still draggable, just invisible).
- Lyrics and Queue no longer open sheets: they replace the artwork area on Now Playing (`PlayerStage`, tap the button again to go back; swipe-down-to-close is off while a panel is showing so lists scroll). `LyricsSheet` -> `LyricsPanel`, `QueueSheet` keeps its route/sheet for other callers and wraps the new `QueuePanel`.
- Equalizer button opens the Equalizer screen directly (`openEqualizer` closes the player first; back goes to the screen beneath). `SoundSheet` removed.
- Sleep timer = `SleepDialog`, liquid-glass dialog from the supplied Kyant0 sample, fixed look (no settings), drawn in the MusicApp overlay layer; `SleepSheet` removed. Not compiled or run.
- Build fix: `AppSlider` opted in to ExperimentalMaterial3Api (custom thumb/track). Not rebuilt.

## 2026-10-23 - Subscriptions, feed cards, word-synced lyrics (F-129..F-131)
- Subscriptions: `Song.channelUrl` (from `StreamInfoItem.uploaderUrl`, persisted in online metadata / Home cache / feed cache); `SubscriptionStore` (SharedPreferences); `OnlineRepository.channelInfo/channelUploads` (`ChannelInfo.getInfo` + `ChannelTabInfo.getInfo` on the VIDEOS tab, TRACKS/first tab as fallback; API names checked against the v0.26.5 sources). VM: `subs` flow, `toggleSubscription/unsubscribe/openSubscriptions/refreshSubscriptions`; feed = round-robin merge of up to 12 uploads per channel, max 60, refreshed when older than 30 min or on pull-to-refresh. Subscribe lives in Now Playing > More (online songs only). `Route.Subscriptions` + `SubscriptionsScreen` (channel avatars -> channel uploads via `Route.OnlineList(name, "ch:<url>")`, big cards for the newest 3, rows for the rest, unsubscribe buttons at the bottom).
- Home: new `FeedBigCard` (16:9, title/artist over a gradient, duration chip) for the Subscriptions and Trending shelves; related / artist / downloaded / history keep the small square cards; a tip card shows until the first subscription.
- Lyrics: `LyricLine.words` + `LyricWord`; `parseTtml` keeps `<span>` word timing (background vocals skipped); provider order is now Better Lyrics (6 s timeout) then LRCLIB; cache JSON v2 with words (older entries refetched once). `LyricsPanel` rewritten: 26 sp bold lines, karaoke colour sweep per word on the active line, count-in dots for gaps >= 6 s, alpha-mask edge fades (works on the player background), no header.
- Not added: other YumaPlayer providers (KuGou, SimpMusic, Unison, YouLyPlus) - APIs not verified; translation/romanisation and quote cards. YumaPlayer is GPLv3 (like NewPipeExtractor, so the app is already GPL); no code was copied.
- New strings in `values/strings_feed.xml` (copy the new file). Not compiled or run; syntax-checked with kotlinc only.

## 2026-10-23 - Artist navigation (F-132)
- Home: "Your top artists" row of round avatars (newest played song's artwork per top-12 artist, `HistoryStore.topArtistSongs`, local so it works offline). Now Playing: the artist name under the title is a tap target. Every song action sheet gets "Go to artist" (in the shared `SongActionsMenu`).
- All three call `MusicActions.openArtistOf(song)`: closes Now Playing/Queue first, then opens the YouTube channel page (`OnlineList(name, "ch:<url>")`) when the channel is known (also looked up from the online metadata cache), else the library artist page (`Route.Artist`), else an online search for the name. No subscribe button on the artist page yet. New strings in `values/strings_feed.xml`. Not compiled or run.

## 2026-10-23 - Artist profile + Library hub (F-133, F-134)
- `OnlineRepository.artistPage/playlistSongs`, `ArtistUi` + `MusicViewModel.artist`, `Route.ArtistProfile/Downloads`, actions `openArtistProfile, toggleChannelSubscription, openDownloads, openHistory`; `"pl:"` and `"hist:"` branches in `openOnlineList`. `openArtistOf` and the subscription bubbles now open the profile. New screens `ArtistProfileScreen`, `LibraryHubScreen`; `LibraryScreen` got a back arrow and title "Downloads". Strings in `values/strings_library.xml`. Verified: kotlinc syntax, string/drawable/action cross-check. Not compiled or run.

## 2026-10-24 - Lucide icons + press feedback (F-135)
- All 45 `ic_*.xml` drawables replaced by Lucide (ISC, `docs/licenses/lucide-ISC.txt`): stroke 2, round caps, white, tinted at the call site; play/pause/skip/heart(liked) are filled Lucide shapes. New `ic_audio_lines`, `ic_shield_check`; Settings hub uses headphones / audio-lines / download-less groups as requested (cloud, palette, droplets, shield-check, info). Nav icons 24dp like everywhere else. Accent stays limited to selected nav, active toggles, current song, enabled states (audit: no change needed). Ripple (grey press tint) removed from the glass tab bar and the sleep dialog buttons; other controls already use scale-only feedback. Icons are never inside blur layers. Not compiled or run.

## 2026-10-24 - Artist identity everywhere, short song list, quieter Settings (F-136)
- `ArtistDirectory` (SharedPreferences) + `OnlineRepository.artistIdentity/peekIdentity`: channel url + real avatar per artist name, from `ChannelInfo` or a NewPipe channel search (`YoutubeSearchQueryHandlerFactory.CHANNELS`). Home top artists show that photo (`HomeUi.avatars`, filled in as lookups finish).
- Every artist entry (Now Playing name, song menu, Home bubbles, Library/Search artist lists, downloaded songs) now calls `openArtistProfile`; blank url = look up by name; offline falls back to the local songs of that artist. `Route.Artist` is no longer pushed.
- Artist profile shows 6 songs + "More" button. Settings: removed the explanation texts (hub summaries, toggle/row subtitles, notes, quality hints); status notes (unsupported, credit) and confirm dialogs stay. Not compiled or run.

## 2026-10-24 - Settings redesign, About, artist glass (F-137, F-138)
- `SettingsScreen` hub regrouped (3 groups, big heading, glass back, search with empty-state "Clear search"); `AboutScreen.kt` new; `ConfirmDialog` is shared and takes a confirm label; `TonalButton(accent = true)` = accent-tinted glass; artist hero overlays name + glass buttons; new icons `ic_code`, `ic_scale`; strings in `values/strings_settings.xml`. Sub-pages keep their controls (already grouped) and get the big heading. Not compiled or run; no preview image was attached, so the layout follows the text brief only.

## 2026-10-24 - Glass dialogs everywhere (F-139)
- `components/GlassDialog.kt`: `GlassDialog(...)` (portal to `DialogLayer` in MusicApp's overlay layer, so it blurs the screen behind; solid card without glass), `DialogPillButton`. Replaced every `AlertDialog` (settings confirms, licences, delete downloads/playlist, queue clear, playlist name, add-to-playlist picker). Sleep dialog keeps its own copy of the same look. Fixed values, not customisable. Not compiled or run.

## 2026-10-24 - Artist blend, fixed back, open-source list (F-140)
- Artist photo fades into the page with a 5-stop gradient; the back button is now outside the scrolling list (fixed). About: "Open-source projects" list (11 public repositories, each opens in the browser). Not compiled or run.

## 2026-10-24 - Lyrics: providers, race, karaoke, controls (F-141) + Home playback fix (F-142)
- `LyricsProvider.kt` (interface, Better Lyrics + LRCLIB providers, matcher, HTTP seam), `LyricsRace.kt` (parallel race, first valid match wins, plain->synced upgrade within 1.5 s, timeout 10 s, cancellation), `MiniJson.kt` (JVM-testable JSON), `LyricsRepository.kt` (cache per song+provider choice, versioned v3, 90 d found / 7 d none; failures never cached; one-off provider switch; per-song offset store), `Lyrics.kt` (enhanced LRC, SAX TTML, `wordProgress`). Setting `lyricsProvider` (choose / better / lrclib) in Settings > Streaming & lyrics; lyrics panel shows "via <provider> - <timing>", source switch dialog, per-song timing dialog, retry.
- Only two providers are real: Better Lyrics and LRCLIB. The other YumaPlayer providers (KuGou, SimpMusic, Unison, YouLyPlus) were NOT added: SimpMusic documents no public API for third parties, the rest are unofficial/undocumented.
- Tests (app/src/test .../Lyrics*Test.kt, 39 tests) were compiled and run on the JVM with JUnit: all passed. Gradle build and device playback not run.
- F-142: songs played from Home history / subscription feed / cached shelves were unknown to the player (no artwork, like, download in Now Playing); every play/queue entry now registers online songs (`OnlineRepository.remember`).

## 2026-10-24 - More lyrics providers (F-143)
- Added real providers (own code; YumaPlayer, GPL-3.0, read only for API facts): BetterLyrics Portato (`lyrics-api.boidu.dev/qq/getLyrics`, same TTML parser as Better Lyrics), LyricsPlus (mirror list, `v1/ttml/get` then `v2/lyrics/get` JSON with syllable times, max 4 requests), Unison (`unison.boidu.dev/lyrics`, text sniffed as TTML/LRC/plain, song/artist verified), KuGou (song search -> lyric candidates by hash -> base64 LRC download, duration within 8 s, credit lines dropped). New file `data/LyricsProvidersExtra.kt`; ids/capabilities in `LyricsProviders`; wired into `LyricsRepository`, Settings picker, Now Playing source dialog; strings in `values/strings_lyrics_providers.xml`. "Choose" races all 6 providers.
- NOT added: "lrc.red + BiniLyrics" - no verifiable API contract found; no stub was created.
- Tests: 49 JVM unit tests (10 new, fake HTTP) pass. Not run: Gradle build, live endpoints (sandbox cannot reach them), device. Mirrors/endpoints can change or go down; failures degrade to the other providers.

## 2026-10-24 - Search redesign + folded tab bar fix (F-144)
- Bug: with Search (standalone tab) selected, the folded tab bar lost the Home/Library icon. Cause: `FloatingTabBarScopeImpl` is rebuilt every recomposition, so its remembered inline tab was lost. Fix: `DopavinNavBar` remembers the last group tab and passes it as `inlineFallbackKey`.
- Search screen: filter chips (All/Songs/Artists/Albums/YouTube), albums section (was missing), "Did you mean" banner (`data/SearchAssist.kt`: accent-insensitive, swap-aware edit distance; whole-name then word-by-word repair; vocabulary = library titles/artists/albums + recent searches only; shown only when the library has no match), recent searches (`SearchHistoryStore`, 12 max, committed on keyboard Search or opening a result, remove one/clear all), new icon `ic_history` (Lucide). Strings in `values/strings_search.xml`.
- Not done: suggestions for YouTube-only queries (would need an unverified external API).
- Verified: 7 new JVM tests (56 total pass), syntax + resource cross-checks. NOT verified: Gradle build, on-device look/behaviour.

## 2026-10-24 - Lyrics race: synced always wins, plain is last resort (F-145)
- `RaceConfig.upgradeGraceMs` now defaults to the overall timeout: a plain-text result is held until every provider answered (or the 10 s timeout), and any synced result (word or line) replaces it. Plain wins only when no provider delivered synced lyrics. First synced arrival still ends the race at once. Trade-off: songs that exist only as plain text now show after the slowest provider (max 10 s) instead of after 1.5 s.
- Tests: 3 new race tests (59 JVM tests pass). Gradle build and device not run.

## 2026-10-24 - Search screen visual redesign (F-146)
- Landing: recent searches as glass pills (tap = search, x = forget), 2x2 browse tiles with live counts (songs, artists, liked, newest), popular artists row. Fix: "Popular artists" read the (empty) search results and never showed; it now reads the whole library.
- Results: "Top result" hero card (artist/album named like the query, else first song; round art for artists, play button for songs), then artists, albums, first 6 songs + "Show all N songs", YouTube with skeleton rows while loading. Chips and "Did you mean" (F-144) kept.
- Files: `ui/screens/SearchScreen.kt` (rewritten), `MusicApp.kt` (passes `visible`), strings in `values/strings_search.xml` (+ plural `artists_count`).
- Verified: syntax parse of the whole app, string/drawable/plural cross-check. NOT verified: compile against Compose, how it looks on a device.

## 2026-10-24 - Home screen redesign (F-147)
- Reused: every data source and action (HomeUi: trending, related, artist shelves, subscriptions feed, play history, top artists; `refreshHome`, playback actions, existing shelf/cards/menus). No new feed, repository or request; nothing removed.
- Header: compact brand mark + greeting (headline instead of display) + settings; the big search icon became a full-width glass search pill that opens the existing Search tab.
- Quick access row (glass pills, existing destinations only): Liked, Playlists, Downloads (only when songs exist), Recently played, Subscriptions - with counts where real.
- "Continue listening" header over the quick-picks grid (recent plays topped up with newest downloads). Mood chips: glass/solid tonal pills instead of rainbow gradients. `SectionHeader` "See all" now has a chevron (shared, also affects other screens).
- Glass vs solid: all new surfaces use the shared `glassSurface(..., fallback = surface colour)`, so non-glass mode shows solid theme-coloured surfaces; no new hard-coded colours (white text stays only on artwork/gradient tiles as before).
- "Music Collections" section not added separately: it would duplicate the quick-access shortcuts (same data).
- Verified: syntax parse + resource cross-check. NOT verified: Gradle build, look in glass/solid and light/dark on a device, 200 % font.

## 2026-10-24 - "More" menus are anchored dropdowns, not bottom sheets (F-148)
- Owner request supersedes F-109 ("no dropdowns"). New `components/AnchoredMenu.kt`: `AnchoredMenu` (Kyant0 `ContinuousRoundedRectangle` card, capsule-highlighted rows `AnchoredMenuItem`), `MenuAnchor`. Opens just below the button that opened it; if there is not enough room below it opens just above, always beside the button, clamped to the screen with a 12 dp margin; scrolls when taller than the free space. Liquid Glass on: same blur/lens card as the glass dialogs; off: solid themed card with hairline edge + shadow. Scrim tap and Back close it.
- How the anchor is known: every button goes through `clickableScale`, which records the pressed element's window bounds in `MenuAnchor` just before calling its handler (also for long-press); `SheetHost.show` and the Now Playing menu pick it up (valid 2 s, otherwise the menu is centred).
- Covered automatically: every song row/card/queue entry menu (`SongActionsMenu`), Library sort, Home cards/tiles. Now Playing "More" (speed, skip silence, output, driving mode, subscribe, radio, details, download) is now a 320 dp dropdown under the More button (`MoreSheet`).
- Other sheet identified and kept: the Queue (`QueueScreen` -> `BottomSheet`, 88 % height) is a full reorderable list page, not a menu, so it stays a sheet. No other `BottomSheet`/`ModalBottomSheet`/`DropdownMenu` users exist. Dialogs unchanged.
- Verified: syntax parse of whole app. NOT verified: Gradle build, placement on a device (below/above/edges), glass look, 200 % font, TalkBack.

## 2026-10-25 - Home redesigned again, properly (F-149; replaces the F-147 layout)
- Owner feedback: F-147 kept the old structure and used an invented logo. Now: the header shows the REAL app logo (launcher mipmap background + foreground, rounded), not a redrawn mark.
- New structure (same data and actions, nothing removed): header (logo, greeting, search + settings glass buttons) -> full-bleed artwork hero (continue / shuffle-all / discover) -> round shortcuts (Liked, Playlists, Downloads, Recently played, Subscriptions) -> "Continue listening" poster cards (tall artwork cards, long-press menu) -> moods in two scrolling rows -> top artists with accent ring -> subscriptions feed (big cards) -> trending as a ranked 1-5 list ("See all" opens the full list) -> related / downloaded / artist shelves -> genres as a 2-column tile grid -> recently played shelf.
- Removed pieces were only layout widgets (search pill, pill shortcuts, 2-column quick-pick grid, rainbow mood row, horizontal trending cards); trending's other cards remain reachable through "See all".
- Verified: syntax + resource cross-check. NOT verified: Gradle build, device look (glass/solid, light/dark), 200 % font.

## 2026-10-25 - Artist photo blends into the body (F-150)
- Cause of the visible edge: the photo faded to a painted solid `canvas` colour, while the page behind the list is the ambient-tinted background, so the two never matched. Now the photo itself is faded with an alpha mask (offscreen layer + DstIn gradient, 8 soft stops), so its lower part dissolves into whatever the page really shows (ambient, solid, light or dark). `ArtistProfileScreen.kt` only.
- Verified: syntax parse. NOT verified on device (look with bright photos, name legibility, light theme).

## 2026-10-25 - Cleaner titles and no non-song videos on artist pages (F-151)
- `data/TrackNames.kt` (pure Kotlin, 8 JVM tests): `clean(title, artist)` removes junk-only brackets ("(Official Music Video)", "[4K]", "【MV】"), the "| Artist | Label" tail, trailing "Official/Lyrical/Full Video", hashtags, and the "Artist - " prefix (or " - Artist" suffix) when it equals the uploader (a "- Topic"/VEVO suffix on the channel name is ignored). Bracket tags that mean something (remix, live, acoustic, cover, version, slowed, ft./feat., from...) are kept; a result shorter than 2 characters falls back to the original title.
- `looksLikeSong(title, seconds)`: rejects interviews, podcasts, vlogs, behind-the-scenes/making-of, reactions, trailers/teasers/promos, streams, jukebox/non-stop/top-N compilations and anything over 15 minutes. Applied to the artist page and the channel uploads feed (subscriptions), not to YouTube Music search (already music-filtered).
- Titles are cleaned where online songs are created (`toSong`), so Home, Search, queue, lyrics lookups and downloads all show/use the clean title; the video id is unchanged.
- Heuristic only: a real song whose title contains a word like "story" or "live stream" may be hidden from an artist page; songs cached before this change keep their old title until fetched again. Not verified: Gradle build, live NewPipe results.

## 2026-10-25 - Artist channels in Search, genuine channels, local re-uploads demoted (F-152)
- `data/ArtistPick.kt` (pure Kotlin, 6 JVM tests): name match (exact > starts-with > contains; "- Topic"/VEVO/Official suffix ignored), then verified and subscriber count (log scale); a channel without the name is never picked. `uploaderTier` + stable `demoteUnofficial`.
- `OnlineRepository.search`: results from verified / Topic / VEVO / Official uploaders come first, other uploaders after them (demoted, not removed). `searchArtists` (MUSIC_ARTISTS filter, CHANNELS fallback) and `genuineChannel(artist)` (remembered, else best pick).
- `openArtistOf(song)` for online songs now opens the genuine channel when one carries the artist's name, else the uploader channel as before. Songs on the page come only from that channel.
- Search: `OnlineUi.artists`; artist channels are searched in parallel with songs and appear in the Artists section (avatar + name, tap opens the artist page).
- Unverified: Gradle build, live NewPipe results (verified flag / subscriber count may be missing for some channels, then name match decides), device behaviour.

## 2026-10-25 - Karaoke for line-synced lyrics, smoother sweep, lyrics in Driving mode (F-153)
- `data/Karaoke.kt` (pure Kotlin, 6 JVM tests): `words()` keeps provider word timing; for line-synced lines it estimates word times by spreading the line span over words by length (a long pause after the line is not stretched). `charProgress()` is an eased, overlapping per-character sweep.
- `LyricsSheet.kt`: active line uses `karaokeText` (per-character colour sweep); position is sampled every frame (`rememberFramePosition`) instead of every 250 ms.
- `DrivingScreen.kt`: previous / current (karaoke) / next line between title and controls; only for synced lyrics, loaded only when Now Playing has not loaded them. `RouteContent` gets `currentSong` and `lyrics`.
- Estimated timing is a guess: the highlight can run ahead or behind the real singing on line-synced songs. Unverified: Gradle build, on-device smoothness/battery, Driving layout on small screens.

## 2026-10-25 - Karaoke no longer breaks Hindi and other scripts (F-154)
- Cause: F-153 coloured every UTF-16 char separately, so a vowel sign (matra), anusvara or conjunct got a different colour from its base letter.
- `Karaoke.units(word)`: base + marks (Mn/Mc/Me), virama conjuncts (Devanagari, Bengali, Gurmukhi, Gujarati, Odia, Tamil, Telugu, Kannada, Malayalam, Sinhala, Myanmar, Khmer, Thai phinthu), ZWJ/ZWNJ, variation selectors and emoji skin tones stay in one piece; words in cursive scripts (Arabic, Syriac, N'Ko, Mongolian) are one piece. Sweep and the time estimate use these pieces.
- Not verified on device: rendering of each script. Indic scripts with reordering vowels (e.g. Hindi "ि") are kept with their base letter by design.

## 2026-10-25 - YouTube radios on Home (F-155)
- `OnlineRepository.radio(videoId)`: NewPipe `PlaylistInfo` on `watch?v=ID&list=RDID` (the mix extractor), first page + up to 2 more, max 50 songs, titles cleaned, seed first. Empty on failure.
- Home: "Radios" shelf (only while online features are on): up to 6 seed songs, one per artist (most played, then recent, then trending); card = seed thumbnail + radio badge + "<Artist> Radio". The mix itself is fetched when a card is opened (not for every card on Home, to save data).
- Opens in the existing online list screen (`RADIO_QUERY_PREFIX`); that screen now has Play, Shuffle and "Save as playlist" for every online list (shelves, albums, radios). Saving uses the list title as playlist name; if the name exists the existing "name taken" message shows.
- Unverified: Gradle build; live mix results (some regions need YouTube consent for mixes - then the list shows the usual unreachable line); device UI.

## 2026-10-25 - Playlist cover page and HD download artwork (F-156)
- Playlist screen: artist-style hero (square cover with the same alpha fade, name, count, Play / Shuffle, floating back button), songs below, Delete at the end. Cover = YouTube cover when known, else a 2x2 collage of four different song covers, else the first song's cover, else the initials placeholder.
- NewPipe: `OnlineRepository.playlist()` and `radio()` return `Listing(songs, cover)` (`PlaylistInfo.getThumbnails`; a mix gets the seed video's thumbnail). `OnlineListUi.cover` -> "Save as playlist" passes it on (`createPlaylist(name, songs, cover)`).
- Storage: covers live in their own file `playlist_covers.json` (name -> url); `playlists.json` and backups are unchanged, so old playlists are untouched. Backups do not carry covers (collage is used after a restore). Deleting a playlist removes its cover.
- Downloads: `ArtworkUrls.candidates` tries a large version first (Google image hosts rewritten to 1200x1200; ytimg `maxresdefault`, then `sddefault`), then the original; a candidate counts only with HTTP 200 and at least 5 KB. Only new downloads get the better cover; existing downloads keep theirs.
- Unverified: Gradle build, whether each host really serves the larger size (maxres does not exist for every video; the fallback handles that), device look of the cover page.

## 2026-10-25 - Smart lyrics fallback (F-157)
- Kept: providers, race ([raceLyrics], synced beats plain), provider choice (choose / one provider), cache layout, retry, loading and empty states, the screen. Nothing there was rewritten.
- New `LyricsQueryPlan` (pure): reads a raw title into clean title, movie/album bracket groups, featured artists and version tags; builds up to 7 ordered queries: exact title + artist, cleaned + artist, cleaned only, title + album/movie, other artist/title combinations (first of several artists, "Artist - Title" both ways), the rest (version tag kept). Generic, nothing is hard-coded; the song's own metadata is never changed (`rawTitle` rides along in every query).
- New `LyricsFallback` (pure, fakes in tests): runs the plan step by step over the allowed providers (all for "choose", only the picked one otherwise - never silently switched). A provider that failed (network, rate limit) is dropped for the rest of the song, so an outage or a limit is never hammered; providers that need an artist (LyricsPlus, Unison) skip artist-less queries; a provider's "no lyrics" is remembered 6 h per exact query (`MissCache`, in memory; the retry button ignores it); 25 s total budget; artist-less queries only accept a close title match (`StrictGate`, confidence >= 0.75).
- `LyricsMatcher`: "love" no longer matches "love story" (containment needs 2+ words and 40% length); remix / karaoke / instrumental / cover / mashup / slowed / reverb versions never stand in for each other; live / acoustic / remaster only cost a small penalty. Title case, spaces and punctuation are still tolerated.
- `LyricsRepository`: `load` uses the plan + fallback; `SingleFlight` makes two callers for the same song and choice share one search (the work runs in the repository's own scope, so a cancelled waiter does not cancel it). Older "no lyrics" cache entries are ignored (they came from a single query); found lyrics stay cached. The view model's token check still drops a late answer for an old song (not unit-tested: the view model is not JVM-testable here).
- Tests: 30 new (plan, matcher, fallback, single flight); 119 pass in total. Not verified: Gradle build, real providers (no network in the sandbox), the extra request load on a real "no lyrics" song (up to 7 queries per provider that is still eligible, minus misses and artist-less skips).

## 2026-10-25 - Song details dialog and marquee (F-158)
- `SongDetailsDialog` replaces the details screen (the route is gone): opens over the current screen, closes with Done or back. `MusicActions.closeSongDetails()`.
- `MarqueeText`: one line that scrolls sideways only when it does not fit. Used for the Now Playing title (was two lines, now one scrolling line), the mini player title and the title/artist in the details dialog.

## 2026-10-26 - Queue artwork, toast, search dedupe (F-159, F-160)
- Queue artwork was missing because the lookup only knew local songs; `QueueSheet` now gets local + `onlineSongsById()`.
- Snackbar replaced by `ToastState`/`ToastHost` (`ui/components/Toast.kt`): 3 s (5 s with an action), newer notice replaces older, swipe down dismisses, glass card or solid card with hairline by theme. Cause of the stuck Undo: Material `showSnackbar` with an action is Indefinite by default.
- `TrackMatch` (pure): `dedupe` (title + main artist + version words + length within 3 s), `rank` (originals first unless the query asks for the version), `sameChannel`. Used in `OnlineRepository.search`; versions are demoted, never hidden.
- `artistIdentity`: removed the "first channel result" fallback; no exact-name channel now means no artist page instead of a wrong one.
- Tests: 10 new, 129 pass. Not verified: Gradle build, live NewPipe, toast visuals on device.
- Not done (deferred): "Load more" paging for search/artist tabs/playlists and albums via /releases; needs live NewPipe checks I cannot run here.

## 2026-10-26 - Cover-page layout for radio list, scrolling Downloads header (F-161)
- Build of F-149..F-160 confirmed successful by the owner.
- `CoverHero` + `meltIntoPage()` (components/CoverHero.kt) = the artist-style header. `OnlineListScreen` uses it with `PlaylistCover` (now internal): YouTube cover or collage of the songs. Back is a fixed glass `IconAction` over the page.
- `LibraryScreen` (Downloads): title row, tabs and active downloads are now the first list item and scroll away; back is fixed glass. Alphabet jump index shifted by one for the extra item.
- Not verified: Gradle build of this change, on-device look (glass on/off, 200% font).

## 2026-10-26 - Real glass back buttons, scrolling settings titles (F-162)
- Why the back button looked flat: pages sampled `ambientBackdrop` (canvas + wash only). Pages cannot sample their own layer, so `FloatingBackButton` (components/FloatingBack.kt) only registers the click with `FloatingBackState`; MusicApp draws it once in `FloatingBackLayer`, above the content box and below the player, with `overlayBackdrop` (ambient + page content). Handover between pages uses an owner token so the cross-fade cannot clear the new page's button.
- Used by OnlineList, Playlist detail, Artist, Downloads, Settings (all categories incl. Glass & Style / About), Collection, and `ScreenTopBar` (Subscriptions, Storage, Equalizer, Suggestions).
- Settings: title, search and content are one scrolling column; back stays fixed.
- Now Playing: the player background (canvas + ambience wash) moved into its own `layerBackdrop`; its glass buttons (close, more) sample that.
- Not verified: Gradle build, on-device look, performance of the extra backdrop layer.

## 2026-10-26 - Crash report screen (F-163)
- `DopavinApp` installs `CrashReporter` (uncaught-exception handler). It writes `files/last_crash.txt` (time, app version, device, Android, thread, full stack with causes; cut at 60k chars), starts `CrashActivity`, then kills the process. A second crash inside the handler falls through to the system handler.
- `CrashActivity`: plain views, own process (`:crash`) so it works even when Compose/theme code was the crash. Copy, Share, Restart. Reads and deletes the file on open. If it could not open (background crash), `MainActivity.onCreate` opens it on the next start.
- No new dependency. Strings in `strings_crash.xml`.
- Not verified: Gradle build, an actual crash on a device (Android may block starting an activity from a background crash; the next-start fallback covers that).
- F-163 fix: the owner reported the crash screen itself crashed on device (cause not seen, no log). `CrashActivity` now builds the screen inside try/catch; on failure it shows the report plus its own error as plain selectable text, so the real cause becomes visible. The report is read defensively; the launch no longer uses CLEAR_TASK. Root cause still unverified.

## 2026-10-26 - Crash fix from the owner's tombstone, debug crash log, titles, predictive back (F-164..F-167)
- Tombstone: SIGSEGV in RenderThread, `RenderNode::prepareTreeImpl` recursing (stack overflow, 512 frames). Cause: F-162 drew `FloatingBackLayer` inside the Box recorded by `layerBackdrop(contentBackdrop)` while sampling `overlayBackdrop` (contains that layer), so the layer contained itself. Fix: the layer now sits in the "floats over the screens" block, outside the recorded Box, and is hidden while the player is open. RULE: anything that samples `overlayBackdrop`/`contentBackdrop` must be drawn outside the content Box.
- Native crashes (signals) cannot reach the Java handler, so no crash screen for them; that is why the screen never appeared.
- `CrashReporter` keeps up to 30 reports in `files/crash_history` when the app is debuggable (`FLAG_DEBUGGABLE`, no BuildConfig needed); Route.CrashLog / `CrashLogScreen`; row in Settings > About only in debug builds.
- `ScreenTopBar` removed; `ScreenHeader` (components/TopBar.kt) = fixed glass back registered via `FloatingBackButton` + title/subtitle/actions as page content. Used by Subscriptions, Storage, Playlists, Equalizer, Suggestions, Crash log; Downloads and Settings use the same layout by hand. Artist/Playlist/Radio pages keep the cover hero.
- Predictive back: `android:enableOnBackInvokedCallback="true"`, `PredictiveBackHandler` in MusicApp; page container gets scale/translate/alpha/rounded clip from `backProgress`; player and queue just close. Dialogs/sheets keep their own BackHandler (they win). Settings category pages (inner BackHandler) are not animated.
- Not verified: Gradle build, device behaviour of any of this (especially that the renderer crash is gone and the gesture feel).

## 2026-10-26 - Tint, dynamic tint, GitHub links, UI rules, iOS back (F-168..F-171)
- `AccentTint` (data, pure, 6 tests): presets, HSV derive of accent / gradient ends / on-accent ink / focus ring for dark and light (light accent darkened to about 3:1), `baseFor` maps the setting (0 default, 1..8 preset, -1 dynamic). Setting `accentTint` in SettingsStore (`accent_tint`). `MusicTheme(accentTint, artworkTint)` overrides the accent tokens and fades 450 ms; MainActivity feeds the playing song's artwork colour only for the dynamic option. Settings > Appearance > `TintPicker`. It changes the accent token only (buttons, selected states, sliders, gradients, accent icons); plain icons keep their text colour on purpose.
- About: `about_source_url` = https://github.com/playboy-gg/Dopavin, `about_profile_url` = https://github.com/playboy-gg. The profile page loaded; the repository page could not be fetched from the sandbox (not created yet or private), so that link is unverified.
- Predictive back rewritten: underlay (previous page, parallax -28% to 0, dim 28% to 0, its `FloatingBackButton` disabled via `LocalFloatingBackEnabled`) + top page (translateX by progress, opaque canvas, edge shadow). On commit the swap has no cross-fade (`skipNextTransition`). The previous page rule mirrors `MusicViewModel.back()`.
- `docs/10_UI_RULES.md` added and linked in AGENTS.md.
- Tests: 135 pass. Not verified: Gradle build, device look of the tint, gesture feel (a second page composes during the drag; check smoothness), repo link.

## 2026-10-27 - HIG audit fixes 1-5 (F-172..F-175)
- `retryOnline()` (MusicActions/VM) re-runs the request of the open screen: online list, artist, mood, song radio (remembers the seed in `lastRadioSeed`), else the search (`searchRetry` flow joins the search combine). Open functions were split into `loadOnlineList/loadArtist/loadMood/loadRadio` with the bodies unchanged. `RetryHint` (components/States.kt) replaces the dead-end text on the 5 screens.
- Notification permission: `MainActivity` waits for the first `isPlaying` instead of asking in `onCreate`. Downloads alone do not trigger it (their notification is shown without the permission on Android 13+ only if granted; not changed).
- Reduce motion: `MarqueeText` (ellipsis, no scroll), `ToastHost` (fade only), page `AnimatedContent` (no cross-fade), predictive back (no translate; just goes back). Still NOT covered: karaoke sweep, tint fade, ambient fade, skeleton is already covered.
- Dialog pill buttons `heightIn(min = 48.dp)` (GlassDialog, SleepDialog); Home mood chips `heightIn(min = touchTarget)`.
- docs/11 problems 1, 2, 4, 5 fixed and 3 partly; 6-10 open. Not verified: Gradle build, device.

## 2026-10-27 - Swipe-back fixes (F-176)
- Reported: the back button stayed in place while the page slid, and the sliding page lost its ambience wash.
- Cause 2: the moving page got an opaque `colors.canvas` background (to hide the page underneath), which covered the root ambience. Now `Modifier.ambientBackground(wash)` (extracted from `GlassAmbient` in glass/Glass.kt) gives the moving page canvas + the same wash.
- Fix 1: `FloatingBackLayer` gets `translationX = direction * progress * screenWidth`; the underlay draws the previous page's own glass back button (only when the previous screen is a Route) so it arrives with the page.
- Smoothness measures already in: transforms are draw-phase only, no cross-fade on commit. Known cost: the previous page is composed when the drag starts (one-frame hitch on heavy pages such as Home). Not measured on a device.

## 2026-10-27 - Apple-style motion: player collapse and mini player (F-177)
- Source: the Apple design file is a catalog; its Motion entry only says "clarify, don't distract; honor Reduce Motion". The behaviour follows iOS sheet dismissal and rubber-band stretching, not a quoted Apple spec.
- `MusicApp`: `playerBack` Animatable follows the back gesture; Now Playing gets translationY = progress * height, scale down to 0.94, growing corner radius. Bottom bar and mini player are composed during the drag and fade in with progress. On commit the sheet finishes (220 ms), the exit transition is skipped, and `miniPulse` goes 0.955 to 1 with a spring (damping 0.42). Cancel springs the sheet back. Button-back (no gesture) runs the same animation.
- `MiniPlayer`: new `pulse` parameter (draw-phase scale of the content); swipe to change song drives `stretch` (-1..1): the card widens 4.5% and thins 1.5% from the edge opposite the finger, then springs back (damping 0.5) on release or after the song change.
- Reduce Motion: no player drag animation, no pulse, no stretch.
- Verified: kotlinc parse, no syntax errors. NOT verified: Gradle build, how the spring feels, glass sampling while the mini player is scaled.

## 2026-10-27 - Player hold distance, bars hidden until gone, back flash (F-178)
- Reported: held Now Playing should only cover 10-15 % of the screen height; release takes it fully down; bottom tab and mini player must not appear above it until it has gone; mini player then drops with a spring; the previous page flashed on the current one when going back.
- `playerOffset` (fraction of height): held = 0.12 x progress, release animates to 1. `barsReady` keeps the nav and mini player out until the player has left; `miniDrop` springs the mini player in from 24 dp above. Replaces the F-177 fade-in-during-drag and landing squash.
- Flash: back via the button used the screen cross-fade (both pages half visible). Now `FloatingBackLayer(intercept)` routes the button through `requestBack`, the same slide as the gesture. The commit also no longer resets progress before the new route is composed (`committedFrom` + `effBack`); the player waits until it is really closed before resetting.
- New `docs/12_MOTION_RULES.md`. Verified: kotlinc parse only. NOT verified: build, feel, whether the flash is gone (cause inferred from the code, not reproduced).

## 2026-10-28 - Motion tokens, maths and tests (F-179)
- Owner supplied an animation-maths blueprint (saved as `docs/13_ANIMATION_MATH_BLUEPRINT.md`). Applied its phase 1 to the new gesture code: `ui/motion/{MotionTokens,MotionMath,MotionSpecs}.kt`; `MusicApp` and `MiniPlayer` now read their durations, fractions and springs from there (same values as F-178, no behaviour change intended).
- Verified: 11 JVM tests pass (lerp bounds, pop duration, held offset 12 %, scale, corner, stretch, spring finite/converges/overshoot, damping ratio, timestep guard); kotlinc parse of the changed files. NOT verified: Gradle build, device feel.

## 2026-10-28 - Blueprint phase 2 and the back "flash" (F-180)
- Flash, most likely cause (inferred from the code, not reproduced): after a back the previous page was composed TWICE (once as the underlay, once again by `AnimatedContent`), so at the swap its images and list were rebuilt; on a plain forward/tab change `AnimatedContent` cross-faded both pages. Fix: the page host is now a keyed list of layers; the underlay page is the same composition that becomes the current page, so nothing is rebuilt at the swap. Forward/tab change still cross-fades, but the new page starts at alpha 0 from its first frame.
- Phase 2: single `sheet` value for Now Playing (see docs/12), drag-up on the mini player (`draggable` vertical in `MiniPlayer`, callbacks `onSheetDrag/onSheetRelease`), bars faded by `sheet`; removed `skipPlayerExit`, `barsReady`, `skipNextTransition`.
- Verified: kotlinc parse (no syntax errors; unresolved library symbols only), 11 motion tests still pass. NOT verified: Gradle build, feel, whether the flash is really gone, drag-up vs horizontal swipe vs tap conflicts on the mini player, `LocalFloatingBackEnabled` switching on the page that becomes current.

## 2026-10-28 - Build fix for F-180
- Build log: `MusicApp.kt` used `configuration` (defined further down) in the new `screenHPx`; now reads `LocalConfiguration.current` directly. The three "overload resolution ambiguity" errors came from that unresolved value. First real compile of F-161..F-180: this was the only error reported; a second pass may reveal more once this one is gone.

## 2026-10-28 - Search "Show more results" (F-181)
- `OnlineRepository.searchPage/searchMore` + `SearchCursor` (in memory): the first page is no longer cut to 10, and `SearchInfo.getMoreItems` continues it. `search(query, limit)` still exists for the other callers. `OnlineUi.hasMore/loadingMore`, `MusicActions.loadMoreOnline()`, button "Show more results" under the online songs (spinner while loading; on failure the same button retries). A newer search discards a late page (cursor identity check).
- Not done: playlist paging beyond 80 songs (the playlist reader already reads pages up to its limit); album fallback via MUSIC_ALBUMS (needs live NewPipe).
- docs/11: finding 10 withdrawn (queue rows have Move up / Move down).
- Verified: kotlinc parse only. NOT verified: Gradle build (`Page.url/ids`, `SearchInfo.getMoreItems` signature are from memory of NewPipeExtractor 0.26.5, no jar here), live behaviour.

## 2026-10-28 - Shared-element cover morph (F-182)
- Owner reminder: the artwork flight between the mini player and Now Playing was still open. Added `ArtworkFlight` (see docs/12). Modifiers `flightMini` / `flightPlayer` on the two real covers, `ArtworkFlightLayer` at the root after the bars, `MotionMath.flightFraction` (+1 test).
- Verified: kotlinc parse, 12 motion tests pass. NOT verified: Gradle build, how it looks (rectangles come from `boundsInRoot`, which includes ancestor layer transforms: recorded only when settled), flicker when the cover image is not cached yet.

## 2026-10-28 - Cover flight did not run (F-183)
- Owner: no flight at all. Cause (from the code): the big cover's rectangle was only recorded inside `onGloballyPositioned` when the sheet was 0, but the sheet moves the player with `graphicsLayer`, which does not trigger that callback; the player was laid out while the sheet was still 1, so `playerRect` stayed null and `flying` was never true.
- Fix: the cover keeps its `LayoutCoordinates`; the root reads its rectangle (`settlePlayer`) when the sheet reaches 0. The first open since app start still has no flight (nothing recorded yet); the first close and every later open/close fly.
- Verified: kotlinc parse only. NOT verified on a device.

## 2026-10-28 - Cover flight: slower than the sheet, reverse on open (F-184)
- Replaced the interpolation that started at the 12 % held range (and needed a hand-off to the real cover) with a linear flight over the whole sheet range; resting rectangle from `flightPlayerHost` (relative to the host box), so even the first open flies. `MotionMath.flightFraction` is now clamp01(sheet); test updated (12 tests pass).
- Verified: kotlinc parse, 12 JVM tests. NOT verified: Gradle build, look/feel (speed ratio is about 0.6-0.7 of the sheet by geometry, not tuned), `LayoutCoordinates.localPositionOf` behaviour with the sheet's layer.

## 2026-10-28 - Build fix for F-184
- Build log: `ArtworkFlight.kt` imported `toSize` from `androidx.compose.ui.geometry`; `IntSize.toSize()` lives in `androidx.compose.ui.unit`. Import fixed. Only these two errors were reported (the same symbol); a further pass may show more.

## 2026-10-28 - Cover flight gone again (F-185)
- Owner: with F-184 the flight vanished. F-183 did fly (the owner described its position problem), F-184 changed two things: the linear flight and the way the big cover's rectangle is recorded (relative to a host box). Most likely the second broke it (rectangle wrong or never set; not reproducible here). Reverted the recording to the F-183 way (`boundsInRoot` read when the sheet settles at 0, via `settlePlayer`), kept the linear flight. Removed `flightPlayerHost`.
- Consequence: the very first open after app start still has no flight (no rectangle yet); the first close and later ones fly.
- Verified: kotlinc parse only. NOT verified: build, device.

## 2026-10-28 - First-open cover flight (F-186)
- Task from the owner (detailed brief). Findings: the cover lives in `ArtworkStage` inside `screens/NowPlayingScreen.kt` (no separate file); its place depends on sibling layout, so analytic geometry was rejected. Compose source (NodeCoordinator/NodeChain) confirms the coordinate model written in docs/12. The F-184 host-relative idea was sound on paper; why the flight vanished on the device is NOT established (the same build also changed the flight maths). F-186 therefore uses both readings, validates them, enables logging, and honours Reduce Motion.
- Changed: `components/ArtworkFlight.kt` (updatePlayerRect, settlePlayer, validation, `enabled`, logs, `flightPlayerHost`), `MusicApp.kt` (host modifier, `enabled = !motion.reduce`).
- Verified: kotlinc parse only. NOT run: Gradle build, any of the 11 test cases (device needed). If the flight is still missing, the logcat lines tagged `ArtworkFlight` show which rectangle is missing or rejected.

## 2026-10-28 - Swipe-down drives the shared sheet (F-187)
- Root cause confirmed in code: `NowPlayingScreen` had its own `dragY`/`shownDrag` + `graphicsLayer { translationY }`; the root `sheet` (read by `ArtworkFlight`) was untouched during the drag. Also, on commit `actions.back()` -> `LaunchedEffect(playerOpenNow)` animated `sheet` from 0 while the screen still carried its own translation (a jump).
- Fix: removed `dragY/shownDrag/draggingDown/dismissPx/DISMISS_DISTANCE_DP`; new params `onDragSheet/onReleaseSheet/onCancelSheet`; root handlers `onPlayerDrag/onPlayerRelease/onPlayerCancel` (see docs/12). Gesture scope unchanged (`detectVerticalDragGestures`, only on the artwork stage with no sheet open), so horizontal artwork swipes, lyrics/queue and the More menu are untouched.
- Verified: kotlinc parse only. NOT run: Gradle build, the 10 validation cases (device), velocity-based fling (not implemented).

## 2026-10-28 - No bar recreation, cover landing spring (F-188)
- `MusicApp.kt`: removed the `!playerOpen` conditions on bottom bar, mini player and NavRail; added `barsPlaced` + `Modifier.placedWhen`; landing spring via `artFlight.land` (replaces the `miniDrop` effect; the `drop` parameter of `MiniPlayer` stays but is always 0). `ArtworkFlight.kt` (land scale on the mini cover), `MotionSpecs.landSpring`, `MotionTokens.CoverLandKick`.
- Risks to check on the device: touches while the bars are hidden, the mini-player drag-up gesture (bars must stay placed), glass sampling of bars that were just placed again, rail mode.
- Verified: kotlinc parse only.

## 2026-10-28 - Build fix for F-188
- Build log: `artFlight` was used (landing spring effect) before its declaration; moved the declaration next to `sheet`. Only these two errors were reported.

## 2026-10-29 - Settings back (F-189)
- Root cause: F-178 `FloatingBackLayer(intercept = requestBack)` ignores the page's registered click, so back from a category called `viewModel.back()` and left Settings; the old inner `BackHandler` was not predictive.
- Fix: `Route.SettingsPage(name)`, `MusicActions.openSettingsCategory`, `SettingsScreen(categoryName)`; removed local `categoryName` state and `BackHandler`; back is always `actions::back`, so the layered page slide/predictive back applies. The hub stays composed underneath (search text kept).
- Verified: kotlinc parse only. Not run: build, device. Check: Settings > Appearance > back button and device back, search then open category then back.

## 2026-10-29 - Share song and Recap (F-190, F-191)
- Share: `ui/components/ShareSong.kt` (`shareTextFor`, `shareSong`), entries in `SongActionsMenu` and the Now Playing More sheet, string `menu_share`. Text only, no permission.
- Recap: `data/ListenLog.kt` (store + pure `RecapMath`), `ui/screens/RecapScreen.kt`, `Route.Recap`, `MusicActions.openRecap/clearRecap`, VM `recap` flow, hub row `lib_recap`, `strings_recap.xml`. A listen is recorded in `judgeLeft` when the song is left after 30 s (half of a song shorter than a minute); a failing stream is not counted. Limitation: the song that is still playing is counted only when the player moves on from it, and a process kill loses it. Cap 3000 entries.
- Tests added: `RecapMathTest` (9 cases). Verified: kotlinc parse only. NOT run: Gradle build, unit tests, device.

## 2026-10-29 - Mobile quality, lyrics cache, lyrics card (F-192..F-194)
- F-192: `AppSettings.streamQualityMobile` (+ SettingsStore key), extension `AppSettings.effectiveStreamQuality(context)` (metered -> mobile choice), used by `PlaybackService` resolver and `prefetchTop`; Settings > Streaming picker. Downloads keep their own quality setting. Default High = same as Wi-Fi, so behaviour does not change until the user picks something.
- F-193: `StorageUi.lyricsBytes`, `MusicActions.clearLyricsCache`, row on the Storage screen.
- F-194: `ui/components/LyricsCard.kt` (`renderLyricsCard`, `shareLyricsCard`), `LyricsCardDialog` in `LyricsSheet.kt`, share icon in the lyrics controls, `<provider>` (androidx FileProvider, authority `${applicationId}.files`) and `res/xml/file_paths.xml` (cache `cards/`). No new dependency (FileProvider is in androidx.core). Cover is used only when already in the memory cache.
- Verified: kotlinc parse of every touched Kotlin file, XML well-formedness, no duplicate string names. NOT run: Gradle build, tests, device. Things to check on device: share sheet shows the PNG, mobile-data switch (turn Wi-Fi off, play an online song), lyrics-cache clear.
- Correction to the earlier gap list: the licences screen and the sleep timer's End of song already existed.

## 2026-10-29 - Batch of five small features (F-195..F-199)
- `MusicActions.downloadAll/shareArtist/setHaptics`; VM implementations (`downloadAll` counts what `DownloadCenter.enqueue` really started). Buttons: `OnlineListScreen`, `PlaylistDetailScreen`, `ArtistProfileScreen` (FlowRow + OptIn), `RecapScreen`. Setting `AppSettings.haptics` (SettingsStore key `haptics`), `MusicApp` provides a no-op `LocalHapticFeedback` when off (covers `clickableScale` and the nav bar). Strings in `strings_batch2.xml`.
- Verified: kotlinc parse of all touched files, XML well-formed, no duplicate string names. NOT run: build, device.

## 2026-10-29 - More menu, widget, lyric strip, Spotify import (F-200..F-205)
- F-200: `ui/components/CollectionMore.kt`; `OnlineListScreen` and `PlaylistDetailScreen` show Play + Shuffle + More.
- F-201: `openOutputPicker` in `NowPlayingScreen.kt` + manifest `<queries>` for the panel action.
- F-202: `playback/NowPlayingWidget.kt`, `res/layout/widget_now_playing.xml`, `res/xml/now_playing_widget_info.xml`, `widget_bg.xml`, manifest receiver; `PlaybackService.pushWidget` on media-item / play-state / metadata changes. Buttons are media-button intents to the Media3 service.
- F-203: `ui/components/LyricStrip.kt`, setting `lyricStrip`.
- F-204: `data/SpotifyImport.kt` (pure parser, 3 unit tests that PASS on the JVM), VM `importSpotify`, `SpotifyImportDialog`, Library hub row. The embed-page layout is not a public API: read at runtime only; the sandbox cannot reach Spotify, so the live page was NOT tested.
- F-205: `autoplayFresh` + filter in `buildAutoplayBatch`.
- Settings moved: haptic feedback now lives in Appearance (it was added to Playback by mistake in F-197); search labels added for haptics, mobile quality, lyric strip, don't repeat suggestions.
- Verified: kotlinc parse of every touched file, XML well-formedness, no duplicate strings, SpotifyImportTest run on the JVM. NOT run: Gradle build, other unit tests, device (widget buttons, output panel, Spotify live page).

- Build fix (F-204): LibraryHubScreen needed the getValue/setValue imports for the dialog state.

## 2026-10-30 - Update check and release notes (F-206)
- `data/UpdateCheck.kt` (pure `isNewer`, `parseRelease`; `fetchLatest` over HttpURLConnection to the GitHub API), VM `update` flow + `checkForUpdates/dismissUpdate/setAutoUpdateCheck`, `UpdateDialog`, About rows, `LocalUpdate`. Setting `autoUpdateCheck` (About category, default on). Version raised to 1.1 / 2.
- `UpdateCheckTest` (4 cases) PASSES on the JVM; the network call and the dialog are not run. The repository's releases page could not be opened from the sandbox, so the API response format is taken from GitHub's documented fields (`tag_name`, `body`, `html_url`, `assets[].browser_download_url`).
- Not done from the backlog: B-04 language picker (needs a real Hindi translation), B-07 pinch full-screen artwork, B-09 save albums/artists, B-13..B-20, B-22.

## 2026-10-30 - Start-up crash fix (F-206)
- Crash report (Xiaomi, Android 14): `NullPointerException` in `MusicViewModel.autoCheckForUpdates` called from `<init>`; `updatePrefs` was null.
- Root cause: Kotlin runs property initialisers and `init` blocks in file order. The update properties (and `appContext`) were declared further down the class than the `init` block that calls `autoCheckForUpdates()`, so they were still null.
- Fix: the update and Spotify job properties now sit above `init` and use the constructor parameter `application` instead of `appContext`. Rule for this class: anything that code inside `init` can reach must be declared before `init`.
- Verified: kotlinc parse only. NOT run on a device.

## 2026-10-30 - Updates page, in-app download, lyric strip, marquee fade, More-menu glass (F-207..F-210)
- Updates: new settings category `Updates` (hub group App, "New" badge when a release is waiting). `ui/screens/UpdatesPage.kt` is the whole update UI; `UpdateDialog` and the two About rows were removed. `data/UpdateInstaller.kt`: https-only download to `cacheDir/updates/`, `.part` file renamed when complete, size/space checks, cancellation removes the partial file, `verify` (same package, higher versionCode, same signing certificate where Android exposes it), `install` (FileProvider URI + ACTION_VIEW; opens the "install unknown apps" setting first when not allowed). VM: `startUpdateDownload / cancelUpdateDownload / installUpdate`; `UpdateUi` now carries `step`, `progress`, `errorRes`. A finished download of the same release is offered again after a restart. Version NOT changed.
- `UpdateCheck.plainNotes` turns the GitHub markdown body into plain lines (headings, bullets); 1 new test, `UpdateCheckTest` 5/5 PASS on the JVM.
- Lyric strip: `karaokeText` + `rememberFramePosition` (per frame), tap -> lyrics stage. Marquee: `fadingEdges` (offscreen layer + DstIn gradient, 14 dp) when `hasVisualOverflow`. More menu: `contentBackdrop` records the artwork and controls; the menu gets `rememberCombinedBackdrop(playerBackdrop, contentBackdrop)`; the buttons inside the recorded content keep the old backdrop so no glass samples a layer that contains itself (F-164 crash rule).
- NOT run: Gradle build, device. Unverified: that `getPackageArchiveInfo` returns signing certificates on every Android version (if it returns none the check is skipped and the system installer still refuses a different key), that GitHub asset redirects download correctly, and how the glass menu looks.

## 2026-10-30 - Widget look (F-211)
- `playback/WidgetStyle.kt` draws the card bitmap (cover shrunk to 10x10 and stretched = soft blur, dark scrim, top ambience glow in the cover's colour, white sheen, rim) and the rounded cover; `NowPlayingWidget.draw` sets both. Layout is now a FrameLayout with the bitmap behind; buttons use `widget_btn_glass` / `widget_btn_primary` drawables.
- Limit: a widget cannot use live backdrop blur, so the glass is drawn, not real. Background bitmap is 720x200 stretched (fitXY), so very wide or tall resizes distort the corners slightly. Verified: kotlinc parse, XML well-formed. NOT run on a device.

## 2026-10-30 - Back gesture over lyrics, held queue row (F-212)
- Cause: `ArtworkFlight.flying` only looked at the sheet value, so closing the player (button or predictive back) always drew the flying cover, even when Now Playing showed lyrics or the queue. New `ArtworkFlight.coverVisible`, set by NowPlayingScreen (`stage == Artwork`, reset on dispose); `flying` is false when it is off, so the whole player just slides down with its lyrics.
- Queue row: `QUEUE_ROW_LIFT` 1.05; while held and glass is on (`LocalGlassStyle.enabled` and a backdrop exists) the row gets `padding + glassSurface(ContinuousRoundedRectangle(radiusXLarge), floating)`. Glass off or Reduce Motion: enlarge only.
- Limit: the glass samples the player background layer (not the other queue rows), same reason as F-210: a layer that contains the glass must not be sampled. Verified: kotlinc parse. NOT run on a device.

## 2026-10-30 - Smooth queue reorder (F-213)
- Before: the other rows' offset jumped by one row height as soon as the finger crossed half a row, and on release the offsets reset to 0 at once. Now each non-held row animates to its offset (`Animatable`, low-bouncy spring); on release the held row animates (no-bounce spring) from the finger offset to the exact slot offset, then `moveInQueue` runs. `pendingIds` keeps the offsets until the new queue order arrives, and they are cleared in the same composition (`reorderApplied`), so there is no one-frame flash. 800 ms safety reset if the queue never changes. New lifts and drags are ignored while settling.
- Verified: kotlinc parse. NOT run on a device; the feel (spring numbers) needs a hand test.

## 2026-10-30 - Held queue row: glass over the passing songs (F-214)
- F-212's glass sampled only the player background, so the other rows were not visible through it. Now the `LazyColumn` is recorded with `layerBackdrop(listBackdrop)`; the held row is drawn by an overlay `QueueRow` placed above the list (outside the recorded layer, positioned from `listState.layoutInfo` + `dragOffsetPx`) with `LocalGlassBackdrop = combined(base, listBackdrop)`. The copy inside the list keeps the gesture but is drawn with alpha 0 (`hidden`), and never draws glass, so no glass samples a layer that contains it (F-164 rule). Glass off / Reduce Motion: no overlay, the row only enlarges.
- Row shift spring is now damping 0.9, stiffness 380 (no overshoot).
- Unverified: kotlinc parse only; not built or run. Open risks: overlay position while the list is scrolled during a drag (no auto-scroll exists), cost of recording the list each frame while dragging.
