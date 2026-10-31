# 14 - Releasing a version and telling users what changed

Dopavin's in-app updater (F-206/F-207, Settings > Updates) reads the **latest GitHub release** of `playboy-gg/Dopavin`. What you publish there is exactly what users see on the Updates screen (version, size, "What's new").

## What to upload to GitHub for every new version

1. **Source (git push)** - the whole project: `app/`, `docs/`, `README.md`, `CHANGELOG.md`, `RELEASE_NOTES_v<version>.md`, `AGENTS.md`, `TASKS.md`, `CURRENT_STATE.md`, Gradle files. Do not upload `build/`, `.gradle/`, `local.properties` or keystores.
2. **A GitHub Release** (Releases > Draft a new release):
   - **Tag**: `v` + versionName, e.g. `v1.1`. The tag must be higher than the installed `versionName`; the app compares the numbers (`1.10` is newer than `1.9`).
   - **Title**: `Dopavin 1.1`.
   - **Description**: paste the contents of `RELEASE_NOTES_v<version>.md`. The app shows this text as "What's new", so write it for users, not developers.
   - **Attach the signed APK** named `Dopavin-<version>.apk`. The app downloads the first attached file that ends in `.apk` inside the app; without one the button opens the release page.
   - Leave **Set as a pre-release** and **draft** OFF: the app ignores drafts and pre-releases.
3. Publish. Within about 12 hours (or at once through "Check for updates") installed apps show a "New" badge on Settings > Updates and one short message; the full screen with the notes is on that page.

## Before you tag

- Raise `versionCode` (+1) and `versionName` in `app/build.gradle`.
- Sign every release with the SAME keystore. Android refuses to update an app over a build signed with a different key. Keep the keystore and its passwords backed up outside the repository.
- Add a section to `CHANGELOG.md` and create `RELEASE_NOTES_v<version>.md`.
- Build the release APK, install it over the previous version on a phone, and check that the app opens and plays.

## What the app does and does not do

- It downloads the APK into its own cache, checks that it is Dopavin with a higher version code (and the same signing certificate), then opens Android's install screen. The user always confirms; nothing installs silently. The first time, Android asks to allow installs from Dopavin (permission `REQUEST_INSTALL_PACKAGES`).
- A repository with no published release simply shows "You have the latest version".
- If GitHub cannot be reached, a manual check says so; the automatic check stays silent.
- If the APK is signed with a different key, the app refuses it and Android would too: keep using one keystore.
