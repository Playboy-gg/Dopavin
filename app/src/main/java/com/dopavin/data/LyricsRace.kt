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
data class RaceConfig(val timeoutMs: Long = 10_000, val upgradeGraceMs: Long = timeoutMs)
sealed interface RaceResult {
    val outcomes: Map<String, ProviderOutcome>
    data class Won(val doc: LyricsDoc, override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    data class Instrumental(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    data class NoLyrics(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    data class Failed(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
    data class TimedOut(override val outcomes: Map<String, ProviderOutcome>) : RaceResult
}
suspend fun raceLyrics(
    providers: List<LyricsProvider>,
    query: LyricsQuery,
    config: RaceConfig = RaceConfig(),
): RaceResult {
    if (providers.isEmpty()) return RaceResult.NoLyrics(emptyMap())
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
    var graceUntilMs = -1L
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
