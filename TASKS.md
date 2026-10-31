# TASKS

Source docs: docs/01 (scope), 05 (screens), 06 (architecture), 07 (log), 08 (audit), 09 (NewPipe audit). Scope: **offline local-music player**; online/YouTube extraction is out of scope (docs/09).

Status key: done = implemented AND statically checked; "unbuilt" = never compiled (no Android SDK in authoring sandbox).

| ID | Task | Depends | Status | Acceptance |
|---|---|---|---|---|
| T01 | v1 offline player | - | done, unbuilt | Library, playback service, mini/full player |
| T02 | v2 UI redesign + rename to Dopavin | T01 | done, unbuilt | Design system, all screens |
| T03 | Phase 1: queue sheet, EQ/bass/loudness, sleep timer, volume, swipe-down, Settings | T02 | done, unbuilt | Screens/sheets reachable |
| T04 | Like button on Now Playing | T03 | done, unbuilt | Persisted heart toggle |
| T05 | Remove grey press rectangle (root-cause: default ripple) | T02 | done, unbuilt | clickableScale/selectable/toggleable use no indication |
| T06 | Phase 3 features: playback speed, pause-on-unplug toggle, keep-screen-on toggle, Liked tab | T03,T04 | done, unbuilt | Speed chips in Sound sheet; 2 new toggles in Settings; Library > Liked |
| T06b | LRCLIB lyrics: INTERNET permission, cache, LRC parser, lyrics sheet, Settings toggle | T03 | done, unbuilt | Lyrics pill in Now Playing; synced highlight + tap-to-seek |
| T07 | **First real build** (Gradle assemble) and fix compile errors | T01-T06 | BLOCKED: needs user to build and send error list | `assembleDebug` succeeds |
| T08 | Device check: press states, EQ on real device, sleep timer, speed | T07 | pending | Manual checklist |
| T09 | Unit tests (parseLrc, currentLineIndex, sort, grouping) | T07 | pending | JUnit passes |
| T11 | Downloads-first batch: crash fix, no local music, downloads engine + library, bottom sheets, Home/Now Playing relayout, Settings hub + pro options | T06 | done, unbuilt | See docs/07 "Downloads-first batch" |
| T12 | Device check of downloads: download, offline play, save to device, delete, Wi-Fi only, quality | T07,T11 | pending | Manual checklist |
| T13 | Liquid glass (vendored Backdrop + Capsule, Settings page, floating nav) | T11 | done, unbuilt | docs/07 "Liquid glass" |
| T14 | Extras: download limit, downloaded badge, backup, resume on reconnect | T11 | done, unbuilt | docs/07 "Extras" |
| T15 | UI polish: liquid tab bar, landscape rail, pull-to-refresh, haptics, Driving mode | T13 | done, unbuilt | docs/07 F-122 |
| T18 | Artist profile, Subscribe button, Library hub (F-133, F-134) | T17 | done, unbuilt | docs/07 F-133 |
| T17 | Subscriptions, big/small feed cards, word-synced lyrics (F-129..F-131) | T07 | done, unbuilt | docs/07 F-129 |
| T16 | 200 % font check, glass seek bar/dialogs | T07 | pending | - |
| T19 | Share song, Recap (F-190, F-191) | T17 | done, unbuilt | docs/07 2026-10-29 |
| T21 | Mobile quality, lyrics cache clear, lyrics card (F-192..F-194) | T19 | done, unbuilt | docs/07 2026-10-29 |
| T22 | Download all, Add all, haptics switch, share artist, Recap play (F-195..F-199) | T21 | done, unbuilt | docs/07 2026-10-29 |
| T23 | More menu, Play on, widget, lyric strip, Spotify import, autoplay freshness (F-200..F-205) | T22 | done, unbuilt | docs/07 2026-10-29 |
| T20 | Backlog B-03..B-22 (docs/01 "Backlog") | T07 | planned | one scope line per item before code |
| T10 | Candidates (unconfirmed): excluded folders, playlists, crossfade, lyrics from embedded tags | T07 | proposal only | Needs user confirmation |

## Assumptions
- Speed also changes pitch (ExoPlayer default PlaybackParameters(speed)); no separate pitch control.
- Liked songs are stored as MediaStore ids in SharedPreferences; ids can change if files are re-indexed.

## Liquid glass + extras (2026-10-22)
Files: `ui/glass/Glass.kt`, `GlassSwitch.kt`, `ui/glass/backdrop/**` (vendored), `com/kyant/capsule/**` (vendored), `ui/screens/GlassSettingsPage.kt`, `MusicApp.kt` (ambient/content/overlay backdrops), `BottomNav.kt`, `MiniPlayer.kt`, `BottomSheet.kt`, `Controls.kt`, `SettingRows.kt`, `SearchScreen.kt`, `NowPlayingScreen.kt`, `data/{SettingsStore,DownloadStore,DownloadCenter,Backup,PlaylistStore}.kt`, `playback/PlaybackService.kt`, `MainActivity.kt`, `build.gradle` (Compose 1.7.5, Material3 1.3.1), `docs/licenses/*`.
Checks run: kotlinc parse, string/drawable/action cross-checks. NOT run: compile, device tests.
Known limits: glass falls back to solid surfaces below Android 12 / when animations are off / when disabled; in-screen glass samples the ambient colour backdrop (not the exact pixels behind it) to avoid self-sampling; trimming deletes the oldest downloads first (no "last played" tracking); import replaces settings and merges likes/playlists (existing playlist names are kept).
- T24 (unbuilt): F-207..F-210 Updates page + in-app download, lyric strip karaoke/tap, marquee fades, More-menu glass. Next: build and test on device.
