package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The allowance, counted at home.
 *
 * Feature 009 changed where the truth comes from, not what it is for. The previous provider reported
 * the remaining allowance in the headers of every response; this one sends nothing, so either the
 * application counts or it breaks 009 FR-020 and FR-022 (009 research.md D-108).
 *
 * Everything here is deterministic: the clock and the scatter are injected, and no test waits.
 */
class GeminiRateLimitCoordinatorTest {

    private val clock = MutableClock()
    private val coordinator = GeminiRateLimitCoordinator(clock, NoJitter)

    // ---------- Reading the delay the service asks for ----------

    @Test
    fun `it reads a bare number as seconds`() {
        assertEquals(56L, GeminiRateLimitCoordinator.parseRetryDelaySeconds("56"))
    }

    /** The protobuf duration shape, which is how a `RetryInfo` carries it. */
    @Test
    fun `it reads the protobuf seconds shape`() {
        assertEquals(56L, GeminiRateLimitCoordinator.parseRetryDelaySeconds("56s"))
        assertEquals(3_600L, GeminiRateLimitCoordinator.parseRetryDelaySeconds("3600s"))
    }

    @Test
    fun `it reads a fractional protobuf duration`() {
        assertEquals(7L, GeminiRateLimitCoordinator.parseRetryDelaySeconds("7.66s"))
    }

    /**
     * A field we do not understand must not take down a request that otherwise worked, and it must
     * not be guessed at either.
     */
    @Test
    fun `an unknown shape is null rather than a guess`() {
        assertNull(GeminiRateLimitCoordinator.parseRetryDelaySeconds("pronto"))
        assertNull(GeminiRateLimitCoordinator.parseRetryDelaySeconds("2m59s"))
        assertNull(GeminiRateLimitCoordinator.parseRetryDelaySeconds(""))
    }

    @Test
    fun `it never throws on a null delay`() {
        assertNull(GeminiRateLimitCoordinator.parseRetryDelaySeconds(null))
    }

    // ---------- The minute window ----------

    @Test
    fun `the first request is allowed`() {
        assertEquals(QuotaVerdict.Allowed, coordinator.verdict())
    }

    @Test
    fun `a full minute window waits, and says how long`() {
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_MINUTE) { coordinator.recordRequest() }

        val verdict = coordinator.verdict()

        assertTrue("debe esperar, no permitir", verdict is QuotaVerdict.WaitMinute)
        assertEquals(60L, (verdict as QuotaVerdict.WaitMinute).secondsRemaining)
    }

    @Test
    fun `the wait shrinks as the oldest request ages`() {
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_MINUTE) { coordinator.recordRequest() }
        clock.advance(50_000)

        val verdict = coordinator.verdict() as QuotaVerdict.WaitMinute

        assertEquals(10L, verdict.secondsRemaining)
    }

    /** The window slides: once the oldest mark falls out, there is room again. */
    @Test
    fun `the minute window replenishes when its oldest mark expires`() {
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_MINUTE) { coordinator.recordRequest() }
        clock.advance(60_000)

        assertEquals(QuotaVerdict.Allowed, coordinator.verdict())
    }

    // ---------- The day window ----------

    /**
     * 009 FR-022. The daily limit is a different sentence to the reader — «try tomorrow» rather than
     * «a few seconds» — and with no headers this count is the only thing that can tell them apart.
     */
    @Test
    fun `a full day window is exhausted rather than waiting`() {
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_DAY) {
            coordinator.recordRequest()
            // Spread them out so the minute window is never what trips first.
            clock.advance(3_000)
        }

        assertEquals(QuotaVerdict.ExhaustedDay, coordinator.verdict())
    }

    /**
     * Sliding rather than resetting at midnight: the provider replenishes in its own time zone, not
     * the phone's, and a sliding window never allows more than it allows.
     */
    @Test
    fun `the day window slides over twenty-four hours`() {
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_DAY) {
            coordinator.recordRequest()
            clock.advance(3_000)
        }
        assertEquals(QuotaVerdict.ExhaustedDay, coordinator.verdict())

        clock.advance(24 * 60 * 60 * 1_000L)

        assertEquals(QuotaVerdict.Allowed, coordinator.verdict())
    }

    // ---------- What a 429 says ----------

    /**
     * 009 research.md D-109. The service returns the same `RESOURCE_EXHAUSTED` whichever allowance
     * ran out, so it is classified by the **delay it asks for**: a number, which does not depend on
     * the provider's wording or language.
     */
    @Test
    fun `a short delay is a wait of the minute`() {
        val verdict = coordinator.recordExhaustion(retryAfterSeconds = 30)

        assertEquals(QuotaVerdict.WaitMinute(30), verdict)
        assertEquals(QuotaVerdict.WaitMinute(30), coordinator.verdict())
    }

    @Test
    fun `a delay of daily scale is exhaustion of the day`() {
        val verdict = coordinator.recordExhaustion(
            retryAfterSeconds = GeminiRateLimitCoordinator.DAY_SCALE_THRESHOLD_SECONDS + 1,
        )

        assertEquals(QuotaVerdict.ExhaustedDay, verdict)
        assertEquals(QuotaVerdict.ExhaustedDay, coordinator.verdict())
    }

    /** Our own count can promote a short delay: if the day is already spent, it is spent. */
    @Test
    fun `a short delay is still the day when our own count says the day is spent`() {
        repeat(GeminiRateLimitCoordinator.REQUESTS_PER_DAY) {
            coordinator.recordRequest()
            clock.advance(3_000)
        }

        assertEquals(QuotaVerdict.ExhaustedDay, coordinator.recordExhaustion(retryAfterSeconds = 20))
    }

    /** The service's word is remembered, and then it expires like anything else. */
    @Test
    fun `an exhaustion expires once its delay has passed`() {
        coordinator.recordExhaustion(retryAfterSeconds = 30)
        clock.advance(30_000)

        assertEquals(QuotaVerdict.Allowed, coordinator.verdict())
    }

    // ---------- Serialising, and backing off ----------

    /** One summary at a time across the whole application. */
    @Test
    fun `requests are serialised`() = runTest {
        val order = mutableListOf<String>()

        val first = async {
            coordinator.serialised {
                order += "primera entra"
                order += "primera sale"
            }
        }
        val second = async {
            coordinator.serialised { order += "segunda entra" }
        }
        first.await()
        second.await()

        assertEquals(listOf("primera entra", "primera sale", "segunda entra"), order)
    }

    @Test
    fun `the backoff grows and is bounded`() {
        assertEquals(1_000L, coordinator.backoffMillis(0))
        assertEquals(2_000L, coordinator.backoffMillis(1))
        assertEquals(4_000L, coordinator.backoffMillis(2))
        assertEquals(4_000L, coordinator.backoffMillis(9))
    }

    @Test
    fun `the backoff carries scatter so two devices do not return together`() {
        val scattered = GeminiRateLimitCoordinator(clock, MaximumJitter)

        assertTrue(scattered.backoffMillis(0) > 1_000L)
    }

    private class MutableClock(private var now: Long = 1_700_000_000_000L) : TimeProvider {
        override fun nowMillis(): Long = now
        fun advance(millis: Long) { now += millis }
    }

    private object NoJitter : RandomProvider {
        override fun nextLong(bound: Long): Long = 0
    }

    private object MaximumJitter : RandomProvider {
        override fun nextLong(bound: Long): Long = bound - 1
    }
}
