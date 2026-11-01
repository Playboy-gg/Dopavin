# Third-party notices

Dopavin is distributed under the **GNU General Public License v3.0** (see [`LICENSE`](LICENSE)). This file lists every third-party component used by the app, its licence and what it is used for. Full licence texts are in [`docs/licenses/`](docs/licenses/).

Versions are pinned in [`app/build.gradle`](app/build.gradle). If you add, remove or update a dependency, update this file in the same change.

## Runtime dependencies

| Component | Version | Licence | Copyright | Used for |
|---|---|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) (stdlib) | 1.9.24 | Apache-2.0 | JetBrains s.r.o. | Language runtime |
| [AndroidX Core KTX](https://github.com/androidx/androidx) | 1.13.1 | Apache-2.0 | The Android Open Source Project | Android platform helpers |
| [AndroidX Activity Compose](https://github.com/androidx/androidx) | 1.9.0 | Apache-2.0 | The Android Open Source Project | Compose activity host |
| [AndroidX Lifecycle](https://github.com/androidx/androidx) — `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | 2.8.3 | Apache-2.0 | The Android Open Source Project | ViewModel and lifecycle-aware state |
| [Jetpack Compose UI](https://github.com/androidx/androidx) — `ui`, `foundation` | 1.7.5 | Apache-2.0 | The Android Open Source Project | Declarative UI, layout, drawing |
| [Material 3](https://github.com/androidx/androidx) | 1.3.1 | Apache-2.0 | The Android Open Source Project | Component and accessibility foundations |
| [AndroidX Media3](https://github.com/androidx/media) — `media3-exoplayer`, `media3-datasource`, `media3-session` | 1.3.1 | Apache-2.0 | The Android Open Source Project | Playback, media session, Android Auto library |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.8.1 | Apache-2.0 | JetBrains s.r.o. | Structured concurrency |
| [Guava](https://github.com/google/guava) (transitive via Media3) | — | Apache-2.0 | The Guava Authors | `ListenableFuture` bridge used by Media3 |
| [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | 0.26.5 | **GPL-3.0** | Team NewPipe and contributors | YouTube Music search, streams and metadata |
| [JUnit 4](https://github.com/junit-team/junit4) — test only | 4.13.2 | EPL-1.0 | JUnit contributors | JVM unit tests |

AndroidX, Compose, Media3 and Material 3 pull in further AndroidX artifacts transitively; all are Apache-2.0. Guava is pulled in by Media3 and is Apache-2.0.

## Vendored source code

These components are compiled from source in this repository rather than consumed as binaries. Each vendored file keeps its original copyright and licence header; modifications are noted below and in [`docs/licenses/NOTICE.md`](docs/licenses/NOTICE.md).

| Component | Upstream | Licence | Location | Modifications |
|---|---|---|---|---|
| Backdrop 2.0.0 | <https://github.com/Kyant0/backdrop> | Apache-2.0 | `app/src/main/java/com/dopavin/ui/glass/backdrop` | Package renamed to `com.dopavin.ui.glass.backdrop`; KMP `expect`/`actual` merged into the Android source set; `backdropScale` added for cheaper rendering |
| Capsule | <https://github.com/Kyant0/Capsule> | Apache-2.0 | `app/src/main/java/com/kyant/capsule` (original package) and `app/src/main/java/com/dopavin/ui/glass/shapes` (renamed copy) | Original copy unchanged; the copy used by the liquid tabs is package-renamed to `com.dopavin.ui.glass.shapes` |
| Android Liquid Glass (kmp branch) | <https://github.com/Kyant0/AndroidLiquidGlass> | Apache-2.0 | `app/src/main/java/com/dopavin/ui/glass/interaction` | `DampedDragAnimation`, `DragGestureInspector` and `InteractiveHighlight` ported from the catalog app and renamed |
| FloatingTabBar 1.0.1 | <https://github.com/elyesmansour/compose-floating-tab-bar> | Apache-2.0 | `app/src/main/java/com/dopavin/ui/glass/floatingtabbar` | Adapted for Dopavin navigation and its pinned Compose version; the bar wrapper and glass effect are Dopavin's own code |

Copyright 2025 Kyant0 for Backdrop, Capsule and Android Liquid Glass. Copyright 2025 Elyes Mansour for FloatingTabBar. All four are licensed under the Apache License, Version 2.0.

## Fonts and icons

| Asset | Licence | Copyright | Source |
|---|---|---|---|
| Plus Jakarta Sans | SIL Open Font License 1.1 | Tokotype | <https://github.com/tokotype/PlusJakartaSans> |
| Material Icons | Apache-2.0 | Google LLC | <https://fonts.google.com/icons> |
| Lucide icons | ISC | Lucide contributors | <https://github.com/lucide-icons/lucide> |
| Dopavin name, wordmark and launcher icon | © 2026 Harish | Harish | Original artwork, not third-party |

The font files are in `app/src/main/res/font/`. Icons are generated as vector drawables in `app/src/main/res/drawable/`; their paths are not hand-typed.

## Services queried at runtime

Dopavin contains **no code** from these services. They are contacted only when the user searches, downloads, opens lyrics or enables the matching integration. Their terms and privacy policies apply to those requests.

- **YouTube Music** — search, streams and metadata through NewPipe Extractor. YouTube Music is a service of Google LLC. Dopavin is not affiliated with Google or YouTube.
- **Lyrics providers** — LRCLIB (<https://lrclib.net>), Better Lyrics, Better Lyrics Portato, LyricsPlus, Unison, KuGou and lrc.red / BiniLyrics. Lyrics requests send the track title, artist, album and duration only.
- **ListenBrainz** (<https://listenbrainz.org>) — optional scrobbling, only after the user connects a token.
- **Subsonic-compatible servers** — optional; the user supplies the server address and credentials.
- **GitHub** — optional update check against the public Releases API.

## Full licence texts

| Licence | File |
|---|---|
| GNU General Public License v3.0 | [`LICENSE`](LICENSE) |
| Apache License 2.0 | [`docs/licenses/Backdrop-LICENSE.txt`](docs/licenses/Backdrop-LICENSE.txt) (identical text applies to all Apache-2.0 components) |
| Apache License 2.0 — Material Icons | [`docs/licenses/MaterialIcons-Apache2.txt`](docs/licenses/MaterialIcons-Apache2.txt) |
| SIL Open Font License 1.1 — Plus Jakarta Sans | [`docs/licenses/PlusJakartaSans-OFL.txt`](docs/licenses/PlusJakartaSans-OFL.txt) |
| ISC — Lucide | [`docs/licenses/lucide-ISC.txt`](docs/licenses/lucide-ISC.txt) |
| Eclipse Public License 1.0 — JUnit | [`docs/licenses/EPL-1.0.txt`](docs/licenses/EPL-1.0.txt) |

## GPL-3.0 note

Dopavin includes NewPipe Extractor, which is licensed under GPL-3.0. As a result, the combined work is distributed under GPL-3.0. The complete corresponding source is this repository. NewPipe Extractor's source is available at its upstream repository and is resolved from JitPack at the version pinned in `app/build.gradle`.

If you distribute a modified version of Dopavin, you must provide the complete corresponding source under GPL-3.0 and keep the copyright and licence notices intact. The Dopavin name and logo are not licensed for use in a way that suggests the modified build is the official Dopavin release.
