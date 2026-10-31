# 01 — Discovery and Scope

Skill phase: Phase 1 (inspect and frame). This is the "what will and won't be in the app" decision record. Every row below was reasoned individually.

## 1. Facts, assumptions, constraints

### Confirmed (from the request)
- Build a music player.
- Use the Universal UI/UX Design Intelligence skill.
- Decide scope (what is in, what is not) before building; create `AGENTS.md` and the supporting documents before any code.

### Observed (repository)
- The repository was empty. There is no existing code, theme or asset to reuse.

### Carried over from the owner's earlier CodeAssist projects
- Builds happen on-device in CodeAssist.
- Kotlin `1.9.24` with Compose compiler extension `1.5.14`; Kotlin 2.x fails to resolve the Compose plugin there.
- Groovy `.gradle` files, no BOM, explicit versions.
- Media3 ExoPlayer has been used for earlier players.

### Assumptions (labelled; change only with the owner's say-so)
| ID | Assumption | Why it is reasonable | Cost if wrong |
|---|---|---|---|
| A-01 | Native Android, Kotlin + Jetpack Compose | Matches the owner's current stack | Rewrite of UI layer |
| A-02 | Plays audio files stored on the device only | "Music app" with no account/service mentioned | Large: streaming is a different product |
| A-03 | `minSdk 26`, `targetSdk/compileSdk 34` | Covers current phones; avoids adaptive-icon fallbacks | Small |
| A-04 | Phone-first, portrait primary, landscape must not break | Typical use | Small |
| A-05 | English UI strings in v1, all in `strings.xml` | No localisation requirement given | Small: add `values-hi` later |
| A-06 | Follows the system light/dark setting | No in-app setting requested | Small |
| A-07 | Package `com.dopavin`, label "Dopavin" | Chosen by the owner (dopamine + veena) | Done in v2 |
| A-08 | Colour palette is provisional (no brand brief exists) | See design system | Small |

### Open questions (none block v1)
- Q-01: Final app name/brand colour. v1 ships with the provisional palette.
- Q-02: Playlists next? Recommended as the first post-v1 feature (see F-26).

## 2. Users, goals, tasks

One user type: a person with music files on their phone.

| ID | Task | Completion condition |
|---|---|---|
| T-01 | Grant access and see my music | Library lists songs after one permission step |
| T-02 | Find a song | Found via scroll, tabs, or search within a few seconds |
| T-03 | Play a song, album or artist | Sound starts; mini player shows what is playing |
| T-04 | Control playback without opening the app | Notification, lock screen, headset buttons work |
| T-05 | Control playback in the app | Seek, skip, shuffle, repeat respond immediately with visible state |
| T-06 | Shape what plays next | Add to queue, play next, remove, jump |
| T-07 | Come back later and continue | Last queue and position restored, paused |
| T-08 | Recover from problems | Missing permission, bad file, empty library each have a clear next step |

## 3. Feature decisions

Decision key: **IN** = built in v1. **LATER** = intentionally deferred, design leaves room. **OUT** = not part of this product.

### 3.1 Playback core
| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-01 | Play local files with Media3 ExoPlayer | IN | The product's reason to exist; proven on CodeAssist in earlier projects |
| F-02 | Background playback via `MediaSessionService` + media notification | IN | Music must continue with the screen off; this also gives lock-screen and system media controls for free |
| F-03 | Audio focus and pause-on-headphone-unplug | IN | Expected behaviour; ExoPlayer does both with two builder flags, so skipping them would be negligent |
| F-04 | Gapless playback | IN | Default ExoPlayer behaviour for playlists; no extra UI. Not guaranteed for every format, documented as a limitation |
| F-05 | Shuffle and repeat (off / all / one) | IN | Core listening controls; built into the player |
| F-06 | Seek bar with scrubbing | IN | Core control. Position is committed on release so dragging does not stutter the audio |
| F-07 | Previous: restart if past 3 s, else previous track | IN | Standard Media3 behaviour; matches what users expect from notification buttons |
| F-08 | Resume last queue and position (paused) | IN | Task T-07. Cheap with a small preferences store; no database needed |
| F-09 | Bluetooth / headset media buttons | IN | Comes with `MediaSession`; nothing to build |
| F-10 | Crossfade | OUT | Not native to ExoPlayer; needs two players and fragile timing for little gain |
| F-11 | Equalizer / bass boost | LATER | Needs audio-session plumbing, a screen, and device-specific behaviour. Separate feature with its own testing |
| F-12 | Playback speed | OUT | Music app, not an audiobook/podcast app |
| F-13 | Sleep timer | LATER | Useful, but needs a service-side timer and a sheet. Does not block any v1 task |
| F-14 | ReplayGain / loudness normalisation | OUT | Needs tag parsing and policy decisions; low value for v1 |

### 3.2 Library
| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-20 | Read audio via MediaStore | IN | Standard, no custom scanner, respects scoped storage |
| F-21 | Songs, Albums, Artists views | IN | The three groupings every listener expects; all derived from one query, so no extra storage |
| F-22 | Sort songs: title, artist, recently added | IN | Three sorts cover real use; more adds menu clutter |
| F-23 | Search (title, artist, album), in-memory | IN | Task T-02; filtering a loaded list is simple and instant |
| F-24 | Auto-refresh when MediaStore changes, and on app start | IN | Newly copied songs should appear without a manual rescan |
| F-25 | Folder browsing | LATER | Useful for people who organise by folder; needs path handling that differs across Android versions |
| F-25b | Genres, years, composers | OUT | Tag quality is inconsistent; creates empty or noisy screens |
| F-26 | Playlists | LATER | Needs durable storage and several screens (create, rename, delete, add-to). Best added right after v1 using a small JSON file, not Room, to stay CodeAssist-friendly |
| F-27 | Favourites | LATER | Same persistence need as playlists; ship together |
| F-28 | Recently played / most played | OUT | Needs a play-history store; also a privacy surface with little benefit now |
| F-29 | Edit tags / artwork | OUT | Writing to other apps' files needs per-file user consent flows on modern Android |
| F-30 | Delete or share files | OUT | Destructive and consent-heavy; not needed for listening |
| F-31 | Hide non-music audio (ringtones, voice notes) | IN | Rule: `IS_MUSIC != 0` and duration at least 10 s. A library full of notification sounds looks broken. Threshold is a named constant |
| F-32 | Custom folder picker (SAF) | OUT | MediaStore already covers shared storage; SAF adds a second storage model |

### 3.3 Queue
| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-40 | View queue, tap to jump | IN | Task T-06; the queue must be visible or "shuffle" feels like a black box |
| F-41 | Play next / Add to queue (song menu) | IN | Two commands on an existing player; high value |
| F-42 | Remove from queue | IN | Mistakes happen; a close button per row |
| F-43 | Drag to reorder | LATER | Drag-and-drop in Compose without a library is fiddly and hard to make accessible; needs its own pass |
| F-44 | Clear queue, save queue as playlist | LATER | Depends on playlists |
| F-45 | Queue source rule | IN | Tapping a song queues the list the user was looking at (current sort/search/album), starting at that song, capped at a 2000-item window around it to stay inside binder and memory limits |

### 3.4 Now Playing
| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-50 | Artwork, title, artist/album, seek, transport, shuffle, repeat, queue button | IN | Everything needed for T-05 |
| F-51 | Lyrics | OUT | No lyrics source on device; online lookups violate the no-network rule |
| F-52 | Audio visualiser | OUT | Needs audio session data and costs battery; decorative |
| F-53 | Swipe gestures to change track / artwork carousel | OUT | Hidden gestures hurt accessibility and discoverability; buttons are enough |
| F-54 | Colours extracted from artwork | OUT | Needs the Palette library, risks unreadable contrast, adds work per track change |
| F-55 | Placeholder when artwork is missing | IN | Many files have no art; the UI must look intentional anyway |

### 3.5 System and platform
| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-60 | Light and dark themes following the system | IN | Two token sets, same roles |
| F-61 | Material You dynamic colour | OUT | Skill rule: no default style. A fixed palette gives a consistent identity and testable contrast |
| F-62 | Settings screen | OUT | Nothing to configure yet. Add when a real setting exists |
| F-63 | Home-screen widget | OUT | Separate surface, separate testing |
| F-64 | Android Auto, Wear OS, Cast | OUT (Auto: LATER) | Auto needs a browsable media tree (`MediaLibraryService`); revisit after playlists |
| F-65 | Streaming, accounts, cloud sync | AMENDED 2026-10-19: YouTube streaming IN (F-80…F-85, see the streaming addendum); accounts and cloud sync stay OUT | The owner overruled the old decision for streaming only. The original reasoning stands for everything else (see A-02) |
| F-66 | Ads, analytics, crash reporting | OUT | No network permission; nothing leaves the device |
| F-67 | Localisation | LATER | All strings are already in resources, so adding Hindi later is a translation task |
| F-68 | Dedicated tablet layout | OUT | Layouts adapt to width; no special two-pane library |
| F-69 | Edge-to-edge with correct insets | IN | Current platform default; avoids bars cutting the mini player |
| F-70 | Notification permission (Android 13+) | IN | Asked once after audio access is granted; playback works even if denied |

## 4. Edge-case thinking

Each item names the behaviour the app must have.

**Permissions**
- Denied once: explain why, offer "Try again".
- Denied with "don't ask again": the system dialog no longer appears, so show "Open settings" instead.
- Granted later from settings: when the app returns to the foreground it re-checks and loads the library without a restart.
- Notification permission denied: playback and in-app controls are unaffected; no nagging. The system still lists the foreground service in its task manager.

**Library**
- Empty library: explanatory empty state, not a blank list.
- Very large library (10,000+ files): query off the main thread, sort once, `LazyColumn` with stable keys, no per-row heavy work.
- Missing tags: show "Unknown artist" / "Unknown album"; these group together rather than creating thousands of single-song entries.
- Duplicate titles: identity is the MediaStore `_ID`, never the title.
- Files appearing or disappearing while the app is open: a content observer triggers a debounced reload.

**Playback**
- Unplayable or deleted file: skip to the next item and show a short message. If every item fails, stop and say so; never loop forever.
- Incoming call or another app takes audio focus: pause, and resume when focus returns (ExoPlayer handles this when configured).
- Headphones unplugged or Bluetooth disconnects: pause.
- Service killed by the system: next launch restores the saved queue and position, paused.
- App removed from recents: keep playing if audio is playing, stop if paused (Media3 default, matches platform expectations).
- Paused for a long time: Media3 releases the foreground state after about 10 minutes of inactivity; this is expected.
- Rapid repeated taps on play/pause/next: each is a command to the player, which is idempotent per state; the UI never assumes the result, it reads player state.
- Seeking while buffering: allowed; UI shows the target position and the player settles.
- Scrubbing: the thumb follows the finger; the player is seeked on release only.
- Queue with one item: next/previous act on that item without crashing (repeat mode decides).

**Display and accessibility**
- Long titles: truncate with ellipsis (1 line in lists, 2 lines on Now Playing). No marquee, because it is motion that cannot be paused.
- Time over one hour: `h:mm:ss`; otherwise `m:ss`. Numerals use tabular figures so the text does not jitter.
- Font scale 200%: Now Playing scrolls; list rows grow in height; nothing is clipped.
- Landscape or short windows: Now Playing switches to a side-by-side layout.
- Screen reader: each control has a name; the playing row announces its state; the seek bar announces position as text.
- Reduced motion (system animation scale 0): decorative transitions are disabled; state changes are instant.
- Right-to-left: use start/end, never left/right; seek bar stays left-to-right as it represents time.

**Lifecycle**
- Rotation: the ViewModel survives; the controller is re-attached; playback is unaffected.
- Process death while the UI is open: UI returns to Library; playback and queue persist in the service.
- UI launched while service is already playing: Now Playing shows current state immediately.

## 5. Success criteria (checkable)

| ID | Criterion | How to check |
|---|---|---|
| SC-01 | First launch to first sound needs only the permission step and one tap on a song | Manual run |
| SC-02 | Playback continues with the screen off and the app swiped away while playing | Manual run |
| SC-03 | Notification and headset buttons play, pause, skip | Manual run |
| SC-04 | Every screen state in the blueprint is reachable | Manual run / code inspection |
| SC-05 | Library of 5,000+ songs scrolls without visible stalls | Manual run |
| SC-06 | Font scale 200% shows no clipped controls | Manual run |
| SC-07 | TalkBack can operate library, mini player and Now Playing | Manual run |
| SC-08 | The merged manifest has no INTERNET permission — superseded 2026-10-19: INTERNET exists for LRCLIB lyrics (approved 2026-10-18) and YouTube streaming (2026-10-19); no other network use is allowed | Inspect manifest |
| SC-09 | Build succeeds in CodeAssist | Owner confirms |

Only SC-08 can be checked in the sandbox that produced this project. The rest need a device or CodeAssist and are recorded as Not run in the audit file.

## 6. Risks

| ID | Risk | Likelihood | Mitigation |
|---|---|---|---|
| R-01 | Code was written without a compile step (build repositories unreachable from the authoring sandbox), so compile errors are possible | Medium | Conservative APIs; static checks; owner reports errors and they are fixed first |
| R-02 | Dependency versions may not match what CodeAssist can resolve | Medium | All versions in one place (`app/build.gradle`), easy to change |
| R-03 | Very large queues may hit IPC size limits | Low | 2000-item window (F-45) |
| R-04 | Artwork loading costs on big libraries | Medium | Small thumbnails, in-memory LRU cache, load only visible rows |
| R-05 | Some OEMs kill background services aggressively | Medium | Foreground media service is the supported path; documented, not fixable in code |
| R-06 | Gapless behaviour differs by file format | Low | Documented limitation |


## 7. v2 addendum — UI/UX redesign (scope impact)

Trigger: the owner judged v1 "bahut basic" (plain list + tabs, no brand, no hierarchy). v2 changes the **presentation and navigation only**; the feature table above is unchanged, with these additions that need no new storage or permissions:

| ID | Addition | Status |
|---|---|---|
| F-71 | Bottom navigation: Home / Search / Library | IN (v2) |
| F-72 | Home: greeting, hero card (continue listening or shuffle all), quick tiles, Recently added albums, Your artists, Latest songs | IN (v2) |
| F-73 | Search as its own destination: grouped results (artists, albums, songs), browse tiles when empty | IN (v2) |
| F-74 | Alphabet fast-scroller + section letters on Songs (title sort) | IN (v2) |
| F-75 | Artwork-derived tint (own colour picker, no Palette library) for Now Playing, collection headers, mini player, hero | IN (v2) — replaces former OUT item F-54 |
| F-76 | Swipe gestures: down closes Now Playing; sideways on artwork / mini player skips | IN (v2) |
| F-77 | Animated equalizer marker for the current song | IN (v2) |
| F-78 | Bundled font and brand gradient, adaptive + themed launcher icon | IN (v2) |

Still LATER / OUT: playlists, favourites, equaliser DSP, sleep timer, lyrics, reorder-by-drag, "recently played" history (needs storage).

## Addendum — Phase 1 (Player completeness)
Added after user review: queue as a bottom sheet; Now Playing extras (volume slider, Sound sheet, Sleep timer sheet, swipe-down to collapse); Equalizer screen (system EQ bands, presets, bass boost, loudness); Settings screen (theme System/Dark/Light, skip silence, minimum track length). Still offline-only, no INTERNET permission.
Out of scope (decided): YouTube/Innertube stream extraction (violates YouTube ToS; personal use does not change that). Phase 2 may add a hybrid "Explore" tab with licensed sources (Jamendo, Audius, Internet Archive, Radio Browser, or a self-hosted Navidrome/Jellyfin) — requires a scope update here and the INTERNET permission first. **[Amended 2026-10-19]** Streaming extraction was later approved by the owner — see the YouTube streaming addendum below.

## Addendum — LRCLIB lyrics (owner-approved 2026-10-18)
Optional synced/plain lyrics from the public LRCLIB API (lrclib.net, no key). Adds the `INTERNET` permission only (no connectivity checks; an IOException simply means offline). Sends title, artist, album, duration only. Results cached in app files so lyrics work offline after the first fetch; "not found" cached 7 days. Toggle in Settings ("Fetch lyrics online"). Lyrics sheet on Now Playing: highlighted current line, auto-scroll, tap a line to seek. Everything else stays offline.

## Addendum — YouTube streaming (owner-approved 2026-10-19)

The owner decided Dopavin is a personal app and accepted the YouTube ToS risk of wrapped playback; the old "streaming OUT" decision is overturned for streaming only. The library stays the primary source; YouTube is a companion search, not a replacement.

| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-80 | Search lists YouTube Music songs as a "YouTube" section below the local results | IN | Tapping an online row plays it at once; it does not touch the queue until played |
| F-81 | Streaming playback through NewPipe Extractor's wrapped InnerTube client | IN | Owner decision above. No playback API, no sign-in, nothing is uploaded |
| F-82 | Online playback always available with a Settings toggle ("Show YouTube results", default on) | IN | One switch mirrors the lyrics toggle; off = preview offline-first behaviour |
| F-83 | Audio quality: best available audio-only stream, chosen automatically | IN | No quality picker in this scope |
| F-84 | No poToken / WebView bot-guard work | IN (constraint) | The extractor's `android_vr` client works without it; WebView is fragile to maintain on-device |
| F-85 | No downloads, no offline caching of streams or their thumbnails to disk (thumbnails stay in the memory LRU only) | OUT | Keeps the app one data source; the owner did not ask for downloading |

Nuances recorded while implementing:
- Online rows reuse the local `Song` model with `isOnline`/`videoId`/`thumbUri`; the id is a stable hash of the video id in a shifted 64-bit range so queue restore never collides with MediaStore ids.
- Stream urls are resolved fresh every time playback opens an item (they expire within hours); only search metadata (title, artist, duration, thumb url) is kept in app-private storage so a restored queue can still list online rows (they still need network at first play).
- Lyric lookup for an online song uses its title/artist as given by YouTube Music, exactly like a local file with missing tags.
- What leaves the device stays the same as for lyrics plus the typed query words. No account, no cookies beyond InnerTube's public API key, no download anything.

## Addendum — Online-first Dopavin (owner-approved 2026-10-20)

The owner asked for a "proper online music player approach": Home suggestions built from online sources with artist-wise categories and activity-based recommendations, lyrics that behave like the Better Lyrics / YumaPlayer provider chain, and offline mode still fully working. Decisions needed no further question: without an account, "activity" can only mean local observation, and the offline experience stays present.

| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-86 | Play history store (title, artist, videoId/id, time; capped JSON, app-private) | IN | Records what the user actually plays; the only honest personalisation signal available without sign-in |
| F-87 | Home gains an online zone: "Because you played" (related-items of the last online song), top-3 most-played artists as YouTube shelves, six fixed category tiles (charts / Punjabi / Bollywood / romantic / lo-fi / English hits) | IN | Covers "artist wise categories" and "activity based suggestions". Cold start (no history) shows categories only |
| F-88 | Category and artist shelves open a dedicated online list screen (`Route.OnlineList`) playing straight through the list; the Screen hides entirely when the toggle is off or the network is down | IN | The local Home shelves below it are untouched; offline Home = exactly the old Home |
| F-89 | Lyrics become a provider chain like the Better Lyrics extension: LRCLIB first, then the public Better Lyrics API (TTML, syllable→line fallback), videoId attached when known | IN | Better hit-rate on YouTube-catalog songs; TTML parsing needs no dependency. Word-by-word karaoke display is NOT in this step (the sheet is line-synced); Unison provider deliberately later (its read API is still locked to the extension's auth) |
| F-90 | No sign-in, no account features, no downloads of streams, no syllable-level display rendering (yet) | OUT | Confirms the standing limits around F-85; the sheet's fallback to plain lines covers the rest |

Recording: history never leaves the device; the refresh and "related" walks send only the artist name / videoId to the public YouTube endpoints, same data class as the existing search.

## Addendum — Dopavin Music Core (owner spec `docs/Dopavin_Music_Core_Architecture.md`, owner-approved 2026-10-20)

The owner supplied a full staged architecture (moods, recommendations, queue generation, feedback, provenance, provider-adapter rules). Working decisions from reconciling it with this repository (Stage-1 audit done — findings in `06`/`07`):

| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-91 | Taste store + feedback capture: plays, completions (≥90 %), user skips, auto-failures (never a negative signal — `PlayerEvent.SongFailed` marks them), likes (reused `SettingsStore.likedIds`), per-song "Not interested" (blocked from suggestions only, still playable), plus a "clear personalization" control | IN | §7.5 of the owner spec. Room is REJECTED per the project's no-database rule (§11 asked for an evaluation — outcome: JSON stores in the existing house pattern) |
| F-92 | Queue-item provenance (MANUAL / SMART_AUTOPLAY / MOOD_QUEUE / SONG_RADIO per mediaId, session-only in v1 with a documented reason), atomic batch append through a new `PlayerConnection.appendBatch`, and Smart Autoplay: appends one 8-song batch when repeat is OFF and ≤2 playable items remain, capped at the existing queue cap; disabled by the settings toggle; offline falls back to local candidates | IN | §8 of the owner spec. Append-only keeps manual items and album order untouched (§8.4); provenance persistence deferred because the timeline already restores queue contents itself through the existing `PlaybackStore` path — rationale in `06` |
| F-93 | Six mood presets (Chill, Happy, Energetic, Focus, Reflective, Romantic) and a deterministic, unit-testable scorer with the spec's weights and available-weights normalisation; missing features stay `null` and drop out of the denominator; every pick carries a truthful reason code | IN | §6/§7. No audio analysis exists in the project, so `energy/valence/tempo` are absent everywhere; online candidates seeded by a mood query carry `moodMatch = 1.0` as an honest *retrieval* bias (labeled as such in reasons) |
| F-94 | Mood Queue screen (S-11): generated batch with "Play all" (explicit fresh queue, the user pressed play) and "Append" (bottom, protected); Song Radio from a seed song via the Search / online list / suggestion row menus | IN | §8.2. Replacing the queue only ever happens by an explicit user action |
| F-95 | Personal Mix, full catalog model fields (composition/recording relationship graph), derived audio-feature analyser, and history upload: LATER | LATER | No honest data source for features without a decoder pass; growth of scope is staggered per §16 |
| F-96 | Owner-spec Stage 6 ("InnerTube module") is already satisfied: the project's provider adapter is the NewPipe Extractor integration shipped in T07/T07b (`OnlineRepository` + `PlaybackService` resolver + `dopavin-yt` markers), audited against extractor v0.26.5 sources. Unit tests for scorer/planner added as pure Kotlin JUnit with `testImplementation "junit:junit:4.13.2"` — dependency recorded here as owner-approved via the spec's §15/§17; it is not used by `assembleDebug` | DONE | The only "InnerTube" code in this repo is the already-audited extractor layer |

Language rule (spec §3.9): already honoured and re-checked — no language category, filter or ranking signal exists; the mood/category **seed queries are backend search phrasing** (title words sent to YouTube), never a visible language concept.

## Addendum — Home goes online-only (owner steering "offline ka scene hata do", 2026-10-20)

| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-97 | Home drops the local-library zone entirely (no "Your music" divider, quick tiles, recent albums, local artist row, latest songs). Home is now: greeting + hero, moods, categories, artist shelves (top five played artists, each opening its full song list), "Because you played X", and a new **History** shelf (last plays, newest first, plays local and YouTube entries alike). Offline Home = moods + history | IN | Owner's explicit call; the local library keeps full parity through the Search tab and the Library tab (nothing local is lost, it is just not Home's scene any more). History answers "store karna" — the F-86 log now surfaces on Home |

## Addendum — Music Player UX & Feature Upgrade (owner spec pasted 2026-10-20, features F-98…F-107)

The owner pasted an 8-part UX upgrade spec. Reconciliation with this repository first: EQ, sleep
timer, speed, skip-silence, Liked tab, history shelf, dynamic artwork accent, Recently-added sort
and quick resume already exist (they are reused, not rebuilt). The real gaps were universal song
actions, multi-select, playlists, queue management, a settings reorganisation, a song-details
screen, an honest storage dashboard and a duplicates finder. Every row below honours the standing
rule: **no control without a real behaviour behind it**.

| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-98 | Universal song actions on every row/card: long-press opens the same menu everywhere (Play now / Play next / Add to queue / Favourite / Song radio / Details / Share / Hide-from-library, plus queue-only Move up–down–Remove) | IN | One mental model across Library, Search, Collection, Online lists, Suggestions, Queue and Home cards. Actions only appear when the row can actually perform them (online rows: no share/hide — no file) |
| F-99 | Song details screen: honest facts only (title, artist, album, duration, file size, date added, path) + actions (play, radio, like) | IN | `SIZE` was already projected in a past query branch; now surfaced. No fake bitrate/codec claims — a missing fact shows "Unknown" |
| F-100 | Multi-select in Library (Songs + Liked tabs): long-press to start, tap to extend, bottom bar with count / Select all / Add-to-queue / Like / Add-to-playlist / cancel | IN | Destructive batch delete deliberately NOT here — deletion lives in F-105's audited flow |
| F-101 | Now Playing "More" sheet: speed, skip-silence, current output device + system sound settings, song radio, song details, offline status | IN | Everything in it is real and implemented; gapless stays a note (Media3 default), crossfade/normalisation are not available and are not offered |
| F-102 | Queue upgrade: drag-to-reorder (long-press + vertical drag), swipe-to-remove with Undo snackbar, provenance chips (You/Auto), save-queue-as-playlist, clear-upcoming with confirmation | IN | Undo is single-step in-memory (documented); chips come from the session `_origins` map (D-27 stands) |
| F-103 | Named playlists: create/rename?/delete, add/remove songs, play/append/shuffle; JSON store in the house pattern (no Room/Hilt/KSP) | IN | Renaming is not wired in v1 (listed in `07` limitations); persistence via `playlists.json` + pure codec |
| F-104 | Storage dashboard: library size (sum of MediaStore SIZE), app-data footprint (prefs + playlists + lyrics cache), free space via `StatFs` | IN | Artwork cache is memory-only → no "clear cache" row (nothing to clear — honesty rule). Online downloads do not exist in Dopavin and the screen says so |
| F-105 | Duplicates finder: normalised title+artist, duration within ±2 s; delete only through `MediaStore.createDeleteRequest` (API 30+); below R → honest "unavailable on this Android" message | IN | No direct `ContentResolver.delete` is ever attempted; consent dialog is system-owned |
| F-106 | Settings redesign: search field, honest categories (Appearance / Playback / Audio & effects / Library / Recommendations / Downloads & storage / Privacy & data / About), Advanced fold for the short-audio filter, Restore Defaults with confirmation; startup theme pre-read so the first frame never flashes the wrong canvas | IN | Rows that cannot be implemented (balance, loudness-in-settings, crossfade) simply do not appear |
| F-107 | Now Playing More-sheet strings + PlaybackUi.speed plumbing (snapshot reads `playbackParameters.speed`) | IN | Speed chip state is real player state, not a parallel setting |

Honesty ledger for the pasted spec: gapless = Media3 default (note only) · crossfade = not
available · volume normalisation = not available · online downloads = not possible (provider
rules) → replaced by the F-104 dashboard · no language-based suggestions anywhere (F-91 language
rule unchanged and re-checked).

## Addendum — Downloads-first Dopavin (owner request 2026-10-19, features F-108…F-116)

The owner asked, in one message: fix the crash from the attached screenshot; move every "more
options" dropdown into a bottom sheet; make Home, Now Playing and Settings correct; **add music
downloads**, put them somewhere easy to reach, **remove local-music support**; downloaded songs stay
inside the app but the user may save a copy; add the fine-grained settings a serious listener
expects, drop the unwanted ones, and re-imagine the Settings UI. This overturns F-85/F-90 ("no
downloads") and F-20…F-32 (MediaStore library) by the owner's explicit decision.

| ID | Feature | Decision | Reasoning |
|---|---|---|---|
| F-108 | Crash fix: `EmptyState` must never attach `verticalScroll` to an unbounded height (nested in Playlists / Duplicates / Playlist-detail scroll columns → `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`). Rule: a scrollable is only attached when the incoming height is bounded | IN | Root cause confirmed by reading every `EmptyState` call site; the component is now safe wherever it is placed |
| F-109 | Every "more options" surface is a bottom sheet — song actions on rows/cards/queue, the sort chooser, add-to-playlist. One `ActionSheet` host at the app root (`LocalSheetHost`); no `DropdownMenu` anywhere | IN | Owner request. One host also removes per-row menu composition and the "menu clipped inside a LazyColumn" class of bugs |
| F-110 | Local music (MediaStore scan, READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE permission, access screen, hide-from-library, duplicates finder, "hide short audio", refresh library) is removed. The "library" is now the set of **downloaded songs** | IN | Owner request. The library machinery (artists, sort, liked, playlists, search) is kept and fed from downloads instead of MediaStore |
| F-111 | Download a song from any surface (row sheet, Now Playing, Home/online cards): app-private file `filesDir/downloads/<videoId>.<ext>` + cover image + `index.json` (title, artist, duration, size, format). Queue with 2 parallel jobs, per-item progress, cancel/retry, foreground service with a progress notification, Wi-Fi-only option | IN | Owner request. Downloaded files never leave the app unless the owner exports them (F-113). Provider-policy risk is the owner's already-accepted risk (F-81, docs/09) and is repeated in docs/06 |
| F-112 | Downloads is a first-class place: the bottom-navigation tab "Library" becomes **Downloads** (Songs / Artists / Liked tabs, queue of active downloads pinned on top, storage summary); Home gets a "Downloaded" shelf; Settings > Downloads & storage manages quality, Wi-Fi-only, auto-download-liked and delete-all | IN | "download music aisi jagah rakho jo accessible ho" |
| F-113 | Save a copy: "Save to device" on a downloaded song opens the system file saver (`ACTION_CREATE_DOCUMENT`, no storage permission on any Android version); the app copy is kept | IN | "user chahe to use save kar sakta hai". The user picks the folder; nothing is saved anywhere without an action |
| F-114 | Downloaded songs play from the local file (resolver checks the download store first; offline works); online rows of an already-downloaded song also resolve to the file | IN | Offline playback is the point of downloading |
| F-115 | Settings re-imagined: card groups with icon chips, quick tiles, segmented controls. New real options: stream quality, download quality, Wi-Fi-only downloads, auto-download liked songs, pure-black theme, keep pitch when changing speed, sleep-timer fade-out. Removed: min-duration filter, refresh library, duplicates, gapless/resume/speed notes, "Advanced" fold | IN | Every kept/added option has behaviour behind it; unavailable ideas (crossfade, ReplayGain, balance) are still not offered |
| F-116 | Now Playing and Home re-laid out: Now Playing gets a compact single-row action strip (no wrapping pills), the download control and a "⋮" song-sheet; Home becomes greeting → continue card → downloaded shelf → moods → categories → artist shelves → history | IN | Owner asked for correct layouts; the old pill row wrapped to 2–3 lines and squeezed the artwork |

Standing limits unchanged: no account, no sign-in, no analytics, no cloud sync. Crossfade,
ReplayGain and per-channel balance remain unavailable (no safe Media3 path) and are not offered.

## Addendum — Home redesign, landscape, fast start (owner request 2026-10-21, F-117…F-119)

- **F-117 Home redesign (IN):** quick-picks grid, trending shelf, downloaded shelf, category rail, skeleton while loading, empty-library hero that sends the user to Search.
- **F-118 Landscape (IN):** the activity rotates freely (`fullUser`) and handles configuration changes itself (no recreation, playback UI keeps its state); Home puts hero and quick picks side by side from 600 dp width; Now Playing already had a two-pane layout.
- **F-119 Fast start (IN):** Home shelves are cached on disk and painted first (stale-while-revalidate, 30 min), shelves are fetched in parallel, stream URLs of the first two Home songs are resolved ahead of the tap and kept 20 min in memory.
- **Bug fixed:** taps on Home shelf cards (and long-press-enabled rows) never fired because a separate `detectTapGestures(onLongPress)` consumed the tap before `clickable` saw it. `clickableScale` now takes `onLongClick` (combinedClickable).
- **F-120 Liquid glass (IN):** glass on navigation (floating pill), mini player, sheets, buttons, chips, switches, search field and Now Playing controls; every parameter adjustable in Settings > Glass & style; vendored Kyant0 Backdrop + Capsule with licences. Solid fallback below Android 12.
- **F-121 Extras (IN):** download storage limit (oldest removed first), downloaded badge, backup export/import (likes, playlists, settings), resume on headphone/Bluetooth reconnect. Car-mode large UI still open.
- **F-122 UI polish (IN):** liquid tab bar, landscape navigation rail, pull-to-refresh on Home, tap haptics, Driving mode screen.


## Addendum - Subscriptions, feed cards, word-synced lyrics (owner request 2026-10-23)

| ID | Item | Status | Notes |
|---|---|---|---|
| F-129 | Subscriptions: subscribe to a YouTube channel (artist) from Now Playing > More; stored only on the device (SharedPreferences, no account); "Subscriptions" screen = channel list + merged latest-uploads feed via NewPipe Extractor `ChannelInfo` / `ChannelTabInfo` (videos tab) | IN | Same data class as search: only the channel url goes to YouTube. No import/export of NewPipe subscriptions yet |
| F-130 | Home feed: large wide cards (Subscriptions, Trending) and the existing small square cards (related, artists, downloaded, history) | IN | Pure UI; same data as before plus the subscription feed |
| F-131 | Lyrics: word-by-word (karaoke) highlight from Better Lyrics TTML syllable timing, kept in the cache; provider order Better Lyrics (5 s timeout) then LRCLIB; instrumental-gap dots; clearer showcase in the player panel | IN | Supersedes the "not in this step" note of F-89. Other YumaPlayer providers (KuGou, SimpMusic, Unison, YouLyPlus) are NOT added: their APIs are not verified. YumaPlayer is GPLv3 like NewPipeExtractor; only the idea of its provider list is used, no code copied |
| F-133 | Artist profile (YouTube channel): full-width square photo, Subscribe/Subscribed button, Shuffle, all songs found (first page of the channel's videos/tracks tab, max 60) and albums row (albums/playlists tab; tap opens its songs). Local artist page header also becomes a full-width square | IN | NewPipe `ChannelInfo`/`ChannelTabInfo`/`PlaylistInfo`; first page only, no "load more" |
| F-134 | Library tab replaces the Downloads tab: short hub list (Playlists, Subscriptions, Favorite Songs, Recently Played, Downloads). Downloads/Favorites open the old library screen as a pushed screen with a back button | IN | Only 5 of the Apple-Music-style options on purpose; no Lossless/Spotify/Server/Recap |
| F-137 | Settings redesign: big heading + glass back button, search, three groups (YOUR LISTENING / YOUR EXPERIENCE / PRIVACY & APP), open rows without cards; About Dopavin = real launcher artwork, name, tagline, dynamic version, glass "View source code" button, licences dialog, Privacy & data, Restore settings with confirm, "Made for music." | IN | The repository address is NOT in the project: `about_source_url` (values/strings_settings.xml) is the configuration point; the button stays hidden while it is empty |
| F-138 | Artist profile in liquid glass: back disc, Subscribe (accent glass) and Shuffle glass pills over the photo | IN | Uses the shared `glassSurface`, so the Glass & style settings drive it |
| F-143 | Lyrics providers: BetterLyrics Portato, LyricsPlus, Unison, KuGou added next to Better Lyrics + LRCLIB (lrc.red/BiniLyrics not added: no verifiable API) | done, unbuilt |
| F-144 | Search redesign: filter chips, albums, "Did you mean", recent searches; folded tab bar keeps last tab icon while Search is active | done, unbuilt |
| F-145 | Lyrics race: synced lyrics always win; plain text only when no provider has synced | done, unbuilt |
| F-146 | Search landing and results redesigned (top result card, count tiles, recent pills, skeleton loading) | done, unbuilt |
| F-147 | Home redesign: compact header + search pill, quick-access shortcuts, Continue listening, glass mood chips (existing feed reused) | done, unbuilt |
| F-148 | "More" menus (songs, sort, Now Playing More) are anchored glass/solid dropdowns instead of bottom sheets; Queue kept as sheet | done, unbuilt |
| F-149 | Home redesigned from scratch (real logo, artwork hero, round shortcuts, poster cards, ranked trending, genre grid) | done, unbuilt |
| F-150 | Artist profile photo fades (alpha mask) into the body, no painted edge | done, unbuilt |
| F-151 | Clean YouTube titles; interviews/vlogs/trailers/compilations filtered from artist pages and subscription feed | done, unbuilt |
| F-152 | Search shows artist channels; artist pages use the genuine channel; official uploaders ranked above small re-upload channels | done, unbuilt |
| F-153 | Karaoke feel for line-synced lyrics (estimated word timing), smoother per-character word sweep, lyrics in Driving mode | done, unbuilt |
| F-154 | Karaoke sweep no longer splits Hindi/Indic matras and conjuncts, Arabic letters, emoji (written-unit pieces) | done, unbuilt |
| F-155 | YouTube radio (mix via NewPipe) on Home; opens as full song list with Play, Shuffle, Save as playlist | done, unbuilt |
| F-156 | Playlist screen as an artist-style cover page (YouTube cover or collage); downloads save HD artwork | done, unbuilt |
| F-157 | Smart lyrics fallback: query plan, title cleaning, provider fallback, strict matching, miss cache, single flight | done, unbuilt |
| F-158 | Song details as a dialog; marquee for long titles (Now Playing, mini player, details) | done, unbuilt |
| F-159 | Queue rows show artwork for online songs; notices use an iOS-style toast (glass / solid by theme) that auto-dismisses | done, unbuilt |
| F-160 | Search: same recording shown twice is merged, remix/live/karaoke ranked after originals; artist lookup no longer falls back to an unrelated channel | done, unbuilt |
| F-161 | Radio/list pages use the cover-page layout (cover melts into page, actions on it); Downloads header scrolls away; back button is fixed liquid glass on both | done, unbuilt |
| F-162 | Settings/About titles scroll with the page; every pushed page's back button and the Now Playing close button are real liquid glass (sample what is behind them) | done, unbuilt |
| F-163 | Crash handler: any uncaught exception opens a crash report screen (copy, share, restart) | done, unbuilt |
| F-164 | Fix: renderer stack-overflow crash (glass back layer was inside the layer it samples) | done, unbuilt |
| F-165 | Debug builds only: crash history stored; Settings > About > Crash reports (read, copy, share, delete, clear) | done, unbuilt |
| F-166 | Every pushed page: title below the back button and scrolls with the page (`ScreenHeader`) | done, unbuilt |
| F-167 | Predictive back: page shrinks and slides while the back gesture is dragged; cancel restores it | done, unbuilt |
| F-168 | Appearance: Tint (8 presets + default) and Dynamic tint that follows the playing song; drives all accent colours | done, unbuilt |
| F-169 | About: GitHub profile and Dopavin repository links | done, unbuilt (repo page not verifiable from sandbox) |
| F-170 | iOS-style predictive back: previous page underneath, parallax and dim by drag progress | done, unbuilt |
| F-171 | docs/10_UI_RULES.md: the owner's UI rules saved in the project, linked from AGENTS.md | done |
| F-172 | Failed online screens (search, radio/list, artist, mood, song radio) show Try again | done, unbuilt |
| F-173 | Notification permission asked when music first plays, not at launch | done, unbuilt |
| F-174 | Reduce motion honoured by title marquee, toast, page swap and swipe-back animation | done, unbuilt |
| F-175 | Dialog buttons and Home mood chips grow with font size (min 48 dp) | done, unbuilt |
| F-176 | Swipe-back: back button travels with the page, previous page's button arrives with it, moving page keeps its ambience | done, unbuilt |
| F-177 | Now Playing predictive back (sheet follows finger, mini player fades in and lands with a ~4.5% spring squash); mini player stretches ~4.5% toward the finger while swiping to change song | done, unbuilt |
| F-178 | Player held at 12 %, bars hidden until it is gone, mini drop spring, back button slides (no cross-fade flash), motion rules doc | done, unbuilt |
| F-179 | Motion tokens, maths and specs (blueprint phase 1) with JVM tests; blueprint saved to docs | done, unbuilt |
| F-180 | Blueprint phase 2: Now Playing sheet driven by one value, drag-up on mini player; layered page host to fix the back flash | done, unbuilt |
| F-181 | Search: "Show more results" paging (YouTube next page); docs/11 finding 10 withdrawn | done, unbuilt |
| F-182 | Shared-element cover flight between mini player and Now Playing | done, unbuilt |
| F-183 | Cover flight: record the player cover rectangle when the sheet settles (fix for no flight) | done, unbuilt |
| F-184 | Cover flight rewritten: linear with the sheet, slower than the player, reverse on open, resting rectangle from the host box | done, unbuilt |
| F-185 | Cover flight: back to recording the cover rectangle at sheet==0 (F-184 recording broke it) | done, unbuilt |
| F-186 | First-open cover flight: measured rectangle (host-relative + settled), validation, Reduce Motion switch, logging | done, unbuilt |
| F-187 | Swipe-down in Now Playing drives the shared sheet value (flight follows the finger); own dragY translation removed | done, unbuilt |
| F-188 | Bottom bar / mini player no longer recreated when Now Playing closes; cover landing spring in the mini player | done, unbuilt |
| F-189 | Settings categories are real routes (`Route.SettingsPage`): app back and device/predictive back both return to the settings hub; fixes the F-178 `intercept` regression | done, unbuilt |
| F-190 | Share song: system share sheet with title, artist and a YouTube Music link (song menu and Now Playing More) | done, unbuilt |
| F-191 | Recap: listening statistics (minutes, plays, songs, artists, daily chart, top songs and artists) for 7 days / 30 days / all time; a listen counts after 30 s, stored on the device only; entry in the Library hub; cleared by Clear Recap and by resetting personalisation | done, unbuilt |
| F-192 | Streaming quality can differ on mobile data (metered connection); the Wi-Fi choice is used otherwise | done, unbuilt |
| F-193 | Storage screen: lyrics cache size and Clear lyrics cache | done, unbuilt |
| F-194 | Lyrics card: pick up to 6 lyric lines and a style (Dark / Light / Colour), render a PNG and share it (FileProvider, no storage permission) | done, unbuilt |
| F-195 | Download all: on radio/online lists and playlists, queues every online song that is not saved yet | done, unbuilt |
| F-196 | Add all to queue on radio/online lists and playlists (uses the existing multi-select action) | done, unbuilt |
| F-197 | Settings > Playback: Haptic feedback switch (default on); off replaces the system haptics with a no-op for the whole app | done, unbuilt |
| F-198 | Artist profile: Share (name + channel link); buttons wrap on narrow screens | done, unbuilt |
| F-199 | Recap: Play top songs | done, unbuilt |
| F-200 | Radio / playlist pages: Play and Shuffle stay visible; a round More button opens the rest (save as playlist, download all, add all to queue) | done, unbuilt |
| F-201 | Now Playing More: "Play on..." opens the system output switcher (falls back to sound settings) | done, unbuilt |
| F-202 | Home-screen widget: artwork, title, artist, previous / play-pause / next | done, unbuilt |
| F-203 | Show current lyric under the artist in the full player (setting, default off) | done, unbuilt |
| F-204 | Import a public Spotify playlist or album link: songs matched on YouTube Music and saved as a playlist (Library > Import from Spotify) | done, unbuilt, page format unverified |
| F-205 | Autoplay: Don't repeat suggestions (skips the last 60 listens; setting, default on) | done, unbuilt |
| F-206 | Update check against the latest GitHub release (About: Check for updates + automatic check at most every 12 h); dialog with release notes; Download opens the APK link in the browser | done, unbuilt |
| F-207 | Updates settings category: when a newer release exists the page itself is the update screen (version, size, release notes, Download with progress, Cancel, Install, Retry); no pop-up dialog; hub row shows a "New" badge; auto-check shows one quiet message. In-app APK download into the app cache, package/version/signature check, system installer via FileProvider (new permission REQUEST_INSTALL_PACKAGES). Version unchanged (1.1 / 2) | done, unbuilt |
| F-208 | Lyric strip under the artist: karaoke word highlight (same as the lyrics page) and a tap opens the lyrics | done, unbuilt |
| F-209 | Marquee text fades out at both ends while it scrolls (only when the text is wider than its space) | done, unbuilt |
| F-210 | Now Playing "More" menu glass now bends the real player behind it (artwork and controls are recorded into a second backdrop used only by the menu) | done, unbuilt |
| F-211 | Widget restyled like the app: glass card with blurred cover ambience glowing from the top, sheen and light rim, rounded cover, glass prev/next buttons and a brand-gradient play button | done, unbuilt |
| F-212 | Player: with lyrics or the queue showing, the cover no longer flies in over them during the back gesture / close; queue: a held row is enlarged (1.05) and sits on a liquid-glass rounded box (Capsule shape), only when glass is on | done, unbuilt |
| F-213 | Queue reorder is smooth: the rows the held song passes glide aside with a spring, and on release the held row glides into its slot before the queue changes (no jump) | done, unbuilt |
| F-214 | Queue reorder: the held song is drawn above the list on a real liquid-glass box that bends the other songs passing behind it; the other songs glide with a softer spring (glass off: plain enlarge as before) | done, unbuilt |


## Backlog - candidate features (owner request 2026-10-29)
Ordered by effort and value; none needs a new account unless stated. Each item needs a scope line here before code (owner approves).

| ID | Feature | Needs | Status |
|---|---|---|---|
| B-01 | Share song | - | done (F-190) |
| B-02 | Recap (listening statistics) | - | done (F-191) |
| B-03 | Lyrics card: pick lines, render an image, share | share intent + FileProvider | done (F-194) |
| B-04 | Language picker (English, Hindi first) | translations | planned |
| B-05 | Home-screen widget (artwork, title, controls) | AppWidget | done (F-202) |
| B-06 | Import a public Spotify playlist or album link (songs matched on YouTube Music, no Spotify account) | network | done (F-204), unverified against the live page |
| B-07 | Full-screen artwork (pinch out on the cover) | - | planned |
| B-08 | Lossless / format badge and details (codec, bit depth, sample rate) for downloads | - | partly exists (Song details show format and size); badge not built |
| B-09 | Save albums and artists to the Library | - | planned |
| B-10 | Streaming quality separate for Wi-Fi and mobile data | - | done (F-192) |
| B-11 | Cache control: lyrics cache size and clear (stream cache does not exist) | - | done (F-193) |
| B-12 | In-app licences screen | - | already existed (About > Open-source licences) |
| B-13 | Crossfade | risky: background reliability (see F-10) | candidate |
| B-14 | Sound check (equal loudness), spatial widening | audio processors | candidate |
| B-15 | Output picker with Cast devices | Cast dependency | candidate |
| B-16 | Music videos and video artwork | video playback | candidate |
| B-17 | Scrobbling (Last.fm / ListenBrainz) | user API key or token | candidate |
| B-18 | Discord presence | account / token | candidate |
| B-19 | Subsonic-compatible music server | server + credentials | candidate |
| B-20 | YouTube Music account sync | sign-in (stays OUT unless owner decides) | candidate |
| B-21 | In-app update check from GitHub | network | done (F-206, F-207); download and install happen inside the app |
| B-22 | Android Auto browse tree | MediaLibraryService | candidate |
