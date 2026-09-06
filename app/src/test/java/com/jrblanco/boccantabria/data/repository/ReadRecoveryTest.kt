package com.jrblanco.boccantabria.data.repository

import app.cash.turbine.test
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The operator that keeps a store flow alive across a read failure (feature 014, STAB-004).
 *
 * `catch` runs when the upstream has already terminated; emitting a fallback does not bring a Room
 * flow back. Only re-collecting does, and `retryWhen` re-collects **the same object** — which is why
 * the upstream here counts its subscriptions inside the builder: a mock returning a fresh flow per
 * call could never model "fails, then works".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadRecoveryTest {

    private val crashReporter = RecordingCrashReporter()

    /** A flow whose behaviour depends on which subscription this is. Counted where it matters. */
    private class Upstream<T>(private val behaviour: suspend FlowCollector<T>.(subscription: Int) -> Unit) {
        var subscriptions = 0
            private set
        val flow: Flow<T> = flow { behaviour(++subscriptions) }
    }

    @Test
    fun `a transient failure emits the fallback and recovers after the first delay`() = runTest {
        val upstream = Upstream<List<String>> { subscription ->
            if (subscription == 1) throw IllegalStateException("store busy")
            emit(listOf("boc:1"))
            awaitCancellation() // a live store flow never completes on its own
        }

        upstream.flow.recoverReads(emptyList(), "saved", crashReporter).test {
            assertEquals(emptyList<String>(), awaitItem())
            advanceTimeBy(999)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("boc:1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, upstream.subscriptions)
        assertEquals(1, crashReporter.nonFatals.size)
    }

    @Test
    fun `a success resets the budget`() = runTest {
        // Emits, then breaks — five times over. Each break comes after a success, so each wait is the
        // first of the budget, never an escalation.
        val upstream = Upstream<Int> { subscription ->
            if (subscription <= 5) {
                emit(subscription)
                throw IllegalStateException("flaky")
            }
            emit(99)
            awaitCancellation()
        }

        upstream.flow.recoverReads(0, "unread-count", crashReporter).test {
            repeat(5) { round ->
                assertEquals(round + 1, awaitItem())
                assertEquals(0, awaitItem())
            }
            assertEquals(99, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(6, upstream.subscriptions)
        assertEquals(5, crashReporter.messages.size)
        assertTrue(crashReporter.messages.all { it == "reads: unread-count failed: IllegalStateException, retry in 1000ms" })
    }

    @Test
    fun `a permanent failure stops after three retries and completes with the fallback`() = runTest {
        val upstream = Upstream<List<String>> { throw IllegalStateException("corrupt") }

        upstream.flow.recoverReads(emptyList(), "publications", crashReporter).test {
            repeat(4) { assertEquals(emptyList<String>(), awaitItem()) }
            awaitComplete()
        }

        // One attempt and three retries: a corrupt store is not polled for ever.
        assertEquals(4, upstream.subscriptions)
        assertEquals(4, crashReporter.nonFatals.size)
        assertEquals("reads: publications gave up after 3 retries", crashReporter.messages.last())
    }

    @Test
    fun `an upstream cancellation is rethrown, not reported and not retried`() = runTest {
        val upstream = Upstream<Int> { throw CancellationException("upstream cancelled") }

        val outcome = runCatching { upstream.flow.recoverReads(0, "news", crashReporter).first() }

        assertTrue("debía repropagar la cancelación: $outcome", outcome.exceptionOrNull() is CancellationException)
        assertEquals(1, upstream.subscriptions)
        assertTrue(crashReporter.nonFatals.isEmpty())
    }

    /** Exception transparency: what the collector throws is the collector's, never a read failure. */
    @Test
    fun `an exception thrown by the collector is transparent`() = runTest {
        val upstream = Upstream<Int> {
            emit(1)
            awaitCancellation()
        }

        val outcome = runCatching {
            upstream.flow.recoverReads(0, "rules", crashReporter).collect { throw IllegalStateException("downstream") }
        }

        assertEquals("downstream", outcome.exceptionOrNull()?.message)
        assertEquals(1, upstream.subscriptions)
        assertTrue(crashReporter.nonFatals.isEmpty())
    }

    @Test
    fun `cancelling during a wait stops the retries`() = runTest {
        val upstream = Upstream<Int> { throw IllegalStateException("busy") }
        val job = launch { upstream.flow.recoverReads(0, "issuers", crashReporter).collect {} }
        runCurrent()
        assertEquals(1, upstream.subscriptions)

        job.cancelAndJoin()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, upstream.subscriptions)
    }

    @Test
    fun `the log names the flow and the exception class and nothing else`() = runTest {
        val upstream = Upstream<Int> { subscription ->
            // An exception message can carry a statement, and a statement can carry what somebody
            // typed. None of it may reach the log.
            if (subscription == 1) throw IllegalStateException("SELECT * FROM publications WHERE search_text LIKE '%ganadería%'")
            emit(1)
            awaitCancellation()
        }

        upstream.flow.recoverReads(0, "search", crashReporter).test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("reads: search failed: IllegalStateException, retry in 1000ms"), crashReporter.messages)
    }
}
