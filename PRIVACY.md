# Privacy policy

**Effective: 2026-11-09** · App: Dopavin (`com.dopavin`) · Contact: open an issue at <https://github.com/playboy-gg/Dopavin/issues>

## The short version

Dopavin does not collect, sell or share your personal data. There is no account, no analytics, no advertising and no remote crash reporting. Your library, likes, playlists, history and settings stay on your phone.

## What is stored on your device

Dopavin stores the following locally, in app-private storage:

- your library, downloaded songs, playlists, favourites, recently played and playback state
- settings, theme choices and glass preferences
- optional credentials you enter yourself: a ListenBrainz token, a Subsonic server address / username / password, or YouTube Music session cookies
- a cache of lyrics and artwork

Credentials live in a separate `accounts` preferences file. Android cloud backup is disabled (`allowBackup="false"`), and backup files you export from Dopavin exclude that file. Credentials are not encrypted at rest; a rooted device or a device with existing malware could read them.

You can delete local data by clearing the app's storage in Android settings or uninstalling the app. Downloaded files can be removed inside the app.

## Network requests

Dopavin only uses the network for the features you use:

| Destination | What is sent | Why |
|---|---|---|
| YouTube Music, through NewPipe Extractor | search queries, video / playlist / channel ids, and your country code (two letters, read from your SIM, network or phone region; never typed in by you) | search, stream and download songs, and show charts of your own country |
| Lyrics providers (LRCLIB, Better Lyrics, Portato, LyricsPlus, Unison, KuGou, lrc.red / BiniLyrics) | track title, artist, album and duration | find synced lyrics |
| GitHub (`api.github.com`) | nothing about you; a standard HTTPS request | check for a newer Dopavin release |
| GitHub (`github.com`, which hands over the picture from `avatars.githubusercontent.com`) | nothing about you; a standard HTTPS request for a public profile picture | show the contributors' pictures in Settings > Contributors; the pictures are kept in the app's cache |
| ListenBrainz, only if you connect it | your token and the song you are playing | scrobbling |
| Your Subsonic-compatible server, only if you configure it | your credentials and the songs you request | stream from your own server |
| YouTube Music sign-in, only if you use it | Google's own sign-in flow in a WebView; the session cookie stays on the phone | import your liked songs and playlists |

Dopavin does not attach an advertising ID, a device fingerprint or a user identifier to these requests. The requests are subject to the privacy policies of the services involved. Dopavin is not affiliated with any of them.

## What is never done

- No analytics SDK, advertising SDK or crash-reporting SDK.
- No account registration with Dopavin.
- No selling, renting or sharing of personal data.
- No access to your contacts, calendar, location, microphone, camera or files outside the app's own storage. "Save to device" uses Android's system file picker, which you control.
- No background upload of your library or listening history.

## Children

Dopavin is a general-purpose music player and is not directed at children under 13. It does not knowingly collect data from anyone.

## Changes

If this policy changes, the new version will be committed to this repository with a new effective date. Because the app collects nothing, changes are expected to be rare.

## Contact

Questions or concerns: open an issue at <https://github.com/playboy-gg/Dopavin/issues>. For security problems, follow [`SECURITY.md`](SECURITY.md) instead of posting publicly.
