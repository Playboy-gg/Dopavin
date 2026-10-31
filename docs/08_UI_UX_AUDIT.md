# 08 — UI/UX Audit and Verification (v2)

Honest record. A check is "Pass" only if it was actually run or directly inspected. "Not run" is neither a pass nor a failure.

## 1. Scope

- **Project / build:** Dopavin v2 (UI/UX redesign + rename) on top of the v1 code that the owner's toolchain built successfully.
- **Platforms:** Android phones, API 26–34.
- **Screens:** S-01 … S-07, C-01, C-02 (`05_SCREEN_BLUEPRINT.md`).
- **Authoring sandbox:** file tools, shell, Python (tree-sitter Kotlin parser), npm; no Maven/Google access, no Android SDK, no emulator, no device.
- **Not included:** anything needing a compiler or a running app.

## 2. Evidence

| ID | Source | Observation | Limitation |
|---|---|---|---|
| E-01…E-04, E-09 | Carried from v1 (Media3 guide, FGS types, Android 13 permissions, manifest read) | Unchanged; manifest has no `INTERNET` | — |
| E-10 | **v1** built on the owner's toolchain (`compileDebugKotlin`, `assembleDebug`) after the 1.5.14 compiler fix | v1 compiled and packaged | Applies to v1 only; v2 changed all UI files |
| E-11 | tree-sitter-kotlin parse of all `.kt` files | 35 files, 0 syntax errors | Grammar only: no types, imports or API existence |
| E-12 | Script: all XML parsed | 0 malformed | Not validated against Android schemas |
| E-13 | Script: every `R.string/plurals/drawable/font` and `@type/name` reference vs defined resources | 0 missing | — |
| E-14 | Script: `Dimens.*`, `colors.*`, `type.*` tokens exist; every `MusicActions` method is overridden by the ViewModel and every `actions.x` used exists; every `com.dopavin.*` import resolves to a declaration | 0 problems | Not a compiler |
| E-15 | Contrast script (WCAG luminance) over the v2 palette | All text pairs ≥ 4.5, non-text ≥ 3.0 (`03_DESIGN_SYSTEM.md` §2) | Calculated, not measured; artwork-tinted backgrounds not covered |
| E-16 | Icons generated from the official Material SVG package by script | 23 vector drawables | Visual check still pending on device |

## 3. Findings

| ID | Severity | Where | Observed | Impact | Recommendation | Status |
|---|---|---|---|---|---|---|
| UX-01 | **P1** | Whole v2 UI | **Never compiled.** Written without a compiler; library APIs (Compose 1.6.8, Material3 1.2.1) were used from memory | A compile error blocks the first run | Build, send the error list, fix first | **Open** |
| UX-02 | P2 | Icons | Now generated from the official set (E-16) | Low | Check at real size | Open (reduced) |
| UX-03 | P2 | Tests | None written | Regressions unnoticed | JVM tests for `Song.kt` helpers and `ArtworkTint` binning | Open |
| UX-04 | P2 | Palette | Only calculated contrast | Real legibility unknown | Check in sun, with high-contrast setting | Open |
| UX-05 | P3 | Chips at 200% font | Row of 3 chips may overflow | Minor | Make the row scroll if seen | Open |
| UX-06 | P3 | Play next with shuffle on | Follows shuffle order | Minor | Documented | Open |
| UX-07 | P3 | Reduce motion | Read once at start | Minor | Re-read on resume | Open |
| UX-09 | P2 | Dev note | Home/Search/Library each keep their own scroll state via `SaveableStateProvider`; not tested on config change | State loss possible | Rotate on device | Open |
| UX-10 | P2 | Tablets | Album/artist rows stretch full width; no two-pane layout | Looks sparse on tablets | Add max width if needed | Open |
| UX-11 | P2 | Artwork tint | Text on tinted headers/mini player assumes normal text colours; unusual art (very bright or low-saturation) not tried | Possible low contrast | Test with 10+ covers; clamp tint if needed (`ArtworkTint`) | Open |
| UX-12 | P3 | Swipe gestures | Swipe-down on Now Playing is on the whole screen; may conflict with future scroll content | None today | Re-check if content is added | Open |

## 4. Implementation verification

| Check | Method | Result | Evidence |
|---|---|---|---|
| Build (v2) | — | **Not run** | No SDK/Maven in sandbox (UX-01) |
| Unit / UI tests | — | **Not run** | None written |
| Lint | — | **Not run** | — |
| Kotlin syntax | tree-sitter | **Pass** | E-11 |
| XML well-formed | parser | **Pass** | E-12 |
| Resource references | script | **Pass** | E-13 |
| Cross-file references | script | **Pass** | E-14 |
| Manifest has no `INTERNET`, FGS type present | read | **Pass** | E-09 |
| Navigation, primary actions, validation/recovery | — | **Not run** | Needs device |
| Responsive layouts (portrait, landscape, 600dp+) | — | **Not run** | Needs device |
| Accessibility (TalkBack, 200% font, reduce motion) | — | **Not run** | Needs device |
| Visual comparison with the blueprint | — | **Not run** | No renderer |
| Performance (5,000+ songs, alphabet scroller) | — | **Not run** | Needs device |

## 5. Device test script

1. Fresh install → welcome screen → allow → Home loads; deny twice → "Open settings"; enable → library appears on return.
2. Home: greeting, hero (shuffle all), quick tiles, carousels, latest songs. Tap a song → plays; mini player floats above the nav; hero becomes "Continue listening" with artwork colour.
3. Tabs: Home → Search → Library; back from Library goes to Home; back on Home exits.
4. Search: browse tiles jump into Library; type a query → artists / albums / songs; no-result state; clear; keyboard hides nav and mini player.
5. Library: chips; sort menu; section letters; drag the alphabet scroller (bubble, no jank); albums grid; artists.
6. Album page: tinted header, numbered tracks, Play/Pause reflects state, top bar solidifies on scroll. Artist page with circular art.
7. Now Playing: tint follows art, art shrinks when paused, swipe art left/right, swipe down closes, seek drag + tap, shuffle/repeat dot, queue pill; landscape layout.
8. Queue: jump, remove current, remove last (returns to Home).
9. Background: swipe app away while playing (keeps playing), while paused (stops); unplug headphones pauses; notification and lock screen controls.
10. Kill app while paused → reopen → queue and position restored (paused).
11. Font scale 200%, light and dark theme, animation scale 0, TalkBack: nothing clipped, every control announced, nav tabs say "selected".
12. Launcher icon (normal and themed) looks right.

## 6. Final report

- **Reviewed:** the full v2 code and documents by static means only.
- **Major finding:** UX-01 — v2 has never been compiled.
- **Changes implemented:** redesign per `05_SCREEN_BLUEPRINT.md`; rename to Dopavin / `com.dopavin`.
- **Passed:** syntax parse, XML, resource references, internal cross-references, manifest content, calculated contrast.
- **Not run:** build, lint, tests, and every behaviour, layout, accessibility and performance check.
- **Next:** build in the owner's toolchain and send the errors; then the device script above.

## 7. Handoff checklist

- [x] Scope addendum, direction, design system, iconography, blueprint, architecture, log updated for v2
- [x] Licences for font and icons included
- [x] Findings have severity; verification reports pass / not-run accurately
- [ ] Build and device verification (owner)

## Phase 1 audit note
v2 and Phase 1 have NEVER been compiled (no Android SDK in the authoring sandbox). Static checks only. Expect a few compile errors on first build; send the error list for a fix pass. Audio effects behave differently across OEMs — test EQ on a real device.

## T06c audit note (lyrics, 2026-10-19)

| Check | Method | Result |
|---|---|---|
| LRCLIB reachable / API shape | Live HTTP from sandbox | Pass — `/api/get` and `/api/search` 200, ~6 s; UA not required |
| 503→ busy classification, chain continues, single retry | Code inspection | Pass (static) |
| Failed results never cached; NotFound TTL unchanged | Code inspection | Pass (static) |
| Friendly failure messages wired to strings | Code inspection | Pass (static) |
| Showcase: colour/scale falloff, marker, edge fades, follow-yield | Code inspection only | Pass (static) |
| Showcase feel on device (timing, thresholds) | — | Not run — needs device |
| 200 % font, TalkBack, reduce-motion on the new sheet | — | Not run — needs device |
| New sheet compiles | — | Not run — owner rebuild |

Owner action: rebuild, open lyrics on the previously failing song twice (the first pass should now mark "busy", a later pass should load), then run the sheet through the device script lines 11 (200 %, TalkBack, animation scale 0).

## T07 audit note (streaming, 2026-10-19)

| Check | Method | Result |
|---|---|---|
| Extractor API usage matches v0.26.5 exactly (ServiceList has no `YouTubeMusic`; `MUSIC_SONGS` filter; `StreamInfoItem` getters; `AudioStream.bitrate/getContent`; `SearchInfo.getInfo`; `getSearchQHFactory`) | Source cross-check against the v0.26.5 extractor sources on GitHub | Pass (static) |
| Resolve chain: marker → resolver → best audio-only stream | Code inspection | Pass (static) |
| Local playback path unchanged after the new `DataSource` chain | Code inspection (`DefaultDataSource` keeps its own `content://` branch; resolver is a pass-through for non-`dopavin-yt` uris) | Pass (static) — real-device remaining |
| Search section states (hidden / idle / loading / unreachable / rows) do not fire the local empty state; toggle off hides section | Code inspection | Pass (static) |
| Artwork: thumbnail on rows, mini player, Now Playing, tint, notification (`setArtworkUri(artworkUri)`) | Code inspection | Pass (static) — not yet device-run |
| Restore keeps online rows from metadata cache; uncached ids dropped | Code inspection | Pass (static) |
| Settings toggle persists and switches the search section instantly | Code inspection | Pass (static) |
| Actual YouTube search/resolve/playback on a real network | — | Not run — needs device |
| Thumbnail decode + LRU reuse while scrolling online rows | — | Not run — needs device |
| Skip-to-next behaviour when a video is blocked / has no audiostream | — | Not run — needs device |
| 200 % font, TalkBack, reduce-motion on the new rows/hint | — | Not run — needs device |
| New code compiles | — | Not run — owner rebuild |

Owner action: rebuild in CodeAssist (errors first). Then: search something ("Nucleya"), confirm the local section still shows and a YouTube row plays; confirm with airplane mode that local behaviour is unchanged and the YouTube section shows the "could not be reached" line rather than the empty state.

## T07b audit note (online-first Home + Better Lyrics, 2026-10-20)

| Check | Method | Result |
|---|---|---|
| Better Lyrics API shape (`{"ttml": …}`), keyless 401/422 behaviour, param list | Verified against docs.betterlyrics.org | Pass |
| TTML → line sync parser (clock forms, word collapse, namespace-blind `<p>` match, bad-input → empty) | Code inspection | Pass (static) |
| Lyrics chain order (LRCLIB exact → LRCLIB search → Better Lyrics → NotFound/busy decision) and cache untouched | Code inspection | Pass (static) |
| History store: identity upsert with 90 s gap, 100 cap, main-thread mirror, JSON round-trip | Code inspection | Pass (static) |
| Home online zone order (categories → loading hint → related → artist shelves → local divider) and offline = old Home verbatim | Code inspection | Pass (static) |
| Empty library + online active still shows Home (online-first) | Code inspection | Pass (static) |
| Route.OnlineList flow (push, stale-response guard, loading / unreachable / empty / rows) | Code inspection | Pass (static) |
| Component/token use unchanged (SectionHeader, SongRow, EqualizerBars, placeholderBrush(seed), no new literals) | Code inspection against sources | Pass (static) |
| New strings all referenced; stringResource args match placeholders | Code inspection | Pass (static) |
| History content never leaves the device; only queries/artist names go out | Code inspection | Pass (static) |
| Home shelves behave on a real network (related populated, artist shelves populated, refresh works) | — | Not run — needs device |
| Airplane mode: zone collapses to categories, no error noise; toggle off: old Home | — | Not run — needs device |
| Category tile → list → play-through; back; play from a shelf; queue restore of online list | — | Not run — needs device |
| Better Lyrics miss path shows LRCLIB-quality behaviour (no false NotFound caching) | — | Not run — needs device |
| 200 % font, TalkBack, reduce-motion on tiles/carousel/list screen | — | Not run — needs device |
| New code compiles | — | Not run — owner rebuild |

Owner action: rebuild in CodeAssist (send error list first if it fails; JitPack's first resolve was already done for T07, so no new downloads expected here). Then on device: open Home (categories visible; if you have local plays, artist shelves appear), play an online row once, come back to Home — a "Because you played" row should appear. Airplane mode: online shelves vanish, local shelves + categories tile surface stay; toggle the YouTube setting off for the original Home. Try a category tile (Punjabi hits) — rows should load and play top-down.

## T08 audit note (Music Core: moods, radio, autoplay, feedback — 2026-10-20)

| Check | Method | Result |
|---|---|---|
| Spec rules preserved: single playback authority, no second ExoPlayer, no service replacement | Code inspection (only `appendBatch` added to `PlayerConnection`) | Pass (static) |
| Scorer = spec §7.4 with available-weights normalisation; missing feature never scored as zero | Code inspection + unit tests exist | Pass (static) |
| Reason codes truthful: mood = retrieval-bias only on seeded online candidates; library rows carry no mood feature | Code inspection | Pass (static) |
| No language category/filter/score-signal anywhere | Code inspection of the whole feature set (`Mood` seeds only bias search phrasing) | Pass (static) |
| Hard exclusions: queued ids, blocked ids, repeats off handling overflow | Code inspection + `planBatch` tests | Pass (static) |
| Manual protection: autoplay/queue batches only append (`addMediaItems` at end); replace paths are explicit user presses | Code inspection of `PlayerConnection.appendBatch` + `playSuggestions` | Pass (static) |
| Smart Autoplay gates: toggle + repeat OFF + remaining ≤ 2 + < MAX_QUEUE + cooldown + single-flight | Code inspection | Pass (static) |
| Feedback: completions ≥ 90 %, soft skips, auto-failures recorded as plain plays, never cached as negative | Code inspection | Pass (static) |
| Privacy: all stores app-private JSON; no upload path; clear/reset present | Code inspection | Pass (static) |
| Offline: autoplay/mood fall back to local-only; suggestions screen shows the empty hint | Code inspection | Pass (static) |
| Strings: 22 new, all referenced, no language labels | Static string audit | Pass (static) |
| Unit tests (scorer normalisation / reasons; planner exclusions / runs / dedupe) | JVM JUnit written; execution requires `testDebugUnitTest` | Written — Not run (no JVM in sandbox) |
| Mood batch on device (local + online mix, reasons readable, Play all / Append) | — | Not run — needs device |
| Song radio from Search row menu; autoplay extension observed at queue end | — | Not run — needs device |
| Not-interested removes songs from future batches; reset clears both stores | — | Not run — needs device |
| 200 % font, TalkBack on mood row, S-11 pills and rows | — | Not run — needs device |
| New code compiles | — | Not run — owner rebuild (third pass) |

Owner action: rebuild in CodeAssist (error list first if red). Then on device: (1) Home → tap **Romantic** → batch loads; press Play all (explicit replace) and once Append; (2) play 2–3 songs end-to-end, watch the queue gain ~8 picks near the end with autoplay ON, and nothing when toggled OFF; (3) long-press/overflow → **Not interested** on one row, confirm it stops appearing in mood/radio batches; (4) Settings → **Reset personalization & history** → history gone; (5) airplane mode → mood batch becomes local-only (or empty state), never a crash.

## F-97 audit note (Home online-only: no local shelves, history shelf, five artist sections — 2026-10-20)

| Check | Method | Result |
|---|---|---|
| No local-library shelves left on Home; Search/Library still carry everything local | Code inspection (`HomeScreen.kt` local zone deleted; hero + permission state untouched) | Pass (static) |
| History shelf: newest-first order from the timestamp sort (in-place upserts make array order wrong — sorted) | Code inspection of `HistoryStore.recent` | Pass (static) |
| History works offline and with the toggle off (`HomeUi(history = …)` in the `!allow` branch, refreshes via `homeRequests` on every play) | Code inspection | Pass (static) |
| Artist sections: five shelves × 8 songs, each with See all → full online list; failure leaves that shelf quietly empty | Code inspection | Pass (static) |
| History cards local vs online: both render through `OnlineSongCard` (`song.artworkUri` resolves content-URI artwork and remote thumbs) | Code inspection | Pass (static) |
| Strings: `home_history` added and referenced; removed shelves' strings unreferenced but retained | Static string audit | Pass (static) |
| Home on device: history shelf appears after first plays, artist sections only while toggle ON, no regression in hero/moods | — | Not run — needs device |
| New code compiles (F-97 rides on the same fourth build as T08) | — | Not run — owner rebuild |

## F-98…F-107 audit (UX & Feature Upgrade — 2026-10-20)

| Check | Method | Result |
|---|---|---|
| Universal menu identical across Library/Search/Collection/Online/Suggestions/Home/Queue (one `SongActionsMenu`) | Code inspection: all call sites route through the shared component; conditional items appear only when their capability exists | Pass (static) |
| Play Now documented as the explicit queue-replacing action wherever offered | Code inspection (menu labels + strings) | Pass (static) |
| Multi-select: long-press starts, tap toggles, bar hides on empty, actions non-destructive, Select all bounded to the visible list | Code inspection (`LibraryScreen.kt`) | Pass (static) |
| Queue: drag reorder commit-on-drop (no mid-gesture list mutation), swipe threshold 112 dp, Undo re-inserts at the exact index, chips keyed by mediaId and survive removal/re-insertion, clear-upcoming confirmed and never touches the playing row | Code inspection (`QueueScreen.kt`, `PlayerConnection.kt`, `MusicViewModel.kt`) | Pass (static) |
| More sheet: every row backed by a real behaviour (speed→runtime, skip-silence→runtime+store, output→AudioManager read + system settings intent, radio/details→existing routes); gapless/crossfade/normalisation absent, not greyed | Code inspection (`NowPlayingScreen.kt`) | Pass (static) |
| Storage dashboard shows only real numbers; no clear-cache row (memory-only cache), no downloads fiction | Code inspection (`ExtrasScreens.kt`, `MusicRepository.kt`) | Pass (static) |
| Duplicates delete never bypasses system consent; below R shows the honest unavailable message; refusal changes nothing | Code inspection (`MusicRepository.deletableRequest`, VM `deleteDuplicates/onDuplicateDeleteResult`) | Pass (static) |
| Settings: search filters in place, destructive restore defaults confirmed, likes/hidden survive restore | Code inspection (`SettingsScreen.kt`, `SettingsStore.restoreDefaults`) | Pass (static) |
| No white flash on themed cold start (window background pre-read before setContent) | Code inspection (`MainActivity.onCreate`) | Pass (static) |
| 48 dp targets on new bars/pills; text sp; new rows scroll (never clip) at 200 % | Code inspection (Dimens usage; `sel_*` bar rows use IconAction 48 dp targets) | Pass (static) |
| Touch behaviour of drag/swipe/long-press on a real queue; 200 % font queue wrap; TalkBack on chips and the selection bar | — | Not run — needs device |
| DuplicatesTest / PlaylistCodecTest green on JVM | — | Not run — owner can run `gradlew :app:testDebugUnitTest`; CodeAssist assembleDebug ignores them |
| Whole upgrade compiles | — | Not run — owner build #5 |

## Build #5 error-list audit (2026-10-20)

| Check | Method | Result |
|---|---|---|
| All six error groups fixed in place; no new references introduced (Media3 `removeMediaItems`, direct screen imports, RootContent/RouteContent signatures, helper `liked` threading, semantics `heading`, data-package imports) | Code inspection after fix | Pass (static) |
| Sweep for the same failure class (file-facade references, helper-scope `liked`/`songsById`/`playlists`/`origins` uses) across every screen and MusicApp | Scripted scope scan + grep | Pass (static) — remaining hits were parser artifacts, verified against real signatures |
| Whole batch compiles | — | Not run — owner rebuild |

## Downloads-first batch audit (2026-10-20)

| Check | Result |
|---|---|
| Kotlin syntax of all sources (kotlinc 1.9.24 parse) | Pass (no syntax errors) |
| Type-check / Gradle build against Android, Compose, Media3, NewPipe | **Not run** (no SDK) |
| Every `R.string.*` defined; no duplicates; XML well-formed | Pass (script) |
| Crash scenario (EmptyState with infinite height) | Not run on device; fix reviewed in code |
| Bottom sheet for every "more options" | Pass by code inspection: no `DropdownMenu` remains |
| Download, offline play, save-to-device end to end | **Not run** |
| 200 % font scale, 48 dp targets on new screens | Not run |
| TalkBack labels on new controls | Not run (labels exist in strings.xml) |

## Home / landscape / start-up audit (2026-10-21)
| Check | Result |
|---|---|
| Syntax of all sources (kotlinc parse) | Pass |
| Home tap plays song (shelf card, quick pick, row) | **Not run** — fix reviewed in code only |
| Landscape: Home two-column, Now Playing two-pane, no recreation on rotate | **Not run** |
| Cold start shows cached shelves before network | **Not run** |

## 2026-10-22 glass audit (static)
- Contrast: glass tint defaults to the surface colour at 18 %, text stays textPrimary on top; with Crystal preset (tint 8 %) text over bright art may lose contrast - check on device.
- Fallback: glass off / API < 31 / animations off draws the original solid surfaces, so no information depends on blur.
- Touch targets unchanged (48 dp); switch track 52x32 dp is display-only inside a full-width row.
- Open: 200 % font and landscape checks of the floating nav and the Glass page.
