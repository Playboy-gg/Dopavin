# 09 — NewPipeExtractor audit (2026-10-18)

## 1. Actual Dopavin state (verified in repo)
namespace/applicationId `com.dopavin`; compileSdk/targetSdk 34, minSdk 26; Java/Kotlin JVM 17; Kotlin 1.9.24, Compose compiler ext 1.5.14, Compose 1.6.8, Material3 1.2.1; Media3 exoplayer + session 1.3.1 (no Cast); coroutines 1.8.1. No Room, no OkHttp, no INTERNET permission, no tests, no lyrics, no online code. Playback: `PlaybackService` (MediaSessionService + ExoPlayer) driven by `PlayerConnection`; effects/sleep in `PlaybackRuntime`. Data: `MusicRepository` (MediaStore), `SettingsStore` (SharedPreferences).

## 2. NewPipeExtractor findings
Verified from the project README / releases page (fetched 2026-10-18):
- Library for "extracting data from streaming sites"; services: YouTube, SoundCloud, media.ccc.de, PeerTube, Bandcamp.
- License GPL-3.0-or-later. Distributed via JitPack.
- Latest release seen: v0.26.5. Release notes show repeated YouTube-specific fixes (v0.26.3 SABR workaround, v0.26.4 playlist continuation fix, v0.26.1/0.26.2 lockup-view-model and duration fixes) => YouTube support breaks and is patched continuously.
- Android: minSdk < 33 requires core-library desugaring with `desugar_jdk_libs_nio`; R8 keep rules needed for `org.mozilla.javascript` and `org.mozilla.classfile.ClassFileWriter`. Dopavin (minSdk 26) would need both, plus an OkHttp-based `Downloader` and JitPack repo.
- NOT verified (not run here): exact Java requirement of the pinned version, dependency conflicts. No Android SDK/Gradle in the sandbox.

The library has no official-API mode: it parses YouTube's internal (undocumented) endpoints and pages.

## 3. Policy boundary (verified from YouTube API Services Developer Policies)
Clients must not: scrape YouTube (III.E.6), use undocumented APIs without permission (III.I.14), use technology other than YouTube API Services to retrieve API data, cache/offline copies of audiovisual content (III.E.1), play content from a background player (III.I.9), separate audio from video (III.I.7-8).
=> Every YouTube feature NewPipeExtractor provides (search, channel, playlist metadata as well as streams) goes through unofficial scraping, so it falls under these rules, not only stream URLs. Dopavin's background audio-only player is itself in conflict with the official YouTube player rules.

## 4. Feasibility matrix
| Feature | Via NewPipeExtractor | Verdict |
|---|---|---|
| YouTube stream/audio URL | Yes (technically) | Not implemented: unsupported extraction, ToS violation |
| Background/audio-only YouTube playback | Yes (technically) | Not implemented: forbidden by developer policy |
| YouTube search / channel / playlist metadata | Yes (technically) | Not implemented: scraping of undocumented endpoints |
| SoundCloud / Bandcamp | Yes (technically) | Not implemented: same scraping model; those services' terms not cleared |
| PeerTube / media.ccc.de | Yes | Possible but not music-relevant; GPL cost not worth it |
| Official YouTube search/playlist metadata | No | Use YouTube Data API v3 (key + quota) — metadata only |
| Official YouTube playback | No | IFrame Player API in a WebView (foreground, video visible, no background audio) |
| Synced lyrics | No | LRCLIB public API — independent of any music provider |
| Legal streaming of full tracks | No | Jamendo / Audius / Internet Archive / Radio Browser / self-hosted Subsonic-Navidrome |

## 5. License
GPL-3.0-or-later: linking it into Dopavin makes the distributed app a covered work -> app must be released under GPL-3 with source, notices and install info. Incompatible with closed-source distribution and Play Store closed-source plans. Not legal advice.

## 6. Risks
Frequent upstream breakage; IP/rate blocking; Play Store policy risk; GPL; desugaring + R8 rules; no tests to protect playback.

## 7. Recommendation
Do NOT integrate NewPipeExtractor. No verified, appropriate feature requires it. Official YouTube functionality would use Data API v3 + IFrame player, not this library. For online music inside Dopavin's existing Media3 service, a provider with a real streaming API is required (Jamendo or Audius), plus LRCLIB lyrics.

## 8. If proceeding (files that change)
Scope docs 01/06; `AndroidManifest.xml` (INTERNET, ACCESS_NETWORK_STATE); `app/build.gradle` (OkHttp or HttpURLConnection only); new `online/` package: provider interface, `JamendoSource`, models, error mapping; `LyricsRepository` (LRCLIB) + LRC parser; `MusicViewModel` (Explore state, cancel/debounce); `UiModels` actions; `ExploreScreen`, lyrics view in `NowPlayingScreen`; `PlaybackService` / `PlayerConnection` accept http MediaItems (ExoPlayer already plays http). Tests: LRC parser, mapping, error mapping, search race/cancel (JUnit, no live network). Not built until a provider is chosen.
