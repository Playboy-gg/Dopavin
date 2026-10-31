# 02 — Design Direction (v2)

## 1. Why v2

v1 was correct but generic: a flat list under a top bar, three equal tabs, a small mini player glued to the bottom edge, grey placeholders, system font. Nothing told the user whose app this was, and nothing rewarded opening it. The owner's verdict: "bahut basic". v2 is a redesign, not a re-skin.

## 2. Research (what professional players do, and what we took)

Observed patterns in mature music players (Spotify, YouTube Music, Apple Music, Poweramp, Namida-style local players). These are patterns recalled from the apps, not copied assets; no live screenshots were available in the authoring sandbox.

| Pattern | Seen in | Decision |
|---|---|---|
| Bottom navigation with 3–5 top-level destinations; filled icon + label for the selected one | All | D-11 Home / Search / Library |
| Home as a set of shelves (quick tiles, carousels) rather than a list | Spotify, YT Music | D-12 |
| Floating, tinted mini player above the nav bar with progress hairline | Spotify, Apple Music | D-13 |
| Full-screen player with a colour wash from the artwork, large rounded art, one dominant play button | Spotify, Apple Music | D-14 |
| Artwork "breathes" with play/pause; swipe artwork for next/previous; swipe down to close | Apple Music, Poweramp | D-15 |
| Immersive album/artist header that fades into a compact top bar | Spotify, Apple Music | D-16 |
| Section letters + fast-scroll on long alphabetical lists | Contacts, Poweramp | D-17 |
| Animated bars mark the playing item | Spotify, YT Music | D-18 |
| Pill chips for sub-views | YT Music, Material 3 | D-19 |
| Skeleton loading instead of a spinner | Most | D-20 |

Platform references used: Android Media3 background-playback guide, Android 13/14 permission and foreground-service pages (same as v1), Material 3 accessibility guidance (48dp targets, contrast).

## 3. Direction: "Dopamine on a dark stage"

- **Concept.** Dopavin = *dopamine* + *veena*. The app should feel like a small hit of joy: a dark, quiet stage where the music's own artwork and one warm gradient provide all the colour.
- **Personality.** Warm, confident, a little playful; never noisy. Rounded forms (14–28dp), generous spacing, large friendly type.
- **One brand gradient** — coral-orange `#FF8A4C` → hot pink `#FF3D81` — used for: primary play button, seek progress, primary buttons, the permission-screen logo, the launcher icon. Everywhere else colour comes from the artwork.
- **Dark first, light supported.** Dark canvas `#0B0B10` with three raised surfaces. Light canvas `#FAF7F5` with a darker gradient pair so white-on-gradient text passes contrast.
- **Type.** Plus Jakarta Sans (bundled, OFL): ExtraBold display, Bold headlines, SemiBold row titles, Regular body. Tabular figures for times.
- **Motion.** Springs for touch feedback (press scale 0.96), 240 ms fades for navigation, 700 ms colour drift for artwork tint, bottom-sheet-style slide for Now Playing, looping equalizer bars. All collapse to instant when the system animation scale is 0 (reduce motion).
- **Brand mark.** A note whose stem ends in a flag, with two small "pulse" bars beside it: energy (dopamine) + string/music (veena). Dark glyph on the gradient; monochrome variant for themed icons.

## 4. Decision log

| # | Decision | Reason | Revisit if |
|---|---|---|---|
| D-01…D-10 | From v1 (offline, local, Media3, token-driven, accessibility rules) | Unchanged | — |
| D-11 | Bottom navigation (Home, Search, Library) | Standard, thumb-reachable, replaces tabs-as-navigation | Playlists arrive (maybe a 4th item) |
| D-12 | Home built only from library data | No history storage in scope | History is added |
| D-13 | Floating mini player, rounded 20dp, tinted, swipe to skip | Reads as a card, not a toolbar | — |
| D-14 | Now Playing background = artwork tint → canvas gradient | Emotional link with the music; no extra dependency | Contrast fails on some art |
| D-15 | Artwork scale 0.88↔1 with spring | Shows play state without reading an icon | Reduce-motion (it snaps) |
| D-16 | Collapsing header on album/artist | Focus on art first, list second | — |
| D-17 | Alphabet scroller only for Title sort | Letters are meaningless for other sorts | — |
| D-18 | Equalizer bars + accent title + spoken state for "current" | Not colour-only | — |
| D-19 | Library sub-views as chips, not tabs | Navigation is now the bottom bar | — |
| D-20 | Skeleton rows while loading | Perceived speed | — |
| D-21 | Own tint picker (hue-binned, saturation-weighted) | Palette library not allowed in CodeAssist setup | Quality complaints |
| D-22 | Bundle font + generate icons from the official Material set | Brand voice; removes the hand-typed-icon risk (v1 UX-02) | — |
| D-23 | YouTube results live inside Search below the local section; online rows reuse the song row look with a "· YouTube" suffix and their thumbnail | Keeps one search surface and one row component; online is companion, not a takeover | Owner asks for a separate Explore tab |
| D-24 | Home becomes online-first when the toggle is on: online suggestions before the local shelves, offline ("your music") zone below; the online section is the first thing to collapse | The owner steered to a proper online-music-player approach while insisting local files remain the fallback | A pure-online mode request arrives |
| D-25 | Lyrics providers chained per song like Better Lyrics does it (LRCLIB → Better Lyrics API), parsed into the same LyricLine model | One display engine, one cache format; a hit from any provider looks identical | Word-by-word display is requested (needs a TTML renderer) |
| D-26 | Moods are replay goals, not inferred states: the mood row biases retrieval (seeded online search + taste/novelty scoring); local songs carry no mood data and score without that feature | Spec §6 — no fabrication, no diagnosis; the honest signal is what the mood query retrieved plus listening behavior | Real audio-feature metadata becomes available |
| D-27 | Queue provenance is session-only in v1 (a map beside the timeline, rebuilt as MANUAL after restore); generated batches always append at the end and are labeled to the user in the suggestions screen, not in the queue sheet | The timeline already restores itself through PlaybackStore; a parallel persisted provenance chain would drift from the real queue | Queue provenance chips in the queue sheet are requested |
| D-28 | Home is an online scene (F-97): the offline library shelves left Home and moved fully behind Search/Library; History (the play log) is Home's offline-solid section | The owner steered once more to a pure online-first Home, with history in its place | The owner asks for local shelves back on Home |

| D-29 | The universal song menu is one component (`SongActionsMenu`): rows, cards and queue rows all render it; long-press opens it on touch, the three-dot button keeps an equivalent path for discoverability and switch access | One component means one a11y story and one visual rhythm; conditional items (share/hide/radio) vanish instead of greying | A row type needs an action the others cannot have |
| D-30 | No global ripple override. The grey press tint stays only on standard Material surfaces (dropdown items, chips); all custom house controls keep `indication = null` + their own scale/ring feedback as before | Removing system ripples entirely would fight the platform's accessibility expectations; the house style already avoids double feedback | The owner asks for a fully custom press treatment system-wide |
| D-31 | Queue reorder = long-press + vertical drag with drop-time commit (the list is not mutated mid-gesture; displaced rows are drawn at a fixed row height of 72 dp so the displacement maths is exact); Move up/down menu items are the accessible fallback | Mid-gesture list mutation fights Lazy recycling and animation identity; fixed-height rows keep the drag honest without measuring passes | Rows must wrap at 200 % font (then drag pauses at the fold — documented limitation) |
| D-32 | Provenance chips in the queue show only the non-default case: "Auto" on generated rows, silence on manual ones | Labelling every manual row "You" is visual noise for the 95 % case; the absence of a chip IS the manual signal | The owner asks both chips to always show |
| D-33 | Multi-select is non-destructive by design: queue / like / playlist only. Batch deletion exists solely in the F-105 flow behind the system consent dialog | Two delete paths would double the audit surface for the same job | The owner asks for row-level delete inside multi-select |
