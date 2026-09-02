package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.RandomProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqRateLimitCoordinatorTest {

    private val clock = MutableClock()
    private val coordinator = GroqRateLimitCoordinator(clock, NoJitter)

    // ---------- Reading the durations the service sends ----------

    @Test
    fun `it reads seconds with decimals`() {
        assertEquals(7_660L, GroqRateLimitCoordinator.parseDurationMillis("7.66s"))
    }

    @Test
    fun `it reads minutes and seconds together`() {
        assertEquals(179_560L, GroqRateLimitCoordinator.parseDurationMillis("2m59.56s"))
    }

    @Test
    fun `it reads a bare number as seconds`() {
        assertEquals(5_000L, GroqRateLimitCoordinator.parseDurationMillis("5"))
    }

    @Test
    fun `it reads hours`() {
        assertEquals(3_600_000L, GroqRateLimitCoordinator.parseDurationMillis("1h"))
    }

    /** A header we do not understand must not take down a request that otherwise worked. */
    @Test
    fun `an unknown shape is null rather than an exception`() {
        assertNull(GroqRateLimitCoordinator.parseDurationMillis("dentro de un rato"))
        assertNull(GroqRateLimitCoordinator.parseDurationMillis("2m59.56sX"))
        assertNull(GroqRateLimitCoordinator.parseDurationMillis(""))
        assertNull(GroqRateLimitCoordinator.parseDurationMillis(null))
    }

    // ---------- The verdict before going out ----------

    @Test
    fun `with nothing recorded yet it lets the first request through`() {
        assertEquals(QuotaVerdict.Allowed, coordinator.verdict(estimatedTokens = 7_200))
    }

    @Test
    fun `it waits when the minute allowance cannot cover the request`() {
        coordinator.record(
            mapOf(
                GroqRateLimitCoordinator.HEADER_REMAINING_TOKENS to "800",
                GroqRateLimitCoordinator.HEADER_RESET_TOKENS to "42s",
            ),
        )

        val verdict = coordinator.verdict(estimatedTokens = 7_200)

        assertEquals(QuotaVerdict.WaitMinute(42), verdict)
    }

    @Test
    fun `once the minute has passed it lets the request through again`() {
        coordinator.record(
            mapOf(
                GroqRateLimitCoordinator.HEADER_REMAINING_TOKENS to "800",
                GroqRateLimitCoordinator.HEADER_RESET_TOKENS to "42s",
            ),
        )
        clock.advance(43_000)

        assertEquals(QuotaVerdict.Allowed, coordinator.verdict(estimatedTokens = 7_200))
    }

    /** FR-039: the daily allowance is not worth an immediate retry, and is a different answer. */
    @Test
    fun `an exhausted daily allowance is told apart from the minute one`() {
        coordinator.record(
            mapOf(
                GroqRateLimitCoordinator.HEADER_REMAINING_REQUESTS to "0",
                GroqRateLimitCoordinator.HEADER_RESET_REQUESTS to "2h",
            ),
        )

        assertEquals(QuotaVerdict.ExhaustedDay, coordinator.verdict(estimatedTokens = 100))
    }

    @Test
    fun `a small request still fits in what is left of the minute`() {
        coordinator.record(
            mapOf(
                GroqRateLimitCoordinator.HEADER_REMAINING_TOKENS to "800",
                GroqRateLimitCoordinator.HEADER_RESET_TOKENS to "42s",
            ),
        )

        assertEquals(QuotaVerdict.Allowed, coordinator.verdict(estimatedTokens = 500))
    }

    /** Header names arrive in whatever case the server felt like. */
    @Test
    fun `header names are matched ignoring case`() {
        coordinator.record(mapOf("X-RateLimit-Remaining-Tokens" to "0", "X-RateLimit-Reset-Tokens" to "10s"))

        assertEquals(QuotaVerdict.WaitMinute(10), coordinator.verdict(estimatedTokens = 100))
    }

    // ---------- retry-after ----------

    @Test
    fun `retry-after is respected and reported in seconds`() {
        val seconds = coordinator.recordRetryAfter(
            mapOf(GroqRateLimitCoordinator.HEADER_RETRY_AFTER to "13"),
        )

        assertEquals(13L, seconds)
        assertEquals(QuotaVerdict.WaitMinute(13), coordinator.verdict(estimatedTokens = 100))
    }

    @Test
    fun `a missing retry-after is null`() {
        assertNull(coordinator.recordRetryAfter(emptyMap()))
    }

    // ---------- Serialisation and backoff ----------

    /**
     * One summary costs about 7.000 of the 8.000 tokens a minute allows. Two at once would guarantee
     * the second one a 429, so they queue instead.
     */
    @Test
    fun `requests are serialised`() = runTest {
        val order = mutableListOf<String>()

        val first = async {
            coordinator.serialised {
                order += "primera entra"
                kotlinx.coroutines.delay(1_000)
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
        val scattered = GroqRateLimitCoordinator(clock, MaximumJitter)

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
