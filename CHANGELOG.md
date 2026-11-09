# Changelog

All notable user-facing changes. The newest version is first. Release notes are also published on GitHub Releases.

## 1.2 (versionCode 3)

Dopavin is now open source under the GNU GPL v3.0. Source, credits and build instructions are in [`README.md`](README.md) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Accounts & services (all optional): ListenBrainz scrobbling, your own Subsonic-compatible music server, YouTube Music sign-in (liked songs, playlist import). Fade between songs. Android Auto / car browsing of recent songs, favourites and playlists.

Search uses the channel YouTube gives and finds new releases; the queue list now matches what really plays (real shuffle); smarter autoplay.

Recommendations: fixed the scorer (it ignored all but one signal), replays now move to the newest history position, unknown songs no longer get a novelty bonus, blocked songs can no longer reach mood/radio, one shared queue pipeline, new Personal Mix.

Performance: fewer needless screen redraws (Compose strong skipping), smoother UI while downloading (throttled progress), Home no longer sorts the library on every play / pause, and artwork loads faster with a bounded loader, a disk cache and a larger memory cache. The debug APK is no longer debuggable (add `-PdopavinDebuggable=true` for breakpoints).

Home that fits the listener: Home no longer assumes everybody wants Hindi and Punjabi songs. Dopavin now reads the country (SIM, network or phone region) and the phone's languages, without asking anything, and uses them in two places. The charts ("Top songs this week") are those of the listener's own country; before, every listener was treated as British. The tiles under "Browse" follow the phone: a Tamil, Telugu, Malayalam, Kannada, Bengali, Marathi, Gujarati, Urdu, Spanish, Arabic, Korean ... phone gets tiles for that language, a phone in the United States gets English, and an English phone in India keeps the Hindi and Punjabi tiles. The tiles also learn from what is played: songs whose names are written in Devanagari, Gurmukhi, Tamil, Telugu and other scripts move their language to the front. No country or language is ever asked for, and Home shelves saved for another country or language are dropped.

Updates: when the automatic check finds a newer release, an "Update available" dialog now opens (before, only a short message appeared that was easy to miss). It shows the version, the size, the release notes and one button that fits the step: Update now, then the download progress, then Install update (or Try again after a failure). "Later" hides it until the next automatic check, "Skip this version" hides it for that release for good, and a newer release asks again. It is not shown over the first-run onboarding, and a check made by hand on Settings > Updates does not open it.

Onboarding: a new listener now gets a short welcome on the first launch: Dopavin in one line, three pages about finding music, keeping it offline and lyrics, and then "Start with your favourite artists", where a few taps (or Skip) fill the "More from ..." shelves on Home at once. The artists offered are those of the local charts. Every page can be skipped, back goes one page back, and nothing asks for a country or a language. Somebody who updates from an older version already has history and never sees it. The picked artists stay on the phone and are forgotten by "Reset personalization" (which does not bring the onboarding back).

Settings: new "Contributors" page under App. It lists the people behind Dopavin with their GitHub profile pictures; tapping a name opens that person's GitHub profile in the browser. Listed so far: playboy-gg (creator and maintainer) and Sandeepbedia (UI/UX designer). The page also links to everybody GitHub lists as a contributor and to the project page. The pictures are downloaded from GitHub only while this page is open, kept in the app's cache, and replaced by the first letter of the name when offline. PRIVACY.md now lists this, and also the two-letter country code that is sent to YouTube Music for the charts of the listener's own country (read from the phone, never typed in).

Artwork that failed to load because of a network problem is tried again after a minute instead of staying missing until the app is restarted.

Player and queue: the Now Playing cover is shown whole (no fade at its bottom edge, no square shadow edge). Dragging a song in the queue keeps it inside the list and scrolls the list by itself near the top and bottom. Autoplay keeps about ten songs queued; every queue row shows its cover and length (also after a restart); downloaded songs are no longer mixed into streamed autoplay, radio and mood queues unless you are offline; the same recording is no longer queued twice when two uploads differ by a few seconds.

Crash fix: playback that starts while the app is in the background (for example "resume when headphones reconnect") no longer crashes the app on Android 12 and newer when the system refuses to start the playback service. The playback notification is then shown as an ordinary notification and the music carries on.

Lyrics: the cleaned title is no longer lost: a title like "Song (Official Video)" used to be treated as the same search as the cleaned "Song", so only the noisy text and one strict artist-less search were sent. Both are sent now. For labels and channels as uploader ("T-Series", "Brown Town Music") the singer at the end of a title ("Song Name Singer") is tried too, and Hindi, Punjabi and other Indic titles keep their vowel signs when compared. Lyrics errors are shown in plain words only; technical details are no longer shown. Songs that were cached as "not found" by the old search are searched again. Results now follow one priority: word synced, then line synced, then plain text. Later search variants are tried only to find a better kind, LRCLIB no longer stops at a plain-text record when another record is synced, and cached line-synced or plain lyrics are looked at again after 14 or 3 days (an older result is kept if the new search is worse).

## 1.1 (versionCode 2)

In short: predictive back and the shared cover animation in the player, Recap, Share song / artist, lyrics card, current-lyric line, home-screen widget, Spotify link import, Download all / Add all to queue, mobile-data stream quality, "Don't repeat suggestions", haptics switch, update page with in-app download and install, and search paging. The lyric line under the artist now highlights word by word and opens the lyrics when tapped; long titles fade at both edges; the player's More menu shows the player through its glass.

## 1.0 (versionCode 1)

First version: downloads-first player with YouTube Music search and streaming, downloads, playlists, favourites, synced lyrics, equalizer, sleep timer and the liquid-glass interface.
