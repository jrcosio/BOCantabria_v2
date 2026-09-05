package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSummaryStatusTest {

    @Test(expected = IllegalArgumentException::class)
    fun `a countdown cannot run backwards`() {
        AiSummaryStatus.WaitingForQuota(secondsRemaining = -1)
    }

    @Test
    fun `a countdown of zero is legitimate`() {
        assertEquals(0L, AiSummaryStatus.WaitingForQuota(0).secondsRemaining)
    }

    /** The two local phases are distinct so the wait can say which one it is in (FR-004). */
    @Test
    fun `the preparing phases are distinguishable`() {
        val fetching = AiSummaryStatus.Preparing(AiSummaryStatus.Preparing.Phase.FETCHING_DOCUMENT)
        val uploading = AiSummaryStatus.Preparing(AiSummaryStatus.Preparing.Phase.UPLOADING_DOCUMENT)

        assertFalse(fetching == uploading)
    }

    /**
     * The whole document is sent, so there is no fraction to announce and no way to build a state
     * that claims one. What used to be `Generating(6, 14)` cannot be written any more, and that is
     * the point: the partial-reading warning was removed rather than left unreachable
     * (010 data-model.md §5.2).
     */
    @Test
    fun `generating carries the size of the document and nothing about a fraction of it`() {
        val generating = AiSummaryStatus.Generating(totalPages = 14)

        assertEquals(14, generating.totalPages)
    }

    /** A document has at least one page. Zero would mean nothing was fetched. */
    @Test(expected = IllegalArgumentException::class)
    fun `generating refuses a document with no pages`() {
        AiSummaryStatus.Generating(totalPages = 0)
    }

    /**
     * FR-035: a stale summary is still a summary. It is shown, marked, with the option to make it
     * again — the distinction lives in the state, not in whether the row survived.
     */
    @Test
    fun `a stale ready state still carries its summary`() {
        val stale = AiSummaryStatus.Ready(summary(), generatedAtEpochMillis = 1_000L, isStale = true)

        assertTrue(stale.isStale)
        assertEquals("Se aprueba la ordenanza.", stale.summary.plainLanguageSummary)
    }

    @Test
    fun `a fresh ready state is not stale`() {
        assertFalse(AiSummaryStatus.Ready(summary(), 1_000L, isStale = false).isStale)
    }

    @Test
    fun `a failure carries the reason so the screen can say which one it was`() {
        val failed = AiSummaryStatus.Failed(AiSummaryError.UnreadableDocument)

        assertEquals(AiSummaryError.UnreadableDocument, failed.error)
    }

    private fun summary() = AiSummary(
        documentTitle = "Titulo",
        documentType = "Anuncio",
        issuingBody = "Ayuntamiento",
        plainLanguageSummary = "Se aprueba la ordenanza.",
        keyPoints = emptyList(),
        affectedParties = emptyList(),
        datesAndDeadlines = emptyList(),
        amounts = emptyList(),
        requiredActions = emptyList(),
        appealsOrClaims = emptyList(),
        warnings = emptyList(),
        coverage = AiSummary.SummaryCoverage(listOf(1), totalPages = 1, complete = true),
    )
}
