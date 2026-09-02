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
        val extracting = AiSummaryStatus.Preparing(AiSummaryStatus.Preparing.Phase.EXTRACTING_TEXT)

        assertFalse(fetching == extracting)
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
        val failed = AiSummaryStatus.Failed(AiSummaryError.NoExtractableText)

        assertEquals(AiSummaryError.NoExtractableText, failed.error)
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
