# 10 - UI rules from the owner (read before any UI change)

Collected from the owner's instructions and feedback during development (2026-10). They are binding. If a change
would break one, stop and ask. Verified-in-code status is in `07_IMPLEMENTATION_LOG.md`; this file only holds the rules.

## Pages that have a back button
1. The back button is fixed in the top-left and never scrolls. It is real liquid glass (it bends and tints what scrolls
   beneath it) and a solid disc when glass is off. It is drawn once by the app root (`FloatingBackLayer`), pages only call
   `FloatingBackButton(onBack)` or `ScreenHeader(...)`.
2. The page title sits BELOW the back button and is part of the scrolling content: it moves with the page. Optional
   actions (sort, new playlist...) stay on the title row.
3. Pages about one thing (artist, playlist, radio, album) use the cover-page layout: a large square cover that melts
   into the page, the name on it, the actions (Play / Shuffle / Save / Subscribe) on it as glass buttons, then the list.
   The header scrolls away; the back button stays. Do not copy an old layout when a redesign is requested.
4. The same applies to Downloads, Settings (every category), Subscriptions, Storage, Playlists, Equalizer, Suggestions,
   Crash reports.

## Liquid glass
5. Every control that floats over content (back button, player close/more, nav bar, mini player, toast, dialogs, menus)
   is liquid glass that samples what is behind it. Both modes must work: glass ON (real backdrop) and glass OFF (solid
   fallback via `glassSurface`).
6. Anything that SAMPLES `overlayBackdrop` / `contentBackdrop` must be drawn OUTSIDE the content Box that records
   `contentBackdrop`. Drawing it inside makes the layer contain itself and crashes the renderer (native stack overflow,
   F-164).
7. Dialogs and menus are not user-customisable and use the single dialog host.

## Motion
8. Back is predictive, iOS style: the page follows the finger by the drag distance, the previous page slides in from a
   parallax offset and un-dims as the drag grows; release past the point completes, dragging back cancels. The player
   and the queue only close as layers.
9. Long single-line titles (song names) use a marquee instead of cutting off.
10. Karaoke / synced lyrics animate smoothly and never break Indic matras or other scripts (use grapheme units).

## Colour
11. Tint: the user can choose an accent preset or "Dynamic" (follows the playing song's artwork). The accent drives
    buttons, icons' highlights, sliders and gradients through the theme tokens only; no screen hard-codes the accent.
12. Light, dark, pure black and 200% font scale must all stay readable.

## Content and honesty
13. Use real icons and assets, not placeholders. Never show fake or unverified providers, APIs or numbers.
14. Notices are an iOS-style toast (glass / solid by theme), not the Material snackbar; they auto-dismiss.
15. No "Vyora" anywhere in the project. No new dependency without the owner's approval. New strings go in new values
    files.

## Process
16. Deliver `Dopavin.zip` after every change; docs (01 scope rows, 07 log, CURRENT_STATE, TASKS) stay in step.
17. Debug builds keep a crash history (Settings > About > Crash reports); native crashes can only be seen in logcat.

## 16. Where a setting goes (owner rule, 2026-10-29)
Before adding ANY setting, pick its category from this table. If none fits, create a new category (enum entry, `cat_*` and `settings_sum_*` strings, a group in the hub, its labels in `rowLabels`) instead of squeezing the setting into the nearest one. Every new setting also needs its label in `rowLabels` so Settings search finds it.

| Category | What belongs there | Current settings |
|---|---|---|
| Playback | How songs are played and queued | skip silence, pause on unplug, keep screen on, keep pitch, sleep fade, autoplay, don't repeat suggestions, resume on connect |
| Sound | Equalizer and audio effects | equalizer, bass, virtualizer, speed |
| Downloads | Saving songs on the device | quality, Wi-Fi only, auto-download liked, limit, storage |
| Streaming & Lyrics | Network use for songs and lyrics | YouTube search, stream quality (Wi-Fi / mobile), lyrics online, lyrics provider, show current lyric |
| Appearance | Theme, colour and how the app feels | theme, pure black, accent tint, ambience, haptic feedback |
| Glass & Style | Liquid-glass look | glass options |
| Privacy | Personal data | reset personalisation, backup export / import |
| Updates | New versions of the app | check for updates, automatic check, download and install |
| About | App information and resets | restore defaults, licences, source |
Actions that are not settings (Share, Recap, Import from Spotify, Clear lyrics cache) live on the page they act on (Library hub, Storage), not in Settings.
