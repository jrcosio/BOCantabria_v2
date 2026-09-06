package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/** One second, five, thirty: a busy store or a passing I/O error, without polling a corrupt one for ever. */
internal val READ_RETRY_DELAYS: List<Long> = listOf(1_000L, 5_000L, 30_000L)

/**
 * Keeps a store flow alive across a transient read failure (feature 014, STAB-004).
 *
 * `catch` runs when the upstream has already terminated, and emitting a fallback there does not bring
 * a Room flow back: the screen kept the empty list for as long as it lived, and the bell's badge —
 * whose owner is never recreated — stayed at zero for the rest of the process. Only re-collecting
 * brings the flow back, so on a failure this reports, emits [fallback], waits, and **re-subscribes**.
 * A successful emission resets the budget; after [delays].size consecutive failures the flow completes
 * quietly — the fallback is already out — so a permanently broken store is not polled for ever. For
 * `combine` and `stateIn`, a completed input is indistinguishable from one that stays silent, and a
 * later re-subscription (the screen coming back) starts with a fresh budget.
 *
 * Two things are load-bearing:
 * - The whole chain sits inside `flow { }` so the failure counter lives **per collection**: the same
 *   `Flow` object is collected many times — every restart of `WhileSubscribed` — and they must not
 *   share it. `retryWhen` and `catch` share `catchImpl`, which rethrows the collector's own
 *   exceptions untouched, so exception transparency holds.
 * - `CancellationException` is checked **first**: `retryWhen` only skips the collector's own
 *   cancellation, and one thrown by the upstream while the collector is still active would otherwise
 *   be reported and retried.
 *
 * **MUST be the last operator, after `flowOn`.** The delays then run in the collector's context —
 * `viewModelScope` in production, the test scheduler under `runTest`. Under `flowOn(io)` they would
 * live in the test dispatcher's own scheduler, which `runTest` never advances (research.md D-615).
 * Re-subscription still runs the DAO on io, because `retryWhen` re-collects the upstream with its
 * `flowOn` included.
 *
 * [name] is a fixed label, never anything from a query: it goes to the log.
 */
internal fun <T> Flow<T>.recoverReads(
    fallback: T,
    name: String,
    crashReporter: CrashReporter,
    delays: List<Long> = READ_RETRY_DELAYS,
): Flow<T> = flow {
    var consecutiveFailures = 0
    emitAll(
        this@recoverReads
            .onEach { consecutiveFailures = 0 }
            .retryWhen { cause, _ ->
                if (cause is CancellationException) throw cause
                crashReporter.recordNonFatal(cause)
                emit(fallback)
                val wait = delays.getOrNull(consecutiveFailures)
                if (wait == null) {
                    crashReporter.log("reads: $name gave up after ${delays.size} retries")
                    false
                } else {
                    consecutiveFailures++
                    crashReporter.log("reads: $name failed: ${cause.javaClass.simpleName}, retry in ${wait}ms")
                    delay(wait)
                    true
                }
            }
            // Already reported and the fallback already emitted: the flow ends, it does not fail.
            .catch { cause -> if (cause is CancellationException) throw cause },
    )
}
