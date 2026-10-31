package com.dopavin.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Timing of one race. [upgradeGraceMs] is how long a plain-text result waits for a synced rival. Plain
 * text is the last resort, so by default it waits for every provider up to the overall [timeoutMs];
 * a smaller value lets plain text win earlier.
 */
data class RaceConfig(val timeoutMs: Long = 10_000, val upgradeGraceMs: Long = timeoutMs)

/** How a race ended; [outcomes] holds what each provider that finished said (by provider id). */
sealed interface RaceResult {
    val outcomes: Map<String, ProviderOutcome>

    data class Won(val doc: LyricsDoc, override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    data class Instrumental(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    /** Every provider answered and none had usable lyrics for this song. */
    data class NoLyrics(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    /** Nobody won and at least one provider failed (network, rate limit, invalid). Not cacheable. */
    data class Failed(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    /** The time limit ran out before a winner or before every provider answered. */
    data class TimedOut(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
}

/**
 * Choose mode: all [providers] start at once. The first provider that returns usable lyrics for the
 * song wins; empty, wrong-song, malformed or failed answers never win and never stop the others.
 * Properly synced lyrics (word- or line-timed) always beat plain text: a plain result is only held
 * as the last resort and is returned only when no provider delivered synced lyrics before every
 * provider answered (or `upgradeGraceMs` / the timeout ran out). Otherwise the first synced arrival wins.
 * When the race is decided the remaining requests are cancelled. A provider that throws (other than
 * cancellation) is recorded as a network error and does not affect the rest. Cancelling the caller
 * cancels every request.
 */
suspend fun raceLyrics(
    providers: List<LyricsProvider>,
    query: LyricsQuery,
    config: RaceConfig = RaceConfig(),
): RaceResult {
    if (providers.isEmpty()) return RaceResult.NoLyrics(emptyMap())
    // The requests live in their own scope, not as children of the caller: a blocking network call
    // that cannot be interrupted must not hold the result back once the race is decided. The scope
    // is cancelled on every exit, including when the caller itself is cancelled.
    val scope = CoroutineScope(currentCoroutineContext().minusKey(Job) + SupervisorJob())
    try {
        return runRace(scope, providers, query, config)
    } finally {
        scope.cancel()
    }
}

private suspend fun runRace(
    scope: CoroutineScope,
    providers: List<LyricsProvider>,
    query: LyricsQuery,
    config: RaceConfig,
): RaceResult {
    val channel = Channel<Pair<String, ProviderOutcome>>(Channel.UNLIMITED)
    val jobs = providers.map { provider ->
        scope.launch {
            val outcome = try {
                provider.fetch(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProviderOutcome.NetworkError(e.javaClass.simpleName)
            }
            channel.send(provider.id to outcome)
        }
    }
    val outcomes = LinkedHashMap<String, ProviderOutcome>()
    var best: LyricsDoc? = null
    var instrumental = false
    val start = System.nanoTime()
    fun remainingMs() = config.timeoutMs - (System.nanoTime() - start) / 1_000_000L
    var graceUntilMs = -1L // absolute (ms since start) end of the plain-winner grace period

    fun absorb(id: String, outcome: ProviderOutcome) {
        outcomes[id] = outcome
        when (outcome) {
            is ProviderOutcome.Success -> if (outcome.doc.usable) {
                val current = best
                if (current == null || (current.kind == LyricsKind.Plain && outcome.doc.kind != LyricsKind.Plain)) {
                    best = outcome.doc
                }
            }
            is ProviderOutcome.Instrumental -> instrumental = true
            else -> {}
        }
    }

    var timedOut = false
    while (outcomes.size < providers.size) {
        val winner = best
        if (winner != null && winner.kind != LyricsKind.Plain) break
        val wait = if (winner != null) {
            // A plain winner: only wait out the remaining grace for a synced rival.
            if (graceUntilMs < 0) graceUntilMs = (System.nanoTime() - start) / 1_000_000L + config.upgradeGraceMs
            minOf(graceUntilMs - (System.nanoTime() - start) / 1_000_000L, remainingMs())
        } else {
            remainingMs()
        }
        if (wait <= 0) {
            timedOut = winner == null
            break
        }
        val next = withTimeoutOrNull(wait) { channel.receive() }
        if (next == null) {
            timedOut = winner == null
            break
        }
        absorb(next.first, next.second)
    }
    jobs.forEach { it.cancel() }
    channel.close()

    val doc = best
    return when {
        doc != null -> RaceResult.Won(doc, outcomes)
        instrumental -> RaceResult.Instrumental(outcomes)
        timedOut -> RaceResult.TimedOut(outcomes)
        outcomes.values.any {
            it is ProviderOutcome.NetworkError || it is ProviderOutcome.RateLimited
        } -> RaceResult.Failed(outcomes)
        else -> RaceResult.NoLyrics(outcomes)
    }
}
