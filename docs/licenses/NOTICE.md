# Third-party notices

Dopavin vendors two Apache License 2.0 libraries by Kyant0 as source.
Full licence text: `Backdrop-LICENSE.txt`, `Capsule-LICENSE.txt` (Apache-2.0).

| Library | Upstream | Location in app | Changes |
|---|---|---|---|
| Backdrop v2.0.0 | https://github.com/Kyant0/backdrop | `com.dopavin.ui.glass.backdrop` | package renamed, KMP expect/actual merged into Android source set, `backdropScale` added for cheaper rendering |
| Capsule | https://github.com/Kyant0/Capsule | `com.kyant.capsule` (unchanged package) | none (see build notes in docs/06) |

Copyright 2025 Kyant0. Licensed under the Apache License, Version 2.0.
Each vendored file keeps its original licence header/notice.

## FloatingTabBar (vendored, modified)
`com.dopavin.ui.glass.floatingtabbar` - FloatingTabBar v1.0.1 by Elyes Mansour, Apache License 2.0 (modified by the owner and adapted here). Bar wrapper `ui/glass/nav/DopavinNavBar.kt` and `GlassEffect.kt` are the owner's own code.
