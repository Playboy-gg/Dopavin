# 11 - Apple design catalog audit (2026-10-26)

Source: the owner's `apple-design-system.zip` (a name index of Apple's HIG: components, foundations, patterns, inputs).
It contains no Apple files and no pixel specs, only principles (clarity, deference, depth, hierarchy, harmony,
consistency) and one-line rules per topic. The audit checks the code against those rules. Android is not Apple, so the
audit takes the principles, not Apple's look (the catalog's own governance file says the same).

Method: code inspection and computed numbers only. Nothing was run on a device. Status words: CONFIRMED = seen in code,
NEEDS DEVICE = cannot be judged from code.

> Update 2026-10-27: problems 1, 2, 4, 5 fixed in code (F-172..F-175), problem 3 partly (marquee, toast, page swap, back animation; karaoke sweep and tint fade still open). Unverified on device.

## Problems found

| # | Sev | HIG topic | Finding | Evidence | Fix |
|---|---|---|---|---|---|
| 1 | High | Loading / Feedback ("never a stuck screen, allow retry") | Every online error state is a plain line of text with no Retry: radio/list page, mood and song-radio, Search online block, Artist page. Only Lyrics offers Retry. | `OnlineListScreen` (Unreachable branch), `SuggestionsScreen`, `SearchScreen` ~549, `ArtistProfileScreen` ~155 | One shared `ErrorState(text, onRetry)` with a Retry button that re-runs the same request |
| 2 | High | Managing notifications ("ask at the right moment") | The notification permission is requested at first launch, before the person has seen any value. | `MainActivity.onCreate -> askNotificationsOnce()` | Ask on first play or first download, with a one-line reason |
| 3 | High | Motion / Accessibility (honour Reduce Motion) | Reduce motion (animator scale 0) is honoured by only 4 places: press scale, skeleton pulse, equaliser bars, one Now Playing spot. Not honoured: infinite title marquee, toast spring, page cross-fade, new predictive-back animation, karaoke sweep, tint fade, ambient fade. | grep `motion.reduce`: Controls, Equalizer, States, NowPlaying only; `basicMarquee(iterations = Int.MAX_VALUE)` | Read `AppTheme.motion.reduce` in `MarqueeText`, `ToastHost`, the page container, `Karaoke` sweep; use instant or fade-only |
| 4 | Med | Typography / Dynamic Type (survive 200%) | Controls with fixed heights that hold text: dialog pill buttons `height(48.dp)`, Home mood chips `height(46.dp)`. At 200% font the label clips. | `GlassDialog.kt:226`, `SleepDialog.kt:187`, `HomeScreen.kt:1160` | `heightIn(min = 48.dp)` |
| 5 | Med | Layout / touch targets (48 dp) | Mood chips are 46 dp high, under the project's own 48 dp rule. | `HomeScreen.kt:1160` | Same fix as 4 |
| 6 | Med | Accessibility / Materials ("test legibility at the full transparency range") | Glass is switched off completely when "remove animations" is on, so Reduce Motion and glass are tied together. The app has its own glass slider (good), but text on glass was never checked across tint 0..100% and on bright artwork. | `Glass.kt: enabled = glassEnabled && !reduceMotion` ; docs/08 has no glass-legibility row | Decouple (own setting only); add a legibility check row to docs/08 |
| 7 | Low | Color ("prefer semantic tokens") | 99 colour literals (`Color.White/Black`, `Color(0x..)`) outside `ui/theme`. Most are scrims over artwork (allowed by AGENTS section 4) but they are not listed, so real violations hide among them. | grep over `ui/` | List allowed scrims in docs/07; move the rest to tokens |
| 8 | Low | Accessibility (VoiceOver / TalkBack) | 37 icons with `contentDescription = null`. The ones read (Home badges, play overlays) are decorative, but nobody has run TalkBack over the app. | grep | A TalkBack pass; record it in docs/08 |
| 9 | Low | Onboarding / Settings | No first-run guidance; "Dynamic tint", glass and the swipe-back gesture are not explained anywhere. Settings itself is well organised (8 categories, search, defaults). | no onboarding code | One skippable tip row on Home (HIG: inline tips, not manuals) |

## Checked and fine (computed or seen in code)

- Contrast, computed from `Color.kt` (WCAG ratio): dark text 17.9:1, secondary text 8.1:1 (7.0:1 on raised surfaces),
  accent 7.0:1; light text 17.0:1, secondary 6.2:1, accent 5.3:1; ink on accent 6.8:1 dark / 5.7:1 light; error 7.1 / 6.1.
  All pass 4.5:1. The new tint presets keep ink at 3:1 or better (unit test) and darken the light-theme accent to about 3:1.
- Type is in sp with a clear scale (32/24/20/16/14/12), so hierarchy and font scale follow the system.
- Destructive actions are confirmed or undoable: delete playlist, delete all downloads, restore defaults, clear
  personalisation are confirmed (6 confirm dialogs); queue removal has Undo.
- Playing audio: Media3 session with audio focus, pause on unplug, now-playing notification.
- Searching: history and suggestions exist. Haptics exist and are limited to two components.
- Both appearances plus pure black exist; back button and titles follow the rules in docs/10.

## Suggested order

1 (Retry), 2 (notification timing), 3 (Reduce Motion), then 4+5 together (one small change), then 6-9.

## Not covered (needs a device)

Real glass look over artwork, 200% font screenshots, TalkBack, landscape, glass performance, the new back gesture feel.

---

# Screen by screen (added 2026-10-26)

The first part above is by TOPIC. This part is by SCREEN. Depth: a structure scan of every screen file (state handling,
accessibility labels, fixed heights, motion, confirmations, header layout) plus targeted reads where the scan showed
something. The 7,683 lines of screen code were NOT read line by line. "Not checked" lists what only a device can show.
Numbers refer to the problem table above.

| Screen | Findings (code) | Not checked |
|---|---|---|
| Home | No failed or offline state: if the online fetch fails with an empty cache the shelves just disappear (only a loading skeleton and "hide empty shelves" exist), no Retry (1). 7 fixed heights incl. mood chips 46 dp (4, 5). 8 decorative icons without labels (8). Notification prompt fires on first open (2). | Carousel motion, 200% font, hero over artwork |
| Search | Online "unreachable" is a line of text, no Retry (1). 3 fixed heights (4). Has loading skeletons, history, empty states. | Keyboard overlap, result ranking feel |
| Library hub | Plain list, clean. 2 icons without labels are decorative inside a labelled row (fine). | - |
| Downloads | Best error handling in the app: per-download Retry and Cancel, errors named (no space, Wi-Fi only...), empty state with action, multi-select bar. 2 fixed heights (4). Title now under the back button, scrolls. | Selection bar vs gesture bar |
| Now Playing | Reduce motion honoured in one place only; the title marquee ignores it (3). Close and More are glass sampling the player's own layer. 3 decorative icons. | Glass over bright artwork (6), drag-down feel |
| Lyrics (player stage and sheet) | Best state coverage: loading, empty, error with Retry, provider choice. Karaoke sweep ignores reduce motion (3). | Indic scripts on device, long lines at 200% |
| Queue | Remove has Undo, clear is confirmed (8 confirm paths). Artwork now present for online songs. | None found. (Correction: queue rows DO have "Move up" / "Move down" in the row menu, `QueueScreen.kt` `onMoveUp/onMoveDown`; the earlier finding 10 was wrong.) |
| Driving | Only 3 labelled controls found in the file (exit, previous, next); play/pause comes from the shared button. No semantics on the lyrics block, no motion handling (3). Keeps the screen on (good). No lyrics loading or error line. | Glare, large-target feel |
| Settings hub and categories | Title and search scroll, glass back fixed. Sliders carry label plus value text (good). Destructive rows are confirmed (16 confirm paths). Search flattens all categories (good). | 200% font on switch rows |
| Appearance (tint) | Swatches have labels and selected state; contrast of derived accents unit-tested. Dynamic tint follows artwork and fades 450 ms (3). | Look on device |
| Glass and Style | Slider values are announced. 2 fixed-height preview areas are decorative. No legibility check for text on glass (6). | The preview vs the real nav bar |
| About | 4 decorative images; links open with a no-browser fallback. Repository link unverified. | - |
| Equalizer, Storage, Playlists | Clean after the header change; Playlists delete is confirmed. No loading needed. | Band sliders with TalkBack |
| Artist profile | Failed load is one line of text, no Retry (1). Cover hero fades into the page. | Subscribe feedback |
| Collection (album, local artist) | Own floating bar with fading title; back is now the shared glass button. Empty state present. | Tint header over light theme |
| Radio / list page | Cover page layout done. Unreachable is plain text, no Retry (1). | Large lists, Save as playlist feedback |
| Subscriptions | Pull to refresh, loading, empty; no explicit "refresh failed" message. | - |
| Suggestions (mood, song radio) | Unreachable is plain text, no Retry (1). | Reason text honesty on device |
| Playlist detail | Cover page layout, remove per song, delete confirmed. | - |
| Song details (dialog) | Single dialog host, closes with back. Not read in depth. | 200% font, long titles |
| Crash reports (debug only) | Fine; no TalkBack pass. | - |

## Screen-level summary

- Screens with a real failure story: Downloads, Lyrics. Screens that fail silently or with a dead-end line: Home, Search,
  Artist, Radio/list, Suggestions, Subscriptions (6 of 8 online screens).
- Screens where Reduce Motion is respected: none fully. Now Playing, Home, Lyrics and the page transitions need it.
- Fixed-height text controls: Home (7), Search (3), Downloads (2), plus the shared dialogs.
- ~~Finding 10 (queue reorder has no non-drag alternative)~~ WITHDRAWN: the row menu has Move up / Move down.
