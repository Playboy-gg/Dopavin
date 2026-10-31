# 12 - Motion rules (iOS-style)

Status: numbers below are working values. The iOS figures come from general knowledge of UIKit behaviour
(push/pop about 0.35 s, underlying page moves about a third of the width, interactive pop finishes using the finger's
velocity), NOT from a quoted Apple spec: the supplied Apple file only says "use motion to clarify, honor Reduce Motion".
A web search for exact values returned only third-party transition examples. Tune on the device.

| Rule | Value in Dopavin |
|---|---|
| Page back (button or gesture) | Slides, never cross-fades. Full move 350 ms; after a gesture the remaining distance takes 140 + 210 x remaining ms |
| Page below | Parallax 30 % of width, dim 28 % fading to 0 |
| Swap on commit | The route change and the "progress = 0" reset happen in the same composition (`effBack`), so no frame shows the wrong page |
| Now Playing held | Moves at most 12 % of screen height, 3 % shrink |
| Now Playing released | Leaves fully (300 ms); bars stay hidden until it is gone |
| Mini player after player closes | Drops in from 24 dp above with spring (damping 0.5, stiffness 380) and fades in |
| Mini player swipe | Stretches 4.5 % toward the finger, 1.5 % thinner, spring back (damping 0.5) |
| Reduce Motion | No slide, no drop, no stretch |
| Draw phase only | Moving things use `graphicsLayer` lambdas, so no recomposition per frame |

## Where the numbers live (F-179)
- `ui/motion/MotionTokens.kt`: plain constants (durations, fractions, dp). `ui/motion/MotionMath.kt`: pure functions (lerp, pop duration, held offset, scale, corner, stretch, damping ratio, spring step). `ui/motion/MotionSpecs.kt`: Compose specs built from the tokens. Tests: `MotionMathTest` (11).
- Source blueprint supplied by the owner: `docs/13_ANIMATION_MATH_BLUEPRINT.md`. It says explicitly that it is Apple-inspired, not Apple's private system.
- Blueprint phase status: 1 (tokens, reduced motion) done for the gesture/transition work; the older fade/tween values elsewhere in the app still use `MotionSpec` (theme/Motion.kt) and are not migrated yet. 2 (mini player to full player as one coordinated transition with anchored drag and release velocity) NOT started: it replaces the current slide and needs device feedback. 3-5: not started.

## Phase 2 (F-180): one sheet value, one composition per page
- `sheet` (0 = Now Playing open, 1 = closed) is the only thing that moves the player: route change (tap, chevron) animates it with `openSpring` (damping 0.9, no overshoot) or a 300 ms slide; dragging the mini player up writes it 1:1 (finger = sheet edge) and on release opens or falls back by position (< 50 % open) and fling speed (900 px/s), carrying the finger velocity into the spring; the back gesture holds it at <= 12 %, release slides it fully down. The old `AnimatedVisibility` for the player is gone.
- Bars (nav + mini player) are faded by `sheet` (gone above 70 % closed), so they never sit on top of the player; the mini player drops in when the sheet is fully closed again after the player was open (not after a cancelled mini drag).
- Pages are a keyed list of layers (Under / Main / Leaving / Entering) instead of `AnimatedContent` + a separate underlay. After a back the page that was underneath becomes the current page WITHOUT being recomposed.
- Still not done: shared-element morph of the artwork between mini player and Now Playing; unit/UI tests of the drag (needs a device).

## Cover flight (F-182)
- `components/ArtworkFlight.kt`: the mini player cover and the Now Playing cover report their rectangles (mini only while the sheet is closed, player only while it is fully open). While the sheet is between 12 % (end of the held range) and closed, ONE cover is drawn at the root and moved/scaled between the two rectangles by `MotionMath.flightFraction(sheet)`; corner radius goes XLarge -> Medium, shadow 28 dp -> 0; the two real covers are hidden (alpha 0) meanwhile. At the start of the flight it sits exactly where the real cover is after the held shift (0.97 scale, 12 % down, pause shrink) so there is no jump.
- First open since app start has no player rectangle yet: nothing flies, the covers show as before; later opens fly.
- Not handled: the real cover mid-swipe (previous / next) when the sheet starts closing; rotation changes between recording and flight.

## Cover flight, rewritten (F-184)
- Owner: the cover only reached the right place after Now Playing was fully open, and the way down to the mini player was unclear. Wanted: when closing (back or swipe down) the cover moves slower than the player, in the finger's direction; opening is the exact reverse.
- Now: the flying cover starts at the real cover's resting place (incl. pause shrink) and reaches the mini cover LINEARLY with the sheet value (`flightFraction(sheet) = clamp01(sheet)`), from the very first pixel of movement (also during the held range). It covers only the distance to the mini player while the sheet covers the whole screen, so it always moves slower than the player (about 0.6-0.7x), in the same direction. No more hand-off at 12 %.
- The big cover's resting rectangle is read with `boundsInRoot` when the sheet settles at 0 (F-185; the host-relative way of F-184 broke the flight).

## Cover flight: first open (F-186)
Coordinate model (verified from Compose's `NodeCoordinator`/`NodeChain` source, not assumed):
- `toParentPosition` applies the coordinator's own graphicsLayer; a LayoutCoordinates therefore includes every layer of the coordinators it sits inside, whichever side of a `graphicsLayer` the `onGloballyPositioned` is written on (a non-layout modifier is attached to the next layout modifier's coordinator after it, or the inner coordinator). So `boundsInRoot()` of the cover INCLUDES the sheet's translation/scale (and Now Playing's own dismiss-drag layer), but not the cover's own later layers (pause shrink, swipe).
- The host box (`flightPlayerHost`, written after the sheet's `graphicsLayer`) sits inside the sheet layer; `host.localPositionOf(cover)` is the cover's place without the sheet transform, valid at any sheet value, including the first frame after the player is composed. The host fills the root at (0,0).
- The cover's place depends on its siblings (header, text, controls, orientation), so it cannot be derived analytically; it is measured from the real layout. Nothing is precomposed: the player is composed at sheet = 1 (offscreen) for the first open anyway; its first layout provides the rectangle.
- Two independent readings: host-relative on every layout (any sheet value), and `boundsInRoot()` when the sheet settles at 0 (exact there). A rectangle that cannot be a cover on this screen is rejected (no flight instead of a flight to nowhere). Logcat tag `ArtworkFlight` prints every accepted/rejected rectangle.
- Reduce Motion: `ArtworkFlight.enabled = false`: nothing flies, no cover is hidden (also during the mini-player drag).

## Swipe-down inside Now Playing (F-187)
- Before: `NowPlayingScreen` kept its own `dragY`/`shownDrag` and translated itself; the root `sheet` stayed at 0, so the cover flight (which reads `sheet`) never ran during the drag, and after the threshold `actions.back()` made the route effect start `sheet` 0 -> 1 on top of the leftover translation.
- Now: the vertical drag calls `onDragSheet(dy)`; the root turns it into `sheet += dy / screenHeight` (clamped 0..1). Release: if `sheet * screenHeight > SheetDismissDp (120 dp)` the sheet finishes to 1 from where the finger let go (duration by remaining distance) and only then `viewModel.back()`; otherwise it springs back to 0. Pointer cancel springs back. One release job at a time; a new drag cancels it (and with it the pending route change). Predictive Back is unchanged and writes the same value.
- No velocity handling (distance only, as before).

## Bars are never recreated; cover landing spring (F-188)
- Owner: after Now Playing closed the bottom tab bar was "created" again, and the cover arriving in the mini player needed a spring, not a rebuild.
- Cause: `bottomBar`/`miniVisible`/NavRail were gated on `!playerOpen`, so opening the player REMOVED the bottom nav + mini player from composition and closing rebuilt them (state lost, layout + glass setup repeated, visible pop).
- Now: they stay composed whenever the base screen is browsing. While the player covers them they are not placed (`Modifier.placedWhen`: no drawing, no hit-testing) when `playerOpen && sheet < 0.7`; they fade with `sheet` (alpha 0 below 0.7 closed, 1 when closed). Mini player drag-up works because the bars stay placed while the route is not yet "open".
- Landing: when the sheet is fully closed after the player was open, `ArtworkFlight.land` (scale of the mini cover) starts at 1 and is kicked with `CoverLandKick` through `landSpring` (damping 0.35, stiffness 500): about +5-6 % overshoot, rings back. No pop at the start, because it starts at the flight's end size. The earlier whole-card drop of the mini player was removed (it moved the card away from the rectangle the cover lands on).

## Settings navigation (F-189)
A settings category is a pushed route above the hub, so it uses the same layered page host and predictive back as every other page. No page may rely on its own registered back click: the root intercepts it.
