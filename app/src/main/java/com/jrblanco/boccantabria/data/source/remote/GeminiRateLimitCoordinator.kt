package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the application inside the service's allowance, counting for itself.
 *
 * ### Why it counts instead of reading
 *
 * The previous provider reported the remaining allowance in the headers of every response — with
 * names misleading enough that feature 007 had to write them down. **This one sends nothing.** So
 * either the application keeps the count or it breaks FR-020, which says not to send requests when
 * it already knows there is no room, and FR-022, which says to tell the short limit apart from the
 * daily one. Without FR-022 the `QuotaDay` state of the tab has no real path to it
 * (009 research.md D-108).
 *
 * ### Two sliding windows
 *
 * Sixty seconds against [REQUESTS_PER_MINUTE], twenty-four hours against [REQUESTS_PER_DAY]. The
 * daily one **slides** rather than resetting at midnight because the provider replenishes in its own
 * time zone, not the phone's: a sliding window never allows more than the provider allows, whatever
 * its reset moment, and needs no assumption about zones. It is more conservative than the real reset
 * in the worst case, and it corrects itself within a day. The sentence the reader sees — «the daily
 * limit has been reached, try tomorrow» — stays true under that rule.
 *
 * ### Not persisted, and that is a decision
 *
 * A process restart forgets the count. Storing it would be work and one more piece of state to
 * protect a limit **a person pressing a button cannot reach**: 1 500 requests in a day is one every
 * fifty-seven seconds for twenty-four hours straight. The minute window, which is what actually
 * guards against a burst — someone hammering «generate again» — does not need to survive a restart,
 * because a restart takes longer than the minute. And the service's own 429 is still the final
 * authority, and **is** remembered for the rest of the process.
 *
 * Requests are serialised: there is never more than one in flight.
 */
class GeminiRateLimitCoordinator(
    private val time: TimeProvider,
    private val random: RandomProvider,
) {
    private val mutex = Mutex()

    private val minuteWindow = ArrayDeque<Long>()
    private val dayWindow = ArrayDeque<Long>()

    private var exhaustedUntilMillis: Long = 0
    private var exhaustionIsDayScale: Boolean = false

    /** One request at a time, across the whole application. */
    suspend fun <T> serialised(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * Asked before going out, so an avoidable 429 never gets sent — and asked **again** before every
     * retry, which is what stops a retry from turning one error into a different one.
     */
    fun verdict(): QuotaVerdict {
        val now = time.nowMillis()
        prune(now)

        if (now < exhaustedUntilMillis) {
            return if (exhaustionIsDayScale) {
                QuotaVerdict.ExhaustedDay
            } else {
                QuotaVerdict.WaitMinute(secondsUntil(exhaustedUntilMillis, now))
            }
        }

        if (dayWindow.size >= REQUESTS_PER_DAY) return QuotaVerdict.ExhaustedDay

        if (minuteWindow.size >= REQUESTS_PER_MINUTE) {
            val oldest = minuteWindow.first()
            return QuotaVerdict.WaitMinute(secondsUntil(oldest + MINUTE_MILLIS, now))
        }

        return QuotaVerdict.Allowed
    }

    /**
     * Counted when the request goes **out**, not when it comes back: what spends the allowance is
     * asking, whatever the answer turns out to be.
     */
    fun recordRequest() {
        val now = time.nowMillis()
        prune(now)
        minuteWindow.addLast(now)
        dayWindow.addLast(now)
    }

    /**
     * A 429 overrides any count of ours.
     *
     * Classified by the **delay it asks for** and not by the text it carries: the service returns the
     * same `RESOURCE_EXHAUSTED` whether what ran out was the minute or the day, its wording changes
     * and is in English, and FR-027 forbids showing the provider's messages anyway — so building on
     * them would mean building on something that cannot be shown. A delay is a number
     * (009 research.md D-109).
     */
    fun recordExhaustion(retryAfterSeconds: Long): QuotaVerdict {
        val now = time.nowMillis()
        prune(now)
        exhaustedUntilMillis = now + retryAfterSeconds * MILLIS_PER_SECOND
        exhaustionIsDayScale =
            retryAfterSeconds > DAY_SCALE_THRESHOLD_SECONDS || dayWindow.size >= REQUESTS_PER_DAY
        return if (exhaustionIsDayScale) {
            QuotaVerdict.ExhaustedDay
        } else {
            QuotaVerdict.WaitMinute(retryAfterSeconds)
        }
    }

    /** Growing wait with a little scatter, so two devices that failed together do not return together. */
    fun backoffMillis(attempt: Int): Long =
        BACKOFF_MILLIS[attempt.coerceIn(0, BACKOFF_MILLIS.lastIndex)] + random.nextLong(JITTER_MILLIS)

    private fun prune(now: Long) {
        while (minuteWindow.isNotEmpty() && now - minuteWindow.first() >= MINUTE_MILLIS) {
            minuteWindow.removeFirst()
        }
        while (dayWindow.isNotEmpty() && now - dayWindow.first() >= DAY_MILLIS) {
            dayWindow.removeFirst()
        }
    }

    private fun secondsUntil(deadline: Long, now: Long): Long =
        (((deadline - now) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtLeast(0)

    companion object {
        /**
         * **Pending confirmation against the provider's own console.**
         *
         * The provider no longer publishes free-plan limits in its documentation; it points at
         * <https://aistudio.google.com/rate-limit> for the project's own figures, which needs the
         * owner's credentials. These are the values documented historically for Flash-Lite models.
         * They are plausible and generous next to the previous provider's 1 000 requests a day, but
         * they are **not confirmed** — see `quickstart.md` §0 bis. Changing either is one line, and
         * the 429 is the final authority whatever they say (009 research.md D-115).
         */
        const val REQUESTS_PER_MINUTE: Int = 30
        const val REQUESTS_PER_DAY: Int = 1_500

        /** Past this delay, what ran out is of daily scale rather than of the minute. */
        const val DAY_SCALE_THRESHOLD_SECONDS: Long = 900

        const val HEADER_RETRY_AFTER = "retry-after"
        const val DEFAULT_RETRY_SECONDS: Long = 60

        private const val MILLIS_PER_SECOND = 1_000L
        private const val MINUTE_MILLIS = 60 * MILLIS_PER_SECOND
        private const val DAY_MILLIS = 24 * 60 * MINUTE_MILLIS
        private val BACKOFF_MILLIS = listOf(1_000L, 2_000L, 4_000L)
        private const val JITTER_MILLIS = 500L

        private val PROTOBUF_SECONDS = Regex("^(\\d+(?:\\.\\d+)?)s$")

        /**
         * Accepts a bare number of seconds and the protobuf duration shape, `56s`.
         *
         * Anything else returns `null` rather than a guess, and it **never throws**: a field we do
         * not understand must not take down a request that otherwise worked. This is a much smaller
         * job than the previous provider's three shapes — `7.66s`, `2m59.56s` and a bare number —
         * because this one is only ever read from a `RetryInfo` or a `retry-after` header.
         */
        fun parseRetryDelaySeconds(raw: String?): Long? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null

            text.toDoubleOrNull()?.let { return it.toLong() }

            val match = PROTOBUF_SECONDS.matchEntire(text) ?: return null
            return match.groupValues[1].toDoubleOrNull()?.toLong()
        }
    }
}

sealed interface QuotaVerdict {
    data object Allowed : QuotaVerdict
    data class WaitMinute(val secondsRemaining: Long) : QuotaVerdict
    data object ExhaustedDay : QuotaVerdict
}
