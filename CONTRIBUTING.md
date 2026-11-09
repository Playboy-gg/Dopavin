# Contributing to Dopavin

Thanks for wanting to make Dopavin better. This is a personal open-source project, maintained in the open. Small, focused contributions are the easiest to review and merge.

## Before you start

Read the README and this file. For anything larger than a small fix, open an issue first so the approach can be agreed before you write code.

## Set up

Requirements: **JDK 17**, Android SDK **platform 34**, and the included **Gradle 8.7** wrapper.

```bash
git clone https://github.com/playboy-gg/Dopavin.git
cd Dopavin

chmod +x gradlew              # if the executable bit was lost on Android storage
./gradlew testDebugUnitTest   # JVM tests
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # signed release APK (needs your keystore)
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Release builds need your own keystore, configured in `app/build.gradle` or through `~/.gradle/gradle.properties`. **Never commit a keystore, `local.properties`, API tokens or a Subsonic/ListenBrainz credential.**

## Project constraints

Dopavin is also built on-device with CodeAssist, so a few rules are not negotiable:

- Kotlin **1.9.24** with Compose compiler extension **1.5.14**. These are a matched pair. Do not move to Kotlin 2.x.
- Groovy DSL `.gradle` files only. No `.kts`, no version catalogs, no BOM.
- Every dependency has an explicit version.
- No KSP, kapt, Hilt, Room, Coil or Navigation-Compose. The architecture is deliberately dependency-light.
- **Do not add a dependency** without opening an issue first. If approved, update [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) in the same pull request.
- Do not change `compileSdk`, AGP or Gradle settings without the maintainer's approval.

## Code style

- Kotlin official code style; keep the surrounding style.
- UI state flows down from `MusicViewModel`; events flow up as function parameters. Composables never talk to the player, stores or network directly.
- Use the tokens in `ui/theme` for colour, type, spacing, radius and motion. Avoid literal values in screens.
- Every user-visible string belongs in `strings.xml`; every icon-only control needs a content description.
- Minimum touch target **48 dp**. Text uses `sp`. Layouts must survive **200 % font scale** — scroll, never clip.
- Never block the main thread: artwork decoding, file and network work goes off it.
- Prefer simple code. Do not add an abstraction layer until there is a second user of it.

## Pull requests

1. Open an issue for anything larger than a typo or a small fix.
2. Keep one concern per pull request. Avoid drive-by refactors.
3. Describe **what changed and why**, and say honestly what you ran:
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`
   - device/emulator model and Android version, if tested on a device
   - font scale / landscape checks, if the change is visual
4. Add screenshots or a short screen recording for UI changes.
5. If the change is user-facing, add a line to [`CHANGELOG.md`](CHANGELOG.md). Update the README if setup, permissions or behaviour changes.
6. Make sure the branch is up to date and the build passes.

Commit messages: short imperative subject, optional body explaining the reason.

```text
Fix queue order after shuffle is turned off

The player shuffle order now follows the visible queue; restoring the old
order also restores the position.
```

## Translations

Strings live in `app/src/main/res/values/strings*.xml`. Do not translate strings marked `translatable="false"` (URLs, package names, user-agent). Keep placeholders and formatting intact. Open a pull request per language and mention which device you used to check the layout.

## Licensing

Dopavin is licensed under **GPL-3.0**. By contributing, you agree that your contribution is licensed under the same terms. Only submit code you wrote yourself or code whose licence is compatible with GPL-3.0, and keep the original copyright notices. Do not paste code from a project with an incompatible licence.

## Being listed as a contributor

Contributors are shown in the app (Settings > Contributors) and in the README. To be added, a maintainer adds one line to `Contributors.people` in `app/src/main/java/com/dopavin/data/Contributors.kt` (name, exact GitHub login, role) and one line to the README. The in-app page also links to GitHub's own contributors list, which always includes everyone.

## Reporting bugs and security issues

- Bugs: use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.yml). Include the app version, device, Android version and exact steps.
- Feature ideas: use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.yml).
- Security problems: **do not open a public issue**. Follow [`SECURITY.md`](SECURITY.md).

## Community

Be kind and assume good intent. The [Code of Conduct](CODE_OF_CONDUCT.md) applies to issues, pull requests, discussions and every other project space.

Thank you for contributing.
