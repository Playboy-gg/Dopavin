# Kotlin Jetpack Compose Animation Mathematics
## Engineering Implementation Guide

**Status:** Implementation blueprint  
**Target:** Native Android, Kotlin, Jetpack Compose  
**Scope:** Animation mathematics, motion architecture, gesture physics, transitions, custom deformation, Liquid Glass integration, accessibility, performance, and testing.

---

## 1. Goals and non-goals

### Goals
- Define a consistent, reusable motion system rather than scattered animation constants.
- Use Compose's built-in animation APIs wherever they fit.
- Implement custom spring simulations only where gesture-driven physical motion requires them.
- Make animations interruptible and responsive to gestures.
- Separate UI state, motion state, and rendering.
- Support reduced motion, light/dark themes, and low-end Android devices.
- Provide a safe foundation for glass-like visual effects without making the entire UI depend on shaders.

### Non-goals
- This document does not claim to reproduce Apple's private implementation or exact tuning.
- No single curve or spring is correct for every interaction.
- Liquid Glass is not equivalent to `Modifier.blur()` or a translucent background; convincing refraction requires an appropriate rendering approach and may be expensive.
- Code samples are reference implementations. Validate imports and API availability against the project's Compose BOM and Kotlin version before integrating.

---

## 2. Motion-system architecture

Use four layers:

1. **Design tokens** — named durations, curves, spring specifications, motion distances.
2. **Motion primitives** — reusable animation specs and helpers.
3. **Interaction/state layer** — expanded/collapsed, selected/unselected, drag/settle states.
4. **Rendering layer** — Compose layout, graphics layers, Canvas, and optional runtime shaders.

Suggested package structure:

```text
ui/motion/
  MotionTokens.kt
  MotionSpecs.kt
  MotionMath.kt
  SpringDragState.kt
  MotionPreferences.kt
ui/components/
  ExpandablePlayer.kt
  SpringSheet.kt
  GlassSurface.kt
ui/graphics/
  GlassShader.kt
```

Rules:
- State describes *what* the UI should be; animation describes *how* it gets there.
- Do not put business logic inside animation lambdas.
- Keep animation values in pixels only where APIs require pixels; use `Dp` for layout design values.
- Convert units at boundaries. Never mix dp, px, normalized progress, and seconds in one variable.
- Keep the glass renderer optional. Every screen must have a non-shader fallback.

---

## 3. Mathematical foundations

### 3.1 Normalized time

For a time-based animation of duration `T`, normalized time is:

\[
u = clamp((t - t_0)/T, 0, 1)
\]

where `t0` is the start time. The animation curve maps `u` to progress `p(u)`.

### 3.2 Linear interpolation (lerp)

\[
lerp(a,b,p) = a + (b-a)p
\]

For example, if a mini-player height changes from 72 dp to 680 dp and `p = 0.5`, the interpolated height is 376 dp.

Kotlin helper:

```kotlin
fun lerp(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}
```

For Compose layout, prefer `animateDpAsState`, `animateFloatAsState`, `updateTransition`, or `Animatable` instead of manually updating state every frame.

### 3.3 Velocity and acceleration

\[
v = dx/dt,\qquad a = dv/dt = d^2x/dt^2
\]

Position is measured in px or dp; velocity must use the matching unit per second. Gesture APIs usually report px/s. A physics simulation that expects px/s must not receive dp/s.

### 3.4 Derivatives and perceived smoothness

- Position continuity (C0): no visible position jump.
- Velocity continuity (C1): no sudden speed jump.
- Acceleration continuity (C2): no sudden acceleration jump.

For interactive motion, preserve position and, where appropriate, velocity when an animation is interrupted. A transition can be mathematically continuous yet still feel poor if its duration, scale, or easing is inappropriate.

---

## 4. Easing curves

An easing function maps normalized time `u` to progress `p`.

### Linear
\[
p(u)=u
\]
Constant progress rate. Useful for continuous progress or rotation; often feels mechanical for spatial UI movement.

### Cubic ease-out example
\[
p(u)=1-(1-u)^3
\]
Fast at the start, decelerates toward the destination.

### Cubic ease-in-out example
\[
p(u)=
\begin{cases}
4u^3 & u < 0.5\\
1-(-2u+2)^3/2 & u \geq 0.5
\end{cases}
\]
Slow start and end, faster middle.

These are examples, not Apple's proprietary curves. Compose provides standard easing functions and cubic Bézier support.

```kotlin
object MotionTokens {
    const val fastMillis = 160
    const val standardMillis = 280
    const val emphasizedMillis = 420

    val standardEasing = androidx.compose.animation.core.FastOutSlowInEasing
    val enterEasing = androidx.compose.animation.core.LinearOutSlowInEasing
    val exitEasing = androidx.compose.animation.core.FastOutLinearInEasing
}
```

Guidance:
- Entering elements commonly decelerate into place.
- Exiting elements can accelerate away, but avoid making important content disappear too abruptly.
- Avoid animating every property with the same curve by default.
- Use springs for gesture-driven settling and easing for simple, bounded state changes.

---

## 5. Spring physics

A damped spring is commonly modeled as:

\[
m x'' + c x' + kx = 0
\]

Here `x` is displacement from the target, `m` is mass, `c` is damping, and `k` is stiffness.

The damping ratio is:

\[
\zeta = c/(2\sqrt{km})
\]

- `ζ < 1`: underdamped; oscillation/overshoot is possible.
- `ζ = 1`: critically damped; fastest idealized return without oscillation.
- `ζ > 1`: overdamped; no oscillation, slower settling.

Compose spring APIs express damping and stiffness through their own units and conventions. Do not blindly copy physical values from another engine; tune by observed motion.

```kotlin
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

val gentleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

val responsiveSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)
```

Recommended usage:
- Gentle spring: large layout changes, sheets, large surfaces.
- Responsive spring: small toggles, compact controls, selection indicators.
- Bouncy spring: use sparingly; avoid excessive bounce on primary navigation or accessibility-sensitive controls.

Do not add a spring to every property. Radius, alpha, and content visibility may need different treatment than position.

---

## 6. Choosing the Compose animation API

| Need | API |
|---|---|
| One state-driven value | `animate*AsState` |
| Multiple properties tied to one target state | `updateTransition` |
| Imperative animation, interruption, bounds | `Animatable` |
| Enter/exit content transitions | `AnimatedVisibility` |
| State-to-state content/layout changes | `AnimatedContent` |
| Layout size animation | `animateContentSize` |
| Drag/fling/settle with anchors | `anchoredDraggable` / `AnchoredDraggableState`, depending on Compose version |
| Custom time/physics simulation | `AnimationSpec`, `Animatable`, `AnimationState` where appropriate |
| Per-frame drawing | `Canvas`, `drawWithCache`, graphics layer |
| Custom pixel-level distortion | Runtime shader/AGSL where supported |

Prefer declarative APIs for ordinary UI. Custom simulation code is justified when the interaction needs direct control of position, velocity, constraints, or custom physics.

---

## 7. State-driven animation: mini-player expansion

This example animates a few visual properties from a single boolean state.

```kotlin
@Composable
fun MiniPlayer(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height by animateDpAsState(
        targetValue = if (expanded) 640.dp else 76.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playerHeight"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 0.dp else 24.dp,
        animationSpec = tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing
        ),
        label = "playerCornerRadius"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(cornerRadius),
    ) {
        // Place compact or expanded content here.
        // For a real player, use AnimatedContent or a shared-element approach
        // so the content does not abruptly jump between unrelated layouts.
    }
}
```

Implementation notes:
- Avoid hard-coded `640.dp` as a universal full-screen height. Derive available height using window insets and layout constraints.
- A real mini-player-to-screen transition should coordinate artwork position/size, title, controls, background, and navigation chrome.
- Keep playback state outside the animation composable so recomposition does not restart playback logic.
- Consider `AnimatedContent`, `SharedTransitionLayout` (if available in the project's Compose version), or a custom overlay for a more coherent transition.

---

## 8. Coordinated motion with `updateTransition`

When multiple values must follow the same state, use a shared transition.

```kotlin
@Composable
fun PlayerArtwork(expanded: Boolean) {
    val transition = updateTransition(
        targetState = expanded,
        label = "artworkTransition"
    )

    val size by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) },
        label = "artworkSize"
    ) { isExpanded ->
        if (isExpanded) 280.dp else 52.dp
    }

    val radius by transition.animateDp(
        transitionSpec = { tween(280, easing = FastOutSlowInEasing) },
        label = "artworkRadius"
    ) { isExpanded ->
        if (isExpanded) 18.dp else 8.dp
    }

    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(radius))
    ) {
        // Artwork image
    }
}
```

A shared transition coordinates target state, but each property can have its own spec. Do not assume every property must finish at the same time.

---

## 9. Gesture velocity and drag-to-settle

A drag-driven component should preserve the finger's movement and decide a destination based on position and velocity.

### Conceptual model

At release:
- `x0`: current position.
- `v0`: measured release velocity.
- `target`: chosen anchor.
- Simulation: evolves position using `x0`, `v0`, and spring parameters.

A simplified spring equation with initial conditions is:

\[
m x'' + c x' + k(x-x_{target}) = 0
\]

Use the gesture velocity in the same coordinate unit expected by the animation. Android pointer input commonly reports pixels and pixels/second.

### Prefer anchored drag for discrete states

For a sheet with collapsed and expanded states, use Compose's anchored drag APIs where supported. They handle important interaction details and are generally safer than building a bespoke drag state from scratch.

Pseudo-structure:

```kotlin
// API signatures differ across Compose Foundation versions.
// Check the version in the project before copying this outline.
val anchors = DraggableAnchors {
    SheetValue.Collapsed at collapsedPx
    SheetValue.Expanded at expandedPx
}

// Configure AnchoredDraggableState with:
// - initial value
// - anchors
// - positional threshold
// - velocity threshold
// - animation specification
//
// Attach Modifier.anchoredDraggable(state, orientation = Orientation.Vertical).
```

Implementation requirements:
- Clamp the sheet to valid bounds.
- Define behavior when the user releases between anchors.
- Use velocity and position thresholds consistently.
- Handle content/anchor changes when window size changes.
- Do not run a second independent animation against the same offset.
- Test interrupted drags, rapid reversals, rotation, keyboard/insets, and nested scroll.

---

## 10. Custom numerical spring simulation (only when necessary)

A custom integrator can be useful for unusual deformation or multiple coupled values, but built-in simulations should be preferred for standard UI motion.

For a numerical model, semi-implicit Euler is usually more stable than explicit Euler for simple spring systems:

```kotlin
data class SpringState(
    val position: Float,
    val velocity: Float,
)

fun springStep(
    state: SpringState,
    target: Float,
    stiffness: Float,
    damping: Float,
    mass: Float,
    dtSeconds: Float,
): SpringState {
    require(mass > 0f)
    require(dtSeconds in 0f..0.05f) {
        "Use a bounded timestep; split large frame gaps into smaller steps."
    }

    val displacement = state.position - target
    val acceleration =
        (-stiffness * displacement - damping * state.velocity) / mass

    val nextVelocity = state.velocity + acceleration * dtSeconds
    val nextPosition = state.position + nextVelocity * dtSeconds

    return SpringState(nextPosition, nextVelocity)
}
```

Important:
- This is a numerical approximation, not a drop-in replacement for Compose's animation engine.
- Integrator results depend on timestep and units.
- Never advance simulation using an unbounded frame delta after a pause.
- For production UI, use a tested animation API unless custom simulation is necessary.
- Add unit tests for convergence, overshoot, stability, and finite values.

---

## 11. Transform mathematics

A visual element may be translated, scaled, rotated, and alpha-adjusted.

### Translation
\[
P' = P + T
\]

### Scale about an origin
\[
x' = s_x(x-o_x)+o_x,\quad y'=s_y(y-o_y)+o_y
\]

### 2D rotation
\[
\begin{bmatrix}x'\\y'\end{bmatrix}
=
\begin{bmatrix}\cos\theta&-\sin\theta\\\sin\theta&\cos\theta\end{bmatrix}
\begin{bmatrix}x\\y\end{bmatrix}
\]

In Compose, prefer `Modifier.graphicsLayer` for common visual transforms. Be deliberate about transform origin; scaling from the center and scaling from a corner look very different.

```kotlin
Modifier.graphicsLayer {
    scaleX = scale
    scaleY = scale
    translationY = offsetY
    rotationZ = rotation
    alpha = alphaValue
    transformOrigin = TransformOrigin.Center
}
```

Use layout modifiers for changes that must affect neighboring layout. Use graphics-layer transforms when visual movement should not remeasure the surrounding layout. Profile both for the actual screen.

---

## 12. Liquid Glass: rendering architecture

Liquid Glass should be treated as an optional rendering effect layered on top of a stable UI component.

### Visual building blocks
1. Translucent base material.
2. Edge/highlight treatment.
3. Background blur, if appropriate.
4. Refraction/distortion shader, if supported.
5. Specular highlight or reflection approximation.
6. Shape mask and clipping.
7. Optional motion-linked deformation.
8. Accessibility and low-performance fallback.

### Refraction concept

Snell's law:

\[
n_1\sin(\theta_1)=n_2\sin(\theta_2)
\]

A shader can use surface normals and an approximation of refractive direction to offset background sample coordinates. This does not by itself produce a physically accurate glass renderer: the surface geometry, normals, sampling, edge behavior, color, and lighting all matter.

### Deformation concept

\[
P' = P + D(P,t,g)
\]

where `P` is a surface coordinate, `t` is time, `g` is gesture state, and `D` is a bounded displacement function. Keep displacement bounded and continuous to prevent sudden jumps and sampling artifacts.

### Compose implementation options
- Simple translucent surface: Compose `Surface`, alpha, border, gradient, and shadow.
- Blur: platform/API-appropriate blur or a library, tested on target devices.
- Distortion: AGSL/runtime shader on supported Android versions, with a fallback.
- Complex glass: evaluate a maintained library before writing a renderer from scratch.

Do not assume blur equals refraction. Do not assume all devices or Android versions support the same shader features. Test API-level behavior, hardware acceleration, color spaces, and battery/performance cost.

### Reduced-cost fallback
When the shader is disabled or too expensive, keep the same layout and interaction but replace the shader with:
- translucent solid fill,
- subtle gradient,
- border/highlight,
- restrained elevation,
- no expensive background sampling.

The app must remain usable and visually coherent without the glass effect.

---

## 13. Performance and frame budgets

Approximate frame intervals:
- 60 Hz: 16.67 ms
- 90 Hz: 11.11 ms
- 120 Hz: 8.33 ms

These are total frame intervals, not guaranteed budgets for app code. System scheduling and rendering overhead also consume time.

Checklist:
- Profile on a physical low-end device and a high-refresh device.
- Avoid reading layout state in ways that trigger unnecessary recomposition.
- Use `graphicsLayer` for suitable visual-only transforms.
- Avoid rebuilding expensive paths/shader resources every frame.
- Cache static drawing work with `drawWithCache` when appropriate.
- Avoid excessive nested blur, clipping, and overdraw.
- Do not put expensive database/network operations in animation callbacks.
- Use stable keys in animated lists.
- Inspect Compose recomposition and frame timing with Android Studio tools.
- Test cold start and theme changes; avoid a dark-to-light flash caused by late preference loading.

A 60 FPS target has a 16.67 ms frame interval; 120 FPS has an 8.33 ms interval. Keep meaningful margin below these values.

---

## 14. Accessibility and motion preferences

Provide a reduced-motion mode:
- Reduce large travel distances.
- Remove decorative bounce and repeated oscillation.
- Prefer a short fade or direct state change when motion adds no useful information.
- Never make an essential state understandable only through animation.
- Preserve touch targets and semantics.
- Respect system animation settings where applicable, but verify platform behavior rather than assuming every setting automatically controls every Compose animation.

The final state and content must remain correct if animation is shortened or skipped.

---

## 15. Testing strategy

### Unit tests for math
- `lerp(a, b, 0) == a`
- `lerp(a, b, 1) == b`
- interpolation remains bounded for fractions in `[0, 1]`
- spring simulation remains finite
- settled spring approaches its target
- clamp/anchor logic always returns a valid state
- coordinate conversions do not mix dp and px

### UI tests
- Tap mini-player and expand.
- Tap close/collapse and return to compact state.
- Begin expansion, reverse direction, and release.
- Fast upward and downward flings.
- Repeatedly interrupt transitions.
- Change screen size/orientation while expanded.
- Open keyboard and verify insets/anchors.
- Verify reduced-motion behavior.
- Verify shader fallback produces a usable UI.
- Verify TalkBack labels and semantics.

### Performance tests
- Record frame timing during expansion/collapse.
- Test multiple glass surfaces simultaneously.
- Test low-end hardware, API minimum, and latest target API.
- Test light and dark themes.
- Check memory allocations and shader initialization.
- Confirm no visible one-frame flash when restoring theme or opening a screen.

---

## 16. Recommended implementation phases

### Phase 1 — Motion foundation
- Add `MotionTokens.kt`.
- Standardize duration, easing, and spring choices.
- Replace arbitrary per-component timing values.
- Add reduced-motion preference.

### Phase 2 — Core interactions
- Implement mini-player expansion.
- Coordinate artwork, title, controls, and background.
- Add anchored drag and release velocity.
- Test interrupted transitions.

### Phase 3 — Custom motion
- Add custom deformation only where it clearly improves interaction.
- Use built-in Compose spring APIs for normal layout motion.
- Add unit tests for any custom physics.

### Phase 4 — Liquid Glass renderer
- Start with a stable translucent fallback.
- Add shader refraction as an optional layer.
- Link small bounded deformation to gesture state.
- Profile on real devices and keep a non-shader path.

### Phase 5 — Quality gate
- Accessibility review.
- Light/dark theme review.
- Frame timing and memory review.
- API-level compatibility review.
- Regression tests for gestures, insets, and state restoration.

---

## 17. Definition of done

The implementation is ready when:
- Motion tokens are centralized and documented.
- Animation and business state are separate.
- Gesture release uses coherent position/velocity/anchor rules.
- No component has competing animations writing the same property.
- Transitions remain stable when interrupted.
- Reduced motion and shader fallback work.
- dp/px conversions are explicit.
- Automated tests cover math and critical UI states.
- Real-device profiling shows no recurring frame drops in the main interactions.
- The implementation is described as Apple-inspired, not as Apple's exact private animation system.

---

## 18. Official references

- Compose animation overview: https://developer.android.com/develop/ui/compose/animation
- Compose value-based animations: https://developer.android.com/develop/ui/compose/animation/value-based
- Compose gesture and pointer input: https://developer.android.com/develop/ui/compose/touch-input
- Compose graphics modifiers: https://developer.android.com/develop/ui/compose/graphics/draw/modifiers
- Android AGSL/runtime shaders: https://developer.android.com/develop/ui/views/graphics/agsl
- Compose Foundation API reference: https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/package-summary

**Version note:** Compose APIs evolve. Confirm exact signatures against the project's Compose BOM and Foundation version before adopting snippets, especially anchored dragging and shared-transition APIs.
