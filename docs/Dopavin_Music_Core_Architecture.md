# Dopavin Music Core Architecture & Intelligent Discovery

**Document type:** Developer-facing architecture specification\
**Status:** Proposed design --- implementation must be reconciled with
the current repository\
**Application:** Dopavin\
**Package:** `com.dopavin`\
**Platform:** Android, Kotlin, Jetpack Compose, Media3\
**Primary principle:** Preserve existing playback behavior while adding
a modular music catalog, mood-aware recommendations, queue generation,
personalization, and provider integration.

------------------------------------------------------------------------

## 1. Purpose

Dopavin should evolve from a local music player into a music player that
can understand the user's music preferences, provide mood-aware
discovery, and generate useful playback queues without compromising
reliability or user control.

This document defines the intended responsibilities, data boundaries,
algorithms, queue behavior, provider integration, persistence strategy,
implementation stages, and acceptance criteria for that core.

This is an architecture proposal, not a claim that these features
already exist in the repository. Before implementation, inspect the
current source code and adapt the proposed interfaces to the actual
project.

## 2. Scope

### Included

-   Common track/catalog model for local and supported online sources.
-   Mood selection and mood-aware recommendations.
-   Personalized ranking based on explicit preferences and listening
    behavior.
-   Smart Autoplay and generated queues.
-   Song Radio, Mood Queue, and Personal Mix concepts.
-   Queue safety and manual-user-control rules.
-   Local-first behavior and provider adapters.
-   Listening history and feedback capture.
-   Error handling, performance requirements, testing, and staged
    delivery.

### Out of scope unless separately approved

-   Replacing the existing Media3 playback service.
-   Adding a second ExoPlayer or parallel playback service.
-   Mandatory cloud AI, a large on-device ML model, or a remote
    recommendation backend.
-   Inferring or diagnosing the user's mental-health state.
-   Using microphone, precise location, contacts, or other sensitive
    signals to infer mood.
-   Automatic upload of the user's local music library or listening
    history.
-   Language-based song suggestions or a language-based recommendation
    UI.

## 3. Non-negotiable product rules

1.  **Preserve existing playback.** Keep the current playback
    architecture unless repository inspection proves a specific change
    is necessary.
2.  **Separate discovery from playback.** A catalog result is not
    automatically a playable track.
3.  **Protect user intent.** Manually added queue items and explicit
    playlist/album order must not be silently overwritten by generated
    recommendations.
4.  **Treat mood as a listening goal, not a diagnosis.** User-selected
    mood takes precedence over optional contextual signals.
5.  **Work offline.** Local playback and local recommendations must
    continue without network access.
6.  **Do not fabricate metadata.** Unknown features stay unknown;
    scoring must tolerate missing data.
7.  **Make automation controllable.** Autoplay, mood-based queue
    replacement, and personalization must have clear user controls.
8.  **Isolate provider failures.** An online source failure must not
    crash or stop otherwise valid local playback.
9.  **No language-based suggestions in the UI.** Do not present language
    as a mood, recommendation category, mix type, or suggestion filter.
    Do not use language as a recommendation-ranking signal. Language
    metadata may only be retained internally if needed for source
    fidelity or catalog interoperability; it must not drive the
    suggestion experience.
10. **Verify actual APIs.** The existing InnerTube module must be
    inspected before its supported capabilities are assumed.

## 4. High-level architecture

The core consists of the following logical layers:

1.  **Presentation layer:** Now Playing, Queue, Search, Mood selector,
    Recommendations, Likes, and playback controls.
2.  **Application/domain layer:** Use cases such as `GenerateQueue`,
    `SetMood`, `PlayTrack`, `RecordListeningFeedback`, and
    `RefreshAutoplay`.
3.  **Intelligence layer:** Mood context, candidate retrieval,
    recommendation scoring, personalization, diversity, and queue
    ordering.
4.  **Catalog layer:** Normalized track and artist metadata, source
    identity, artwork references, and availability.
5.  **Playback integration layer:** Existing `PlaybackService`, Media3
    ExoPlayer, `PlayerConnection`, and `PlaybackRuntime`.
6.  **Persistence layer:** Existing settings store plus structured
    storage for history, feedback, cached catalog data, and queue state
    as justified by repository inspection.
7.  **Provider adapters:** Local MediaStore access and independently
    implemented online catalog/playback adapters.

### Data flow

1.  The user chooses a mood, starts a Song Radio session, or requests
    Autoplay.
2.  The application captures a recommendation context.
3.  Candidate retrieval obtains tracks from eligible local and enabled
    online sources.
4.  Eligibility rules remove excluded or unusable candidates.
5.  Ranking scores candidates based on personal taste, current goal, and
    available track features.
6.  Queue diversification and ordering produce a useful sequence.
7.  Playability is validated before committing generated items.
8.  The queue is handed to the existing playback integration.
9.  Listening events update the user's preference profile and future
    recommendations.

Recommendation work must not block the playback thread or perform
expensive operations on the main thread.

## 5. Catalog and track model

### 5.1 Required conceptual fields

Each catalog entry should support the following fields when available:

-   `trackId`: stable Dopavin catalog identifier.
-   `title`: display title.
-   `artists`: one or more normalized artists.
-   `album`: album and album-artist information, if known.
-   `durationMs`: duration, if known.
-   `sourceType`: local or an identified online provider.
-   `sourceTrackId`: provider-specific identifier.
-   `playableReference`: source-specific reference required by the
    playback adapter.
-   `artworkReference`: local or provider artwork reference.
-   `genres`: known genre tags.
-   `moodTags`: known mood descriptors.
-   `energy`: optional normalized estimate.
-   `valence`: optional normalized estimate of musical emotional tone.
-   `tempoBpm`: optional tempo estimate.
-   `availability`: available, unavailable, or unknown.
-   `metadataConfidence`: optional indication of metadata reliability.

Language must not be used as a recommendation feature and must not
appear as a language-based suggestion UI. If a provider requires
language metadata to preserve its original catalog information, keep it
outside the recommendation scoring path.

Missing values must be represented explicitly rather than replaced with
fabricated defaults.

### 5.2 Track identity and duplicates

A title match alone is insufficient to identify two tracks as the same
recording. Use source IDs where available and compare normalized title,
artist, duration, album, and version/remaster information when
necessary.

Keep these concepts distinct:

-   **Catalog entry ID:** identifies one Dopavin entry.
-   **Recording relationship:** links entries that appear to represent
    the same recording.
-   **Composition/work relationship:** optionally links different
    recordings of the same underlying work.

Do not merge entries merely because their titles are similar. Keep
alternate versions accessible when the user chooses them. Recommendation
and queue logic may avoid accidental duplicate recordings without
deleting the user's chosen entries.

### 5.3 Catalog sources

**Local catalog** - Continue using the existing `MusicRepository` and
Android MediaStore integration. - Respect Android storage/media
permissions and refresh the catalog incrementally. - Local music remains
the primary offline source.

**Online catalog adapters** - Normalize metadata into the common catalog
model. - Preserve source-specific IDs and provider attribution where
required. - Separate metadata search from playback-reference
resolution. - Respect provider restrictions, rate limits, and
availability.

**Optional feature analyzer** - Derive music features only when suitable
metadata or supported audio analysis is available. - Avoid scanning or
decoding every file at startup. - Cache derived features and invalidate
them when relevant source data changes.

## 6. Mood engine

### 6.1 Mood is a listening intention

The system should distinguish:

-   **Selected mood/goal:** what the user explicitly asks to hear.
-   **Track characteristics:** what the music itself sounds like.
-   **Listening context:** optional, user-controlled context such as an
    activity or time-of-day preference.

A user who feels reflective may still want uplifting music. Therefore,
the system must not assume that the user's emotional state and desired
music mood are identical.

### 6.2 Initial mood presets

The first version may offer these presets:

-   Chill
-   Happy
-   Energetic
-   Focus
-   Reflective
-   Romantic

These are starting product choices, not an exhaustive list. Mood names
and descriptions should be understandable, non-clinical, and easy to
change.

The mood UI must not include language-based categories, language
filters, or language-driven suggestion labels.

### 6.3 Priority of signals

Use this priority order:

1.  Explicit current user selection.
2.  Explicit user preferences and exclusions.
3.  Current listening session and queue context.
4.  Learned long-term preferences.
5.  Optional contextual signals, only when enabled and available.

Do not infer a user's emotional or mental-health state from skips,
listening time, or song choice. Listening behavior is evidence of music
preference, not a diagnosis.

### 6.4 Track features

Potential features include:

-   Energy
-   Valence
-   Tempo
-   Acousticness, if available
-   Instrumentalness, if available
-   Genre affinity
-   Mood tags

Each feature is optional. The ranker must adapt to missing values rather
than assuming every track has complete audio analysis.

## 7. Recommendation engine

### 7.1 Strategy

Start with a deterministic, testable, rule-based hybrid recommender. Do
not require a cloud AI service or large ML model for the initial
implementation.

The recommender should:

1.  Retrieve candidates.
2.  Apply hard eligibility and exclusion rules.
3.  Score candidates.
4.  Diversify the ranked list.
5.  Return candidates with reasons and available confidence information.

### 7.2 Candidate retrieval

Candidates can come from:

-   Local library.
-   Explicit favorites and saved playlists.
-   Similar tracks and artists.
-   Tracks relevant to the current mood/goal.
-   Tracks not heard recently but matching the user's taste.
-   Enabled online catalogs that can provide usable metadata and
    supported playback references.

Candidate retrieval should be bounded and cancellable. Do not scan the
entire library expensively for every UI interaction.

### 7.3 Eligibility rules

Before ranking, handle:

-   User-hidden or blocked tracks.
-   Explicit "not interested" feedback.
-   Unavailable or unplayable items.
-   Accidental duplicates.
-   Recent skips, with context-sensitive treatment.
-   Tracks already in the queue, depending on queue mode.
-   Explicit content preferences, if the user has configured them.

Hard exclusions must not be overridden by a high recommendation score.

### 7.4 Initial ranking model

A normalized weighted score can be used:

`score(track) = sum(weight_i * feature_i(track)) / sum(available weights)`

Each feature score must be normalized to a common range. Missing
features should be excluded from the denominator or handled by a
documented fallback, rather than treated as zero without justification.

Suggested initial weights for tuning:

  Signal                             Initial weight
  -------------------------------- ----------------
  Mood/listening-goal match                     25%
  Personal taste                                25%
  Genre/audio-feature similarity                15%
  Favorite artist affinity                      10%
  Recent-session relevance                      10%
  Novelty/rediscovery                           10%
  Optional context match                         5%

These are starting values, not empirically validated optimal weights.
The implementation must make the weights testable and tunable.
**Language must not be a scoring signal.**

The ranking result should include a short internal reason code, such as
`MOOD_MATCH`, `FAVORITE_ARTIST`, `SIMILAR_TRACK`, or `REDISCOVERY`.
User-facing explanations should be concise and truthful; do not claim a
match based on unavailable metadata.

### 7.5 Feedback interpretation

Possible signals:

-   Like/favorite: strong positive preference.
-   Repeated replay: positive signal, context dependent.
-   Song completion: weak-to-moderate positive signal, not proof of a
    favorite.
-   Manual queue addition: strong evidence of current intent.
-   Quick skip: weak negative signal.
-   Repeated consistent skips: stronger negative evidence.
-   "Not interested": strong negative preference.
-   Artist/album follow or explicit preference: positive affinity.

A single skip must not permanently penalize a track. Calls,
interruptions, mood changes, and accidental playback can explain a skip.
Use repeated patterns and recency-aware weights.

## 8. Queue generation engine

### 8.1 Responsibilities

The queue engine converts ranked candidates into an ordered, playable
sequence. It is responsible for:

-   Choosing candidates for a queue mode.
-   Deduplication.
-   Artist/album diversity where appropriate.
-   Sequence ordering and energy flow.
-   Protecting manually arranged items.
-   Validating playability before committing items.
-   Recording why generated items were added.
-   Avoiding repeated retries when no suitable candidates exist.

### 8.2 Queue modes

**Smart Autoplay** - Extends the queue when the remaining playable queue
is low. - Appends generated tracks instead of silently replacing the
queue. - Respects the Autoplay toggle. - Uses local candidates if online
discovery is unavailable.

**Mood Queue** - Generates a batch based on the selected mood and
listening goal. - Offers a clear choice to append, replace the generated
section, or request a single suggestion. - Does not destroy the current
queue without explicit confirmation.

**Song Radio** - Builds a queue related to the selected seed track,
including relevant genre, artist, and musical-feature similarity. -
Same-artist concentration may be appropriate in artist-focused radio.

**Personal Mix** - Combines long-term taste, recent listening,
favorites, and rediscovery. - Mood is optional; it must not silently
override the user's current goal.

### 8.3 Queue-item provenance

Each queue item should carry a stable queue-item ID and provenance, not
only a track ID.

Illustrative Kotlin model:

``` kotlin
data class QueueItem(
    val queueItemId: String,
    val trackId: String,
    val origin: QueueOrigin,
    val addedAtMs: Long,
    val recommendationReason: String?,
    val generationId: String?,
)

enum class QueueOrigin {
    MANUAL,
    PLAYLIST,
    ALBUM,
    SMART_AUTOPLAY,
    MOOD_QUEUE,
    SONG_RADIO,
    PERSONAL_MIX
}
```

Adapt the names and fields to the existing project. The model must not
be copied blindly before inspecting current queue types.

### 8.4 Queue ownership rules

-   Manual queue entries must not be silently removed or reordered.
-   Album and playlist playback must preserve intentional order.
-   Smart Autoplay appends new entries at the end.
-   User can remove, reorder, pin, or skip generated suggestions.
-   Mood changes do not automatically destroy the active queue.
-   Any destructive regeneration must be explicit and explain what will
    be replaced.
-   The currently playing item must not be unexpectedly replaced by a
    background recommendation refresh.
-   Unavailable items must be handled gracefully with a clear state and,
    where appropriate, a supported alternative.
-   Shuffle and repeat behavior must remain consistent with the existing
    playback implementation.

### 8.5 Generation timing

Use a configurable threshold for remaining playable items. Generate a
batch before the queue becomes empty, but do not continually generate
more content when Autoplay is disabled.

Recommendation requests must be cancellable when the user changes mood,
search, or queue mode. Commit a generated batch atomically after
validation to avoid duplicate or partially inserted queues.

### 8.6 Diversity and sequence quality

Avoid unnecessary runs of the same artist, album, or recording in
general-purpose modes. These are soft constraints, not universal rules:
album playback and artist radio should preserve their intended focus.

A conceptual transition score between consecutive tracks can combine:

-   Musical similarity.
-   Energy transition suitability.
-   Mood/valence transition suitability.
-   Personal relevance of the next track.

The desired transition differs by mode. Chill may prefer smoother
changes; energetic sessions may benefit from a gradual build; a surprise
mode may prioritize variety. Smooth transitions must never override
explicit user choices.

## 9. Playback integration

The recommendation and queue systems must use the existing playback
architecture.

Current project components identified from the project description: -
`PlaybackService`: MediaSessionService and ExoPlayer. -
`PlayerConnection`: application-side playback connection. -
`PlaybackRuntime`: effects and sleep behavior. - `MusicRepository`:
local MediaStore catalog. - `SettingsStore`: SharedPreferences-backed
settings.

These components must be verified in the actual repository before
changes.

Recommended integration responsibilities:

-   Compose UI sends user actions to application/domain use cases.
-   Use cases request catalog/recommendation/queue operations.
-   Queue operations pass validated queue items to the existing playback
    connection.
-   The playback service remains the authority for actual playback
    state.
-   UI observes playback state instead of creating a parallel source of
    truth.
-   Recommendation exceptions must not stop the current track.
-   No second ExoPlayer or replacement playback service should be
    introduced without a demonstrated need.

## 10. Provider adapter design

Keep provider-specific APIs outside the recommendation and queue domain.
A provider adapter should normalize catalog results and report
availability/errors using common types.

Illustrative catalog contract:

``` kotlin
interface MusicCatalogSource {
    suspend fun search(query: String): List<CatalogTrack>

    suspend fun getTrack(
        sourceTrackId: String
    ): CatalogTrack?
}
```

This is a conceptual minimum only. A production contract may need
pagination, cancellation, attribution, rate-limit handling, error types,
artwork, and availability fields.

Catalog metadata retrieval and playable-source resolution should be
separate responsibilities. A visible search result is not guaranteed to
be playable.

### Existing InnerTube module

The user has indicated that an InnerTube module was added to the
project. Before integrating it:

1.  Inspect the module's actual public API and Gradle configuration.
2.  Determine what metadata/search functionality it supports.
3.  Verify what playback references it can legitimately provide.
4.  Check compatibility, network requirements, error handling,
    licensing, and provider restrictions.
5.  Implement an adapter only for capabilities verified in the source.
6.  Do not assume support for direct stream extraction, unrestricted
    background playback, or any unsupported provider behavior.
7.  Keep local playback operational if the module fails or is
    unavailable.

Do not claim the online player is implemented until the module has been
inspected and tested in the real project.

## 11. Persistence and state

The existing `SettingsStore` can continue to hold simple preferences,
such as Autoplay and selected options.

Structured state may include: - Listening history. - Likes and explicit
negative feedback. - Recommendation feedback events. - Cached catalog
metadata. - Derived track features. - Queue state required for
restoration. - Optional user preference profile.

A Room/SQLite store is a recommended option for structured history and
feedback, but it must be evaluated against the existing repository and
dependencies before adoption. Do not introduce a database dependency
without checking the current architecture and migration needs.

Persist only what is needed. Provide a way to clear listening history or
reset personalization if those features are implemented. Avoid uploading
local listening history by default.

## 12. Offline behavior and error handling

  -----------------------------------------------------------------------
  Condition                           Expected behavior
  ----------------------------------- -----------------------------------
  No internet                         Local playback and local
                                      recommendations continue

  Online provider timeout             Fall back to local candidates;
                                      current playback remains active

  Online track unavailable            Mark unavailable and avoid
                                      selecting it as playable

  Incomplete metadata                 Rank using available features; do
                                      not invent values

  Empty local library                 Show useful empty state and
                                      supported discovery options

  Autoplay disabled                   Do not extend the queue
                                      automatically

  No suitable candidates              Stop generation cleanly; do not
                                      retry indefinitely

  Duplicate provider results          Deduplicate carefully without
                                      hiding alternate versions

  Media permission revoked            Show the appropriate
                                      permission/empty state; avoid
                                      crashes

  App process recreated               Restore supported queue and
                                      playback state through existing
                                      mechanisms

  Recommendation exception            Keep current playback unaffected
  -----------------------------------------------------------------------

## 13. Performance requirements

-   Do not block the main thread with catalog scans, ranking, or network
    calls.
-   Use bounded concurrency for metadata and artwork work.
-   Make recommendation requests cancellable.
-   Use incremental catalog refresh where possible.
-   Cache derived metadata and recommendation results with clear
    invalidation rules.
-   Avoid full-library audio analysis at startup.
-   Avoid polling the provider continuously.
-   Keep the playback service independent from UI lifecycle.
-   Profile memory, battery, startup time, and long-session behavior on
    the target device class.
-   Use measured performance results before introducing more complex
    ranking or machine-learning models.

## 14. Privacy and user controls

-   Mood selection is optional.
-   Do not infer a clinical or mental-health diagnosis.
-   Do not require microphone or precise location access for
    recommendations.
-   Do not upload the user's local library or listening history by
    default.
-   Make Autoplay and personalization controls understandable.
-   Respect explicit exclusions and "not interested" actions.
-   Do not use language as a suggestion category, recommendation filter,
    or ranking signal.
-   If contextual signals are added later, document them, make them
    optional, and explain their effect.

## 15. Testing strategy

### Unit tests

-   Normalization and track identity.
-   Missing metadata behavior.
-   Recommendation score calculation.
-   Hard exclusions and feedback weighting.
-   Mood selection precedence.
-   Queue deduplication and diversity.
-   Queue ordering and manual-item protection.
-   Cancellation and stale-generation handling.

### Integration tests

-   Queue updates through the existing playback connection.
-   Media3 queue and current-item synchronization.
-   Smart Autoplay threshold and disabled state.
-   Online adapter errors and local fallback.
-   Queue restoration after supported process recreation.
-   Catalog refresh and permission changes.

### Manual acceptance scenarios

1.  Play a local song, enable Autoplay, and verify that new tracks are
    appended without interrupting playback.
2.  Add a track manually and verify that generated items do not reorder
    or remove it.
3.  Choose Chill and generate a mood queue.
4.  Change mood while music is playing and verify that the current track
    is not unexpectedly replaced.
5.  Disable network and verify local playback and local recommendations.
6.  Make a track unavailable and verify that it does not repeatedly
    break the queue.
7.  Like several tracks and verify that future ranking responds
    gradually.
8.  Skip a single track and verify that this alone does not permanently
    suppress it.
9.  Use album and artist-focused playback and verify that diversity
    rules do not break the selected mode.
10. Verify that no language-based suggestion UI or language-based
    recommendation logic exists.
11. Simulate provider errors and verify that the current playback
    remains active.
12. Profile startup, memory, battery, and long playback sessions.

## 16. Staged implementation plan

### Stage 1 --- Repository audit

-   Inspect current playback, queue, repository, settings, dependency,
    and InnerTube module code.
-   Document existing behavior and define regression tests.
-   Do not redesign the UI or replace playback as part of the audit.

### Stage 2 --- Catalog foundation

-   Introduce or normalize the common track model.
-   Add source identity and availability.
-   Implement safe duplicate handling.
-   Preserve local MediaStore playback.

### Stage 3 --- Queue foundation

-   Add queue-item provenance.
-   Implement safe append, deduplication, manual queue protection, and
    availability checks.
-   Add Smart Autoplay with a user-controlled toggle.

### Stage 4 --- Mood and baseline recommendations

-   Implement mood presets and recommendation context.
-   Add deterministic scoring and explainable reason codes.
-   Generate Mood Queue and Song Radio candidates.

### Stage 5 --- Feedback and personalization

-   Record likes, completion, replay, skips, and explicit negative
    feedback.
-   Persist structured feedback if justified by repository inspection.
-   Tune ranking from repeatable tests and user-visible results.

### Stage 6 --- Online provider adapter

-   Audit the existing InnerTube module.
-   Integrate only verified and supported functionality.
-   Validate online availability and local fallback.
-   Do not let provider-specific code leak into the domain layer.

### Stage 7 --- Quality and optimization

-   Tune queue transitions and diversity.
-   Add robust restoration and error states.
-   Profile performance and run the complete regression suite.

Each stage should be completed and tested before the next stage expands
scope.

## 17. Definition of done

The core is ready for release only when:

-   Existing local playback continues to work.
-   There is a single authoritative playback state.
-   Mood selection can generate a relevant queue from available tracks.
-   Smart Autoplay respects user settings and queue ownership.
-   Manual queue entries are not silently altered.
-   Recommendation logic tolerates missing metadata.
-   Listening feedback influences future results in a controlled,
    testable way.
-   Online provider failure cannot break local playback.
-   Offline behavior is verified.
-   There are tests for scoring, queue behavior, provider failure, and
    playback integration.
-   Performance is profiled on representative hardware.
-   No language-based suggestion UI or language-based recommendation
    scoring is present.
-   The actual repository has been inspected and all proposed interfaces
    have been adapted to its real APIs.

## 18. Implementation handoff notes

Before writing implementation code, inspect the real source files for: -
Existing queue model and queue mutation APIs. - `PlaybackService`,
`PlayerConnection`, and `PlaybackRuntime`. - `MusicRepository` and
`SettingsStore`. - Current Gradle modules and dependency versions. -
InnerTube module source, API, and licensing. - Current permissions and
supported online playback sources.

Do not invent classes, functions, dependencies, or module APIs that have
not been verified. Reuse existing project conventions where possible.
Keep changes incremental, preserve current behavior, and run relevant
build/tests after each stage.

**Final product direction:** Dopavin should combine reliable local
playback with optional online discovery, mood-aware recommendations,
user-controlled queue generation, and gradual personalization---without
language-based suggestions and without compromising the user's control
over playback.
