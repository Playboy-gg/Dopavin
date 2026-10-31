# 03 — Design System (v2)

Source of truth in code: `ui/theme/Color.kt`, `Type.kt`, `Dimens.kt`, `Motion.kt`, `Theme.kt`. Contrast ratios below were **calculated** with a script (WCAG relative luminance), not measured on a display.

## 1. Colour roles

| Role | Dark | Light | Use |
|---|---|---|---|
| canvas | #0B0B10 | #FAF7F5 | Screen background |
| surface | #14141B | #FFFFFF | Bottom nav |
| surfaceRaised | #1C1C25 | #F2EEEB | Chips (idle), quick tiles, hero fallback |
| surfaceHigh | #262632 | #E9E4E0 | Search field, tonal buttons, skeleton, mini-player base |
| textPrimary | #F6F4F1 | #17151B | Titles, body |
| textSecondary | #A7A5B3 | #5E5B69 | Subtitles, times |
| divider | #2A2A36 | #E2DCD7 | Hairlines |
| controlOutline | #7C7A8C | #85828F | Non-text outlines |
| accent | #FF6A55 | #C7203F | Selected states, current title, links |
| accentStart → accentEnd | #FF8A4C → #FF3D81 | #CC4108 → #C7203F | Brand gradient |
| onAccent | #1A0A10 | #FFFFFF | Text/icons on accent and gradient |
| error | #FF6B6B | #B3261E | Errors |
| focusRing | #FFB199 | #C7203F | Keyboard focus |

Artwork tint: computed per album (hue-binned, saturation 0.35–0.8, value 0.5–0.85), then mixed into canvas (Now Playing 62% dark / 45% light, headers 70% / 45%, mini player 32% / 28%). Text on tinted areas uses the normal text colours; this is **not yet checked on real artwork** (audit UX-11).

## 2. Contrast (calculated)

| Pair | Dark | Light | Target |
|---|---|---|---|
| textPrimary / canvas | 17.88 | 16.98 | 4.5 |
| textPrimary / surfaceRaised | 15.40 | 15.70 | 4.5 |
| textSecondary / canvas | 8.11 | 6.20 | 4.5 |
| textSecondary / surfaceRaised | 6.98 | 5.73 | 4.5 |
| textSecondary / surfaceHigh | 6.17 | 5.24 | 4.5 |
| accent / canvas | 6.96 | 5.31 | 4.5 |
| accent / surfaceRaised | 5.99 | 4.91 | 4.5 |
| onAccent / accent | 6.80 | 5.67 | 4.5 |
| onAccent / accentStart | 8.21 | 4.85 | 4.5 |
| onAccent / accentEnd | 5.70 | 5.67 | 4.5 |
| controlOutline / canvas | 4.69 | 3.52 | 3.0 (non-text) |
| controlOutline / surfaceRaised | 4.04 | 3.26 | 3.0 |
| error / canvas | 7.08 | 6.13 | 4.5 |

## 3. Type scale (Plus Jakarta Sans)

| Style | Size / line | Weight | Use |
|---|---|---|---|
| display | 32 / 40 | ExtraBold | Screen titles, greeting |
| headline | 24 / 32 | Bold | Track title, collection title, empty states |
| title | 20 / 28 | Bold | Section headers |
| bodyStrong | 16 / 22 | SemiBold | Row titles |
| body | 16 / 24 | Regular | Artist on player, body copy |
| bodySmall | 14 / 20 | Regular | Subtitles |
| label | 14 / 20 | SemiBold | Buttons, chips, menu |
| caption | 12 / 16 | Medium | Nav labels, overlines, alphabet |
| numeric | 13 / 18 | Medium, tnum | Times, track numbers |

## 4. Spacing, size, shape

Spacing 4 / 8 / 12 / 16 / 24 / 32; screen edge 20. Touch target 48. Row 68, artwork in rows 52, mini player 64 (+4 margin), bottom nav 64, search field 52, chips 40, play button 76 (mini 44, collection 56). Radii: 8 (row art), 14 (cards), 20 (mini player, header art), 28 (hero, Now Playing art), full for pills.

## 5. Motion

| Token | ms | Use |
|---|---|---|
| short | 120 | Colour/icon swaps |
| medium | 240 | Screen fades, slide of Now Playing, nav pill |
| long | 400 | Artwork cross-fade on track change |
| ambient | 700 | Tint colour drift |
| springs | — | Press scale (0.96, 0.88 for icon buttons), artwork breathing (low stiffness, medium bounce) |
| loops | — | Equalizer (430–610 ms), skeleton pulse (800 ms) — off with reduce-motion |

Reduce motion = system animator scale 0 → all durations 0, loops static, springs snapped.

## 6. Components

BottomNav · MiniPlayer · SongRow (artwork or track-number layout) · AlbumCard · QuickTile · ArtistAvatarCard · ArtistRow · SectionHeader · SelectChip · GradientButton · TonalButton · IconAction · ToggleIconAction (colour + dot + spoken state) · PlayPauseButton (gradient, cross-fading glyph, buffering ring) · SeekBar (canvas, gradient progress, thickens on drag, adjustable semantics) · AlphabetScroller · EqualizerBars · ArtworkImage (gradient placeholder from a seed) · EmptyState · LoadingState (skeleton).

## 7. Rules

- Selected/current is never colour alone: filled icon + pill (nav), dot (toggles), equalizer + spoken "Now playing" (rows).
- Every icon-only control has a description; decorative art has none.
- Layouts scroll rather than clip at 200% font.

---

## Addendum (2026-10-20): action sheet, settings cards, pure black

- **Action sheet** (`components/ActionSheet.kt`): `BottomSheet` + header + 56 dp rows (icon 24 dp, `bodyStrong`); destructive rows use `colors.error`. Height = `0.2 + 0.085 × rows`, clamped 0.36–0.9 of the screen; rows scroll. One host (`SheetHost`) at the app root, reached through `LocalSheetHost`.
- **Settings hub card**: `radiusLarge`, `surfaceRaised`, 44 dp tonal circle (accent @ 14 %) with a 24 dp icon, title `bodyStrong`, summary `bodySmall`.
- **Player quick actions**: icon over `caption` label, equal weight, `radiusMedium` ripple area, min 48 dp.
- **Pure black** (setting): in dark mode `canvas = #000000`, `surface = #050507`; other tokens unchanged.
