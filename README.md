# Dopavin

**Music, your way.** Dopavin is a downloads-first Android music player built with Kotlin and Jetpack Compose. It searches YouTube Music, plays and downloads songs inside the app, and wraps everything in an artwork-led interface with a liquid-glass look.

- Package: `com.dopavin`
- Platform: Android 8.0 (API 26) and newer
- Status: under active development. Features are added continuously; see [`CURRENT_STATE.md`](CURRENT_STATE.md) for the verified state of the latest build.

> Dopavin is a personal project. It is not affiliated with, endorsed by or sponsored by YouTube, Google, LRCLIB or any lyrics provider. Streaming and downloading content may be subject to the terms of the services involved; you are responsible for how you use the app.

## Features

**Playback**
- Background playback through a Media3 `MediaSessionService`, with notification, lock-screen and headset-button controls
- Queue with drag-to-reorder, swipe-to-remove with undo, and "You / Auto" provenance chips
- Shuffle, repeat, playback speed and pitch, skip silence, sleep timer, equalizer with bass boost and virtualizer
- Smart autoplay that appends similar songs when the queue is about to end
- Last queue and position are restored on launch

**Discover**
- YouTube Music search with filter chips, "Did you mean", recent searches, artist, album and song results, and "Show more results" paging
- Home feed: continue listening, subscriptions, trending, radio, genres and moods (Chill, Happy, Energetic, Focus, Reflective, Romantic)
- Artist pages with subscribe (stored on the device only), shuffle and the artist's songs
- Song radio and mood queues driven by a deterministic, unit-tested scorer

**Library and downloads**
- Download any song for offline playback; downloaded songs play from the local file
- Playlists, favourites, recently played, multi-select actions, duplicate handling
- Wi-Fi-only downloads, download quality, storage dashboard, "Save to device"
- Backup and restore of favourites, playlists and settings to a file you choose

**Lyrics**
- Synced lyrics from several providers (LRCLIB, Better Lyrics, Portato, LyricsPlus, Unison, KuGou) with a fallback chain
- Word-by-word (karaoke) highlighting when the source provides syllable timing

**Interface**
- Liquid-glass tab bar, mini player, buttons and menus, with a flat fallback for older or slower devices
- Artwork-tinted Now Playing, shared cover animation between mini player and full player, predictive back for pages and the player
- Light and dark themes, Reduce Motion support, driving mode

## Privacy

- No account is needed and nothing is collected: no analytics, no advertising. Optional: you can connect ListenBrainz (scrobbling), your own Subsonic-compatible server, and sign in to YouTube Music to import your liked songs and playlists. Those credentials stay on the phone, are sent only to the service they belong to, and are never part of a backup file.
- Subscriptions, history, likes, playlists and settings stay on the device.
- Network access is used for YouTube Music search and streams, downloads, artwork and lyrics lookups. Lyrics requests send the track title, artist, album and duration.
- The app does not request storage permissions. Downloads live in app-private storage; "Save to device" uses the system file picker.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose (UI 1.7.5, Material 3 1.3.1), Compose compiler extension 1.5.14 |
| Playback | AndroidX Media3 (ExoPlayer, DataSource, Session) 1.3.1 |
| Online sources | [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) v0.26.5 |
| Concurrency | Kotlin Coroutines 1.8.1 |
| Build | Android Gradle Plugin 8.5.2, Groovy DSL, single `app` module |
| Min / target SDK | 26 / 34 |

The project is deliberately dependency-light: no Hilt, Room, KSP/kapt, Coil or Navigation-Compose. Rationale is in [`docs/06_ARCHITECTURE.md`](docs/06_ARCHITECTURE.md).

## Building

Requirements: JDK 17, Android SDK platform 34 and Gradle 8.x (the repository contains `gradle/wrapper/gradle-wrapper.properties` but not the `gradlew` script).

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

The project is developed and built on-device with an Android IDE. Keep the Kotlin / Compose compiler pair matched (1.9.24 / 1.5.14); moving to Kotlin 2.x requires the Compose compiler Gradle plugin and is not supported by the current setup.

## Project structure

```
app/src/main/java/com/dopavin/
  data/        stores, repositories, lyrics providers, recommendation logic
  playback/    MediaSessionService, player connection, download service
  ui/          MusicApp (root), MusicViewModel, screens, components, motion, glass
app/src/test/  unit tests (scorer, lyrics, artwork, motion maths and more)
docs/          scope, design system, architecture, motion rules, audits
```

## Documentation

| Document | Purpose |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Rules for developers and AI agents working in this repository |
| [`CURRENT_STATE.md`](CURRENT_STATE.md) | Verified progress, blockers and the next task |
| [`docs/01_DISCOVERY_AND_SCOPE.md`](docs/01_DISCOVERY_AND_SCOPE.md) | Feature scope and decisions |
| [`docs/03_DESIGN_SYSTEM.md`](docs/03_DESIGN_SYSTEM.md) | Colour, type, spacing and components |
| [`docs/06_ARCHITECTURE.md`](docs/06_ARCHITECTURE.md) | Architecture and data flow |
| [`docs/10_UI_RULES.md`](docs/10_UI_RULES.md) | Binding UI rules |
| [`docs/12_MOTION_RULES.md`](docs/12_MOTION_RULES.md) | Motion tokens and animation rules |
| [`docs/07_IMPLEMENTATION_LOG.md`](docs/07_IMPLEMENTATION_LOG.md) | Change log and known limitations |

## Third-party software and credits

- [Backdrop](https://github.com/Kyant0/backdrop) and [Capsule](https://github.com/Kyant0/Capsule) by Kyant0 (Apache-2.0), vendored as source
- FloatingTabBar by Elyes Mansour (Apache-2.0), vendored and modified
- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0)
- Plus Jakarta Sans (SIL OFL 1.1), Material Icons (Apache-2.0), Lucide icons (ISC)

Full notices and licence texts are in [`docs/licenses/`](docs/licenses/).

## Releases and updates

Dopavin checks the latest [GitHub release](https://github.com/playboy-gg/Dopavin/releases) for a newer version (Settings > Updates), shows its notes and can download and install it inside the app after you confirm. See [`docs/14_RELEASING.md`](docs/14_RELEASING.md) for what to upload with each version, and [`CHANGELOG.md`](CHANGELOG.md) for what changed.

## Contributing

Issues and suggestions are welcome. Before changing code, read [`AGENTS.md`](AGENTS.md) and [`docs/10_UI_RULES.md`](docs/10_UI_RULES.md), and record meaningful changes in the implementation log.
