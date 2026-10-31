# AGENTS.md — Dopavin

Instructions for any AI agent or developer working in this repository. Read this file fully, then the documents in section 2, before changing code.

## 1. Mission

A downloads-first Android music player (songs are downloaded into the app; local-file scanning was removed by owner request 2026-10-20): reliable background playback, a premium, artwork-led UI (bottom navigation, floating mini player, artwork-tinted Now Playing), no network access except one optional feature: synced lyrics from LRCLIB (settings toggle, on by default, owner-approved 2026-10-18). Scope is fixed by `docs/01_DISCOVERY_AND_SCOPE.md`. Features marked LATER or OUT there are not built unless the owner (Harish) asks for them. When the owner asks, update the docs first, then the code.

## 2. Document map (read in this order)

| # | File | What it settles |
|---|---|---|
| 1 | `docs/01_DISCOVERY_AND_SCOPE.md` | What is IN / LATER / OUT and why, edge cases, success criteria, risks |
| 2 | `docs/05_SCREEN_BLUEPRINT.md` | Screens, flows, states, copy, acceptance criteria |
| 3 | `docs/03_DESIGN_SYSTEM.md` | Colour, type, spacing, shape, motion tokens and components |
| 4 | `docs/04_ICONOGRAPHY.md` | Icon family, inventory, accessibility labels |
| 5 | `docs/06_ARCHITECTURE.md` | Files, data flow, threading, persistence, error handling, versions |
| 6 | `docs/02_DESIGN_DIRECTION.md` | Research log, design direction, decision log |
| 7 | `docs/07_IMPLEMENTATION_LOG.md` | Changed files, decisions, limitations (update after every meaningful change) |
| 8 | `docs/08_UI_UX_AUDIT.md` | Verification results: Pass / Fail / Not run |
| 10 | `docs/11_APPLE_HIG_AUDIT.md` | Findings against Apple's design principles (open problems, fix order) |
| 11 | `docs/14_RELEASING.md` | How a version is released and what users see as "what's new" |
| 9 | `docs/10_UI_RULES.md` | The owner's binding UI rules (back button, titles, glass, predictive back, tint). Read before ANY UI change |

If code and docs disagree, fix whichever is wrong in the same change and record it in the implementation log.

## 3. Build constraints (CodeAssist-first)

The owner builds on-device with CodeAssist. Keep the project buildable there.

- Kotlin `1.9.24` with `kotlinCompilerExtensionVersion '1.5.14'`. These two are a matched pair; the compiler refuses to run on any other Kotlin version. Do NOT move to Kotlin 2.x (it needs the separate Compose compiler Gradle plugin, which CodeAssist cannot resolve).
- Groovy DSL `.gradle` files only. No `.kts`. No version catalogs. No BOM. Every dependency has an explicit version.
- No KSP / kapt, no Hilt, no Room, no Coil, no Navigation-Compose. Each of these was deliberately left out (see architecture doc, section 9).
- `INTERNET` permission exists ONLY for LRCLIB lyrics (`data/LyricsRepository.kt`, plain `HttpURLConnection`, no extra dependency). The only data sent is track title, artist, album and duration. No other network use; no YouTube/stream extraction.
- Do not add a dependency or change `compileSdk`, AGP or Gradle settings without the owner's approval; record any approved change in the implementation log.
- All version numbers in `docs/06_ARCHITECTURE.md` are provisional until a successful CodeAssist build has been reported by the owner.

## 4. Code rules

- App name: Dopavin (dopamine + veena). Package / applicationId: `com.dopavin`. Single `app` module.
- UI state flows down from `MusicViewModel` as immutable state; events flow up as function parameters. Composables never talk to MediaStore or the player directly.
- Playback lives in `PlaybackService` (Media3 `MediaSessionService`). The UI only holds a `MediaController` through `PlayerConnection`.
- Every colour, text style, spacing, radius and duration comes from the tokens in `ui/theme`. Prefer tokens over literals in screens; one-off decorative sizes (hero art, browse tiles) and `Color.White`/`Color.Black` scrims over artwork are allowed and listed in `docs/07_IMPLEMENTATION_LOG.md`.
- Fonts: Plus Jakarta Sans in `res/font` (OFL). Icons: Material Icons (Apache 2.0), generated as vector drawables; licences in `docs/licenses/`. Do not hand-type icon paths.
- Every icon-only control has a content description from `strings.xml`. Every user-visible string lives in `strings.xml`.
- Minimum touch target 48dp. Text sizes are `sp`. Layouts must survive 200% font scale (scroll, never clip).
- Never block the main thread: MediaStore queries, artwork decoding and SharedPreferences writes run off it.
- Prefer simple code. No abstraction layer without a second user of it.

## 5. Working process

1. Check the change against the scope table. If the feature is not IN, stop and ask.
2. Change tokens/components before screens.
3. Test each screen against its acceptance criteria in `docs/05_SCREEN_BLUEPRINT.md`.
4. Update `docs/07_IMPLEMENTATION_LOG.md` (files, decisions, known limitations).
5. Update `docs/08_UI_UX_AUDIT.md`. Never mark a check Pass unless it was actually run or directly inspected. "Looks plausible" is Not run.

## 6. Delivery and communication

- The owner writes in Hinglish and prefers direct, informal replies. Reply in the language the owner uses.
- Deliver only what was asked for: the project archive plus a short message. Name the archive exactly after the project (`Dopavin.zip`) with no suffixes like `_Fixed` or `_Updated`.
- Prefer forward-looking guidance (what to do next) over retelling what was done.

## 7. Definition of done

Requirements trace to screens and states; the design system is applied consistently; core flows work; accessibility was considered and checked as far as the tooling allows; the audit file states honestly what passed, failed and was not run.
