# 05 — Screen Blueprint (v2)

## 1. Navigation map

```
Access (S-01)  ── granted ──▶  Shell
Shell = [ content ] + [ floating mini player (C-01, when something is loaded) ] + [ bottom nav (C-02) ]
  Bottom nav: Home (S-02) · Search (S-03) · Library (S-04)
  From any of them: Album / Artist page (S-05)  — nav + mini player stay
  From mini player or hero: Now Playing (S-06) — slides up, nav + mini player hidden
  From Now Playing: Queue (S-07)
Back: pops pushed screens; from Search/Library goes to Home; from Home exits.
Tapping a nav item resets the pushed stack to that destination.
```
Nav and mini player are hidden while the keyboard is open.

## 2. Screens

### S-01 Access (welcome + permission)
Gradient glow on top; logo disc (120dp, gradient); "Dopavin"; tagline; three feature lines (local files, plays with screen off, private/offline); explanation sentence for the current state; one gradient button. States: NotAsked ("Allow access"), Denied ("Try again"), Blocked ("Open settings"). Scrolls on small/large-font screens.

### S-02 Home
1. Greeting by hour (display) + "N songs on your phone".
2. Hero card (28dp radius): **continue listening** (tinted by artwork, art 112dp, title, artist, play/pause pill; tap opens Now Playing) or **shuffle all** (brand gradient, shuffle button).
3. Quick tiles: up to 6 recently added albums, 2 columns.
4. "Recently added" album carousel (See all → Library/Albums).
5. "Your artists" circular avatar carousel (See all → Library/Artists).
6. "Latest songs": 5 rows (See all → Library/Songs sorted by recent).
States: loading (skeleton), empty library, error with retry.

### S-03 Search
Title, 52dp pill field (search icon, clear button, Search key closes keyboard). Empty field: "Browse your music" tiles (Songs, Albums, Artists, Recently added — colourful, jump into Library) + "Popular artists". With a query: artists carousel, albums carousel, song rows (play within the result list). No results: message + "Clear search".

### S-04 Library
Title + sort button (Songs only). Chips: Songs · Albums · Artists. Songs: Shuffle (gradient) / Play / count; section letters when sorted by title; alphabet fast-scroller with letter bubble. Albums: adaptive grid (≥156dp). Artists: avatar rows.

### S-05 Album / Artist page
Header tinted from artwork: 232dp art (rounded for album, circle for artist), title, subtitle, Shuffle (tonal) and a round Play/Pause (reflects whether this collection is playing). Album rows show track numbers (equalizer for the current one); artist rows show art. Top bar: transparent with a scrimmed back button; turns solid with the title once scrolled. Missing item: message + "Back to library".

### S-06 Now Playing
Gradient wash from artwork tint to canvas. Top: close chevron, "NOW PLAYING" overline, queue button. Artwork up to 380dp, 28dp radius, shadow, scales 0.88 when paused, cross-fades on track change; swipe sideways = previous/next. Title (headline, 2 lines), artist. Seek bar (gradient, thickens on drag, elapsed / remaining). Transport: shuffle, previous, 76dp gradient play/pause (buffering ring), next, repeat/repeat-one — toggles show colour + dot + spoken state. "Queue" pill. Swipe down anywhere closes (screen follows the finger, closes past 120dp). Landscape: artwork left, controls right.

### S-07 Queue
Top bar with count. Rows with artwork (equalizer on current), title, artist, duration, remove button; opens scrolled to the current item. Tap jumps. Removing the last item returns to Home.

### C-01 Mini player
Floating card, 20dp radius, shadow, tinted by artwork, 46dp art, title/artist, 44dp gradient play/pause, next, 3dp gradient progress line. Tap opens Now Playing; swipe left = next, right = previous (also exposed as accessibility actions).

### C-02 Bottom navigation
64dp + system inset. Selected: filled icon, accent colour, tinted pill behind icon, announced as selected tab.

## 3. Acceptance criteria (device)

1. Every screen above renders without clipping at portrait, landscape, and 200% font.
2. Nav item → correct screen; back from Search/Library → Home; scroll positions survive opening an album and returning.
3. Mini player visible whenever a song is loaded and the nav is visible; hidden with keyboard.
4. Now Playing: tint follows art within ~0.7 s; paused art is visibly smaller; swipe gestures work; seek bar drag does not stutter and seeks on release.
5. Alphabet scroller jumps to the right letter, bubble shows, no jank on 5,000+ songs.
6. Reduce motion (animation scale 0): no looping or sliding animation; state still visible.
7. TalkBack: nav tabs announce selected; seek bar adjustable; rows announce "Now playing".

## Addendum — Phase 1 screens
- S-07 Queue: BottomSheet over Now Playing (82% height, drag handle, scrim, back closes). Header = title + count; current row shows equalizer bars.
- S-08 Settings: reached via gear on Home and Library headers. Theme, playback (skip silence), library (min duration), Equalizer entry.
- S-09 Equalizer: on/off, presets, per-band sliders, bass boost, loudness.
- Now Playing: volume row, action pills (Sound, Sleep, Queue), swipe down collapses (disabled while a sheet is open).
- Sheets: Sound (compact EQ), Sleep (15/30/45/60 min, end of track, cancel).
- Now Playing: heart (like) toggle beside the track title; liked song ids persist in SettingsStore.
- Phase 3: Sound sheet starts with playback speed chips (0.5x-2x); Library gets a Liked tab; Settings gets "Pause when headphones disconnect" and "Keep screen on in player".
- Lyrics: pill on Now Playing opens a 90% sheet; states Loading / Found (synced or plain) / Not found / Instrumental / Failed (retry) / Off (turn on).

## Addendum — YouTube results in Search (owner-approved 2026-10-19)

### S-03 Search changes
- Results keep the order: artists carousel, albums carousel, local song rows, then a **"YouTube"** section header and up to 10 online rows.
- Online rows use the normal song row: thumbnail from YouTube (placeholder gradient when it is still loading), title, and the subtitle "`artist · YouTube`" (string `online_song_subtitle`). They start playback directly.
- While the YouTube request runs, the section shows one quiet line "Searching YouTube…" (`search_online_loading`); if the request fails, "YouTube could not be reached. Songs still play when the connection is back." (`search_online_unreachable`). Neither replaces the local results, and the local "no results" empty state never appears just because YouTube failed — it needs local results empty AND nothing streaming from YouTube.
- The section does not appear when the toggle is off, or the query is shorter than two characters.

### S-08 Settings changes
- A new toggle "Show YouTube results" (`settings_online_search`): default on; turning it off brings back the pure search preview and hides online rows immediately. Copy states plainly that playing online rows uses the internet.

## Addendum — Home, online-first (owner-approved 2026-10-20)

> **Third pass (owner, same day): the local zone left Home.** Home now reads: greeting + hero → mood chips → category tiles → loading hint → **"Your artists" YouTube shelves (top five played artists, each with songs + See all → full list)** → **"Because you played X"** → **"Recently played"** (the F-86 tapelog, newest first; local and YouTube entries both open and play; offline-solid). No local-library shelves remain on Home — offline parity lives in Search and the Library tab. The empty-library permission state stands unchanged when online discovery is off.

### S-02 Home changes
- With the YouTube toggle on, Home orders itself: greeting + hero, then the **online zone**, then a quiet divider "**Aapka music**"-style local zone with the existing hero-follow shelves (quick tiles, recently added, artists, latest songs). The hero itself keeps working across local and online songs.
- Online zone, top to bottom:
  1. **Category tiles** (six: Charts, Punjabi, Bollywood, Romantic, Lo-fi, English hits) — big coloured tiles like Search's browse tiles; each opens the online list screen. Cold start fills nothing else.
  2. **"Because you played X"** carousel (online song cards): related tracks of the most recent online song. Hidden with no history or no related result.
  3. **Your going-top artists** (up to 3): the most-played artists from the local play history become YouTube song shelves (4 cards each + "See all" → full list). Hidden with no history.
- Every online shelf shows 4–10 cards; all failures are silent (empty shelf or a hidden zone) — Home never turns into an error screen. Old local Home is shown verbatim when the toggle is off, and the online zone collapses automatically when the network fails.

### S-10 Online list (new pushed screen)
- A thin title bar (back + shelf title) and a list of online song rows played top-down. Loading / unreachable states reuse Search's quiet hint lines. No tabs, no local mixing — this is a shelf's full view.

### Lyrics chain (Better Lyrics model)
- Lookup order per song: LRCLIB (unchanged) → public Better Lyrics API (`api.betterlyrics.org/getLyrics`, TTML parsed into the existing line-sync model; timestamps from TTML paragraph `begin`). The Better Lyrics request carries `videoId` for online songs (helps their cache revalidation).
- Hit from any provider renders identically; cache format unchanged. A source failing never blocks the chain — the next source tries. Word-by-word syllable rendering: out of scope (F-90).

## Addendum — Music Core: moods, suggestions, radio (owner spec, 2026-10-20)

### S-02 Home changes (second pass)
- A **mood chip row** (six horizontal pills: Chill, Happy, Energetic, Focus, Reflective, Romantic) sits above the category tiles in the online zone — but the row is visible with or without the YouTube toggle, because mood queues work offline too (local candidates). Tapping a chip pushes S-11.
- Everything else on Home is unchanged; moods touch no local shelf.

### S-11 Suggestions (new pushed screen)
- Thin top bar (back + mood/radio title + subtitle "Generated for you"), then a compact action pair — **Play all** (filled tonal; replaces the queue — explicit) and **Append** (adds to the queue bottom; existing items untouched) — followed by the batch as ordinary song rows.
- Each row's subtitle carries a truthful reason word before the artist: "Mood picks", "You keep playing this", "Favourite artist", "Back in rotation", "From this session", or "Popular nearby" — one of six, ever, from the strongest scoring feature. Rows carry Play-next / Add-to-queue / **Song radio** / **Not interested** in the overflow menu.
- Unreachable / empty shows the quiet hint lines (same as Search's online section). Radio replaces nothing that exists (a fresh batch, "Play all" still explicit).
- 200 % font: rows scroll; buttons stay single-line labels.

### S-08 / S-05 Settings and row-menu changes
- Settings · Playback gains **Smart Autoplay** (toggle, default on; body explains it appends YouTube/local picks near the queue's end) and **Reset personalization + history** (SettingsRow with confirmation-free one-tap clear + snackbar).
- The song overflow menu gains two conditional rows — **Song radio** and **Not interested** — shown only where a `SongRow` caller passes them (Search rows, online list, S-11 rows; library/collection/queue keep the old menu).

### Queue sheet (unchanged behaviour, one caveat)
- No visible change in v1; generated items protect nothing readable there yet — provenance is internal (D-27). Autoplay respects the toggle; repeat modes gate it as before.

### S-12 Queue sheet upgrade (F-98/F-102)
- Header: title + song count, save-as-playlist (opens the shared name dialog with the resolved queue songs) and clear-upcoming (confirmation dialog, removes only entries after the playing song, snackbar confirms the count).
- Rows: tap = jump; accent title + equalizer bars on the playing row; fixed 72 dp height so drag displacement is exact; long-press + vertical drag reorders (lifted row gets a raised surface and accent title; drop commits through `moveInQueue`); horizontal swipe removes with an Undo snackbar (single-step, in-memory).
- Three-dot menu (one instance of the universal `SongActionsMenu`): Play now, Move up / Move down (hidden at the edges), Play next, Favourite, Song radio, Details, Share/Hide (local files only), Remove. Generated rows carry a small "Auto" chip in the artist line (D-32).
- Acceptance: reorder, remove+undo, save and clear all behave; chips survive removal/re-insertion; nothing offers actions the row cannot perform.

### S-13 Song details (F-99)
- Artwork hero with accent tint, title/artist/album, then honest fact rows: duration, file size (or "Unknown"), date added, file path (or "Storage: streaming" for online rows — no invented facts).
- Actions: Play, Song radio, Favourite. Share lives in the row menus, not here (details is also reachable for online rows).

### S-14 Storage dashboard (F-104)
- Three real numbers only: local library size (sum of MediaStore SIZE), Dopavin app data (preferences + playlists.json + lyrics cache), free space on shared storage (`StatFs`).
- Standing note: Dopavin cannot download songs (provider rules); no clear-cache row (artwork cache is memory-only). No fake states.

### S-15 Duplicates (F-105)
- Finder: normalised title+artist, both durations within ±2 s, ≥45 s, local files only. Checkbox rows (default: keep newest), Keep/Delete chips per group, confirm dialog, then the system consent dialog (`createDeleteRequest`, API 30+). Below R: an honest "not available on this Android" message. Result → snackbar + library rescan.
- Acceptance: nothing is deleted without the system dialog; a refusal changes nothing.

### S-16 Playlists (F-103)
- List screen: create (name dialog), open, delete (confirmation). Detail: play (replaces queue — explicit), shuffle, append, remove-song (with snackbar).
- Songs are snapshots at add-time (same model as the queue restore). Rename is not in v1 (`07`).

### S-17 Settings v2 (F-106)
- Search field filters every category in place; categories: Appearance, Playback, Audio & effects, Library, Recommendations, Downloads & storage, Privacy & data, About.
- Advanced fold (Library): short-audio filter. About zone: version, about text, licences, and the destructive **Restore defaults** (red title, confirmation dialog; likes/hidden selections survive — they are curation, not preferences).
- Startup: MainActivity paints the window background with the stored theme's canvas colour before the first frame (no white flash after the splash).

### S-04 / S-06 / S-07 / S-11 updates (F-98/F-100/F-101)
- Library Songs + Liked: long-press starts multi-select; bottom bar (count, cancel, add-to-queue, like, add-to-playlist, select-all). Library top bar gains a Playlists shortcut.
- Now Playing: fifth pill "More" opens S-12's More sheet (F-101).
- Every song row/card/queue row now carries the universal menu; Library rows long-press means selection, so their menu is the three-dot button only.

---

## Addendum F-108…F-116 — Downloads-first Dopavin (2026-10-20)

Owner request: no local-music support; downloaded songs live inside the app; every "more options"
menu is a bottom sheet; Settings re-imagined. Screens changed (everything else is unchanged):

| Screen | Change | Acceptance |
|---|---|---|
| S-01 Access | **Removed.** No media permission is requested; the app opens straight on Home. | App starts with no permission dialog except the optional notification one (Android 13+). |
| S-02 Home | Hero (continue / shuffle downloads) → mood chips → **Downloaded** shelf (newest 12, "See all" opens Downloads) → online shelves (YouTube toggle) → history. Every shelf card has a visible 48 dp "more" button besides long-press. Empty state when nothing is downloaded and YouTube is off. | Shelf cards open the sheet; empty state offers "Find music". |
| S-03 Search | Albums removed (browse tile and results). Searches downloads + YouTube. | No album UI remains. |
| S-04 Downloads (Library tab, nav label "Downloads") | Active downloads card (progress, cancel/retry) above Songs / Artists / Liked. Sort opens a bottom sheet. | Failed downloads show a reason line (network, Wi-Fi only, space, unavailable). |
| S-06 Now Playing | Top bar: close · title · **More**. Download button beside the heart (download / progress / saved). Four equal action buttons (Lyrics, Sound, Sleep, Queue). Screens shorter than 640 dp scroll instead of squeezing the artwork. | Nothing is clipped at 200 % font scale. |
| S-08 Settings | **Hub of 7 category cards** (Playback, Sound, Downloads, Streaming & lyrics, Appearance, Privacy, About), each opening its own page; search flattens all rows. | System back from a page returns to the hub. |
| Sheet (all "more options") | One shared action sheet: header (artwork, title, artist) + rows, 56 dp minimum row height, destructive rows in the error colour. | Used by song rows, shelf cards, queue entries, sort. No dropdown menus remain. |
| Storage | Downloads count/size, app data, free space, delete-all (confirmed). | Numbers refresh when a download finishes. |

Removed: Access screen, Duplicates screen, hide/share-file actions, "hide short audio" setting, refresh library.

### Home (S-02) after F-117 — order top to bottom
Header (greeting, search, settings) → hero (Continue / Shuffle downloads / Get started) → **Quick picks** (up to 6 tiles, tap = play, long-press = sheet) → mood chips → **Trending now** (or skeleton) → Because you played X → Downloaded → Top songs of artist shelves → category rail → Recently played. Width ≥ 600 dp: hero and quick picks share one row. Acceptance: a tap on any tile or card starts playback; with a warm cache the first screen shows shelves before any network call returns.
