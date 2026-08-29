package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The summary is what lets the screen tell "everything worked", "some sources failed" and
 * "nothing could be reached" apart while all three keep showing content.
 */
class SyncSummaryTest {

    @Test
    fun `summaries accumulate across sources`() {
        val total = SyncSummary(succeededFeeds = 1, insertedItems = 100) +
            SyncSummary(succeededFeeds = 1, insertedItems = 40, updatedItems = 60) +
            SyncSummary(failedFeeds = 1)

        assertEquals(2, total.succeededFeeds)
        assertEquals(1, total.failedFeeds)
        assertEquals(140, total.insertedItems)
        assertEquals(60, total.updatedItems)
        assertEquals(3, total.attemptedFeeds)
    }

    @Test
    fun `every source failing is reported, and it is not the same as being incomplete`() {
        val allDown = SyncSummary(failedFeeds = 19)

        assertTrue(allDown.allFailed)
        assertFalse(allDown.isComplete)
    }

    @Test
    fun `some sources failing is incomplete but not a total failure`() {
        val partial = SyncSummary(succeededFeeds = 17, failedFeeds = 2)

        assertFalse(partial.allFailed)
        assertFalse(partial.isComplete)
    }

    @Test
    fun `a clean run is complete and is not a failure`() {
        val clean = SyncSummary(succeededFeeds = 19, unchangedFeeds = 18, insertedItems = 3)

        assertTrue(clean.isComplete)
        assertFalse(clean.allFailed)
    }

    @Test
    fun `a skipped refresh attempted nothing, so it did not fail`() {
        assertFalse(SyncSummary.SKIPPED.allFailed)
        assertTrue(SyncSummary.SKIPPED.isComplete)
        assertEquals(0, SyncSummary.SKIPPED.attemptedFeeds)
    }

    @Test
    fun `negative counters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SyncSummary(succeededFeeds = -1) }
    }
}
