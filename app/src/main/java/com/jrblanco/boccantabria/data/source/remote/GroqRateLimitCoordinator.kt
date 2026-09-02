package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the application inside the service's allowance.
 *
 * The headers of each response are **the source of truth**, ahead of any configured value. Their
 * names are misleading enough to be worth writing down, because getting them backwards only shows up
 * once the limit is already broken (research.md D-015):
 *
 * | Header | What it really is |
 * |---|---|
 * | `x-ratelimit-limit-requests` | requests per **day** |
 * | `x-ratelimit-limit-tokens` | tokens per **minute** |
 * | `x-ratelimit-reset-requests` | duration to the daily reset, e.g. `2m59.56s` |
 * | `x-ratelimit-reset-tokens` | duration to the minute reset, e.g. `7.66s` |
 * | `retry-after` | only on 429, and in **whole seconds** |
 *
 * Requests are serialised: there is never more than one in flight. With one summary costing about
 * 7.000 of the 8.000 tokens a minute allows, running two at once would guarantee the second one a
 * 429.
 */
class GroqRateLimitCoordinator(
    private val time: TimeProvider,
    private val random: RandomProvider,
) {
    private val mutex = Mutex()

    private var remainingTokens: Int? = null
    private var tokensResetAtMillis: Long = 0
    private var remainingRequests: Int? = null
    private var requestsResetAtMillis: Long = 0

    /** One request at a time, across the whole application. */
    suspend fun <T> serialised(block: suspend () -> T): T = mutex.withLock { block() }

    /** Asked before going out, so an avoidable 429 never gets sent. */
    fun verdict(estimatedTokens: Int): QuotaVerdict {
        val now = time.nowMillis()

        if (remainingRequests == 0 && now < requestsResetAtMillis) return QuotaVerdict.ExhaustedDay

        val tokensLeft = remainingTokens
        if (tokensLeft != null && tokensLeft < estimatedTokens && now < tokensResetAtMillis) {
            return QuotaVerdict.WaitMinute(secondsUntil(tokensResetAtMillis, now))
        }
        return QuotaVerdict.Allowed
    }

    /** Updated with what the service actually said, after every response. */
    fun record(headers: Map<String, String>) {
        val now = time.nowMillis()
        headers.value(HEADER_REMAINING_TOKENS)?.toIntOrNull()?.let { remainingTokens = it }
        headers.value(HEADER_REMAINING_REQUESTS)?.toIntOrNull()?.let { remainingRequests = it }
        parseDurationMillis(headers.value(HEADER_RESET_TOKENS))?.let { tokensResetAtMillis = now + it }
        parseDurationMillis(headers.value(HEADER_RESET_REQUESTS))?.let {
            requestsResetAtMillis = now + it
        }
    }

    /**
     * `retry-after` arrives on a 429 in whole seconds, and is respected rather than second-guessed:
     * retrying earlier makes the situation worse, not better.
     */
    fun recordRetryAfter(headers: Map<String, String>): Long? {
        val seconds = headers.value(HEADER_RETRY_AFTER)?.trim()?.toLongOrNull() ?: return null
        tokensResetAtMillis = time.nowMillis() + seconds * MILLIS_PER_SECOND
        remainingTokens = 0
        return seconds
    }

    /** Growing wait with a little scatter, so two devices that failed together do not return together. */
    fun backoffMillis(attempt: Int): Long =
        BACKOFF_MILLIS[attempt.coerceIn(0, BACKOFF_MILLIS.lastIndex)] + random.nextLong(JITTER_MILLIS)

    private fun secondsUntil(deadline: Long, now: Long): Long =
        ((deadline - now) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND

    private fun Map<String, String>.value(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    companion object {
        const val HEADER_REMAINING_TOKENS = "x-ratelimit-remaining-tokens"
        const val HEADER_REMAINING_REQUESTS = "x-ratelimit-remaining-requests"
        const val HEADER_RESET_TOKENS = "x-ratelimit-reset-tokens"
        const val HEADER_RESET_REQUESTS = "x-ratelimit-reset-requests"
        const val HEADER_RETRY_AFTER = "retry-after"

        private const val MILLIS_PER_SECOND = 1_000L
        private val BACKOFF_MILLIS = listOf(1_000L, 2_000L, 4_000L)
        private const val JITTER_MILLIS = 500L

        private val DURATION = Regex("(\\d+(?:\\.\\d+)?)([hms])")

        /**
         * Accepts the three shapes the service actually sends: `7.66s`, `2m59.56s`, and a bare
         * number of seconds. An unknown shape returns `null` rather than throwing — a header we do
         * not understand must not take down a request that otherwise worked.
         */
        fun parseDurationMillis(raw: String?): Long? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null

            text.toDoubleOrNull()?.let { return (it * MILLIS_PER_SECOND).toLong() }

            val matches = DURATION.findAll(text).toList()
            if (matches.isEmpty()) return null
            // A shape like "2m59.56sX" is not one we recognise; matching part of it would be a guess.
            if (matches.sumOf { it.value.length } != text.length) return null

            return matches.sumOf { match ->
                val amount = match.groupValues[1].toDouble()
                val factor = when (match.groupValues[2]) {
                    "h" -> 3_600_000.0
                    "m" -> 60_000.0
                    else -> 1_000.0
                }
                (amount * factor).toLong()
            }
        }
    }
}

sealed interface QuotaVerdict {
    data object Allowed : QuotaVerdict
    data class WaitMinute(val secondsRemaining: Long) : QuotaVerdict
    data object ExhaustedDay : QuotaVerdict
}
