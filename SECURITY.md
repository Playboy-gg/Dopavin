# Security policy

Dopavin handles local music, optional service credentials and an in-app APK installer. If you find a security problem, please report it privately so it can be fixed before it is public.

## Supported versions

| Version | Supported |
|---|---|
| 1.1.x (latest) | Yes |
| Older versions | No — please update first |

Only builds from this repository's [Releases](https://github.com/playboy-gg/Dopavin/releases) or built from this source are supported. Re-signed, modified or repackaged builds are not.

## How to report

**Use GitHub's private vulnerability reporting:**

1. Open the **Security** tab of this repository.
2. Click **Report a vulnerability**.
3. Fill in the form with the details below.

Please **do not** open a public issue, pull request or discussion for a security problem.

If private reporting is unavailable, open a minimal public issue asking for a private channel — do not include vulnerability details, credentials or exploit steps in it.

## What to include

- Dopavin version (`Settings > About`) and install source
- Device model and Android version
- A clear description of the problem and its impact
- Steps to reproduce, or a proof of concept
- Any relevant logs or screenshots, **with tokens, cookies, server addresses and personal data removed**

Do not send real ListenBrainz tokens, Subsonic passwords, YouTube cookies or personal music data. Use a throwaway account and a test server where possible.

## Scope

In scope:

- The Android app and its source in this repository
- Credential storage (the private `accounts` preferences file)
- Network handling: HTTP clients, TLS, redirects, downloaded files
- The in-app update checker and APK installer
- Exported activities, services, receivers and content providers
- Backup and restore handling
- Release build and signing configuration

Out of scope:

- The terms of service of YouTube, Google, LRCLIB, ListenBrainz, Subsonic or any lyrics provider
- Bugs in upstream projects (report those to NewPipe Extractor, AndroidX, Media3, Backdrop, Capsule or FloatingTabBar)
- Third-party service outages, rate limits or account bans
- Physical access to an unlocked device, rooted devices, or malware with existing device access
- Attacks that require the user to install a modified APK from an untrusted source
- Denial of service against third-party services

## Good-faith research

Good-faith security research is welcome. Please:

- Test only against your own device, account and server
- Do not access, modify or delete other people's data
- Do not degrade a third-party service or bypass its rate limits
- Give reasonable time to fix the issue before publishing

## What to expect

This is a personal project maintained in spare time, with no bug bounty. Reports are acknowledged on a best-effort basis, usually within a week. You will be credited in the fix notes unless you ask to stay anonymous.

## Safe defaults in the app

- `allowBackup="false"`; service credentials live in a separate preferences file and are excluded from backup exports
- No storage permission; downloads stay in app-private storage
- Update APKs are downloaded over HTTPS and handed to the system installer only after the user confirms
- No analytics, advertising SDKs or remote crash reporting
