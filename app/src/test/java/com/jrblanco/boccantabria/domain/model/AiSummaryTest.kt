package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSummaryTest {

    @Test(expected = IllegalArgumentException::class)
    fun `a summary with a blank plain language text is rejected`() {
        summary(plain = "   ")
    }

    /**
     * FR-022: the second line of defence. The validator corrects the service's answer first; this
     * makes sure no other path — a read from storage, a badly written test — can slip a summary
     * through that cites a page the document does not have.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `coverage cannot claim a page the document does not have`() {
        AiSummary.SummaryCoverage(pagesAnalyzed = listOf(1, 40), totalPages = 12, complete = false)
    }

    /**
     * FR-030 and SC-012, the one that matters most: a partial summary calling itself complete is
     * worse than no summary, because it invites trust it has not earned.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `coverage cannot be complete while pages are missing`() {
        AiSummary.SummaryCoverage(pagesAnalyzed = listOf(1, 2), totalPages = 14, complete = true)
    }

    @Test
    fun `coverage over every page may be complete`() {
        val coverage = AiSummary.SummaryCoverage(listOf(1, 2, 3), totalPages = 3, complete = true)

        assertFalse(coverage.isPartial)
    }

    @Test
    fun `coverage over part of the document is partial`() {
        val coverage = AiSummary.SummaryCoverage(listOf(1, 2), totalPages = 14, complete = false)

        assertTrue(coverage.isPartial)
    }

    /** FR-015: an empty list means the document does not say so, and the screen hides the section. */
    @Test
    fun `a summary with no structured sections says so`() {
        assertTrue(summary().hasOnlyPlainSummary)
    }

    @Test
    fun `a summary with any structured section does not`() {
        val withDates = summary(
            dates = listOf(AiSummary.ReferencedDate("quince dias habiles", "Alegaciones", listOf(2))),
        )

        assertFalse(withDates.hasOnlyPlainSummary)
    }

    /** What the sources row draws: every page cited anywhere, ordered and without repeats. */
    @Test
    fun `cited pages are gathered ordered and deduplicated`() {
        val rich = summary(
            keyPoints = listOf(AiSummary.ReferencedText("Se aprueba la ordenanza", listOf(3, 1))),
            dates = listOf(AiSummary.ReferencedDate("15 dias", "Alegaciones", listOf(2, 3))),
            amounts = listOf(AiSummary.ReferencedAmount("12.000 euros", "Credito", listOf(1))),
        )

        assertEquals(listOf(1, 2, 3), rich.citedPages)
    }

    @Test
    fun `a summary with no references cites no pages`() {
        assertEquals(emptyList<Int>(), summary().citedPages)
    }

    private fun summary(
        plain: String = "Se aprueba definitivamente la modificacion de la ordenanza.",
        keyPoints: List<AiSummary.ReferencedText> = emptyList(),
        dates: List<AiSummary.ReferencedDate> = emptyList(),
        amounts: List<AiSummary.ReferencedAmount> = emptyList(),
    ) = AiSummary(
        documentTitle = "Aprobacion definitiva de la modificacion de la Ordenanza",
        documentType = "Anuncio",
        issuingBody = "Ayuntamiento de Pielagos",
        plainLanguageSummary = plain,
        keyPoints = keyPoints,
        affectedParties = emptyList(),
        datesAndDeadlines = dates,
        amounts = amounts,
        requiredActions = emptyList(),
        appealsOrClaims = emptyList(),
        warnings = emptyList(),
        coverage = AiSummary.SummaryCoverage(listOf(1, 2, 3), totalPages = 3, complete = true),
    )
}
