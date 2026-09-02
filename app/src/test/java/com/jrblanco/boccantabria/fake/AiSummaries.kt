package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AiSummary
import com.jrblanco.boccantabria.domain.model.PdfCorpus

/** Builders for the AI summary tests, so no test has to spell out twelve fields to assert one. */
fun aiSummary(
    plainLanguageSummary: String = "Se aprueba definitivamente la modificacion de la ordenanza.",
    keyPoints: List<AiSummary.ReferencedText> = listOf(
        AiSummary.ReferencedText("Se aprueba la modificacion de la ordenanza", listOf(1)),
    ),
    affectedParties: List<AiSummary.ReferencedText> = emptyList(),
    datesAndDeadlines: List<AiSummary.ReferencedDate> = emptyList(),
    amounts: List<AiSummary.ReferencedAmount> = emptyList(),
    requiredActions: List<AiSummary.RequiredAction> = emptyList(),
    appealsOrClaims: List<AiSummary.ReferencedText> = emptyList(),
    warnings: List<String> = emptyList(),
    coverage: AiSummary.SummaryCoverage = AiSummary.SummaryCoverage(
        pagesAnalyzed = listOf(1),
        totalPages = 1,
        complete = true,
    ),
) = AiSummary(
    documentTitle = "Aprobacion definitiva de la modificacion de la Ordenanza General",
    documentType = "Anuncio",
    issuingBody = "Ayuntamiento de Pielagos",
    plainLanguageSummary = plainLanguageSummary,
    keyPoints = keyPoints,
    affectedParties = affectedParties,
    datesAndDeadlines = datesAndDeadlines,
    amounts = amounts,
    requiredActions = requiredActions,
    appealsOrClaims = appealsOrClaims,
    warnings = warnings,
    coverage = coverage,
)

/**
 * Pages long enough to pass the usable-text threshold, because a fixture of two short lines really
 * would look scanned and the rule would be right to say so.
 */
fun pdfCorpus(
    externalKey: String = "boc:439765",
    pdfSha256: String = "a".repeat(64),
    pages: List<String> = listOf(
        "Aprobacion definitiva de la modificacion de la Ordenanza General de Subvenciones. ".repeat(20),
    ),
) = PdfCorpus(
    externalKey = externalKey,
    pdfSha256 = pdfSha256,
    totalPages = pages.size,
    pages = pages.mapIndexed { index, text -> PdfCorpus.PdfPageText(index + 1, text) },
)

/** A document that is all image: the case that must never reach the service (FR-012). */
fun scannedCorpus(externalKey: String = "boc:439765") = PdfCorpus(
    externalKey = externalKey,
    pdfSha256 = "b".repeat(64),
    totalPages = 3,
    pages = listOf(
        PdfCorpus.PdfPageText(1, "  "),
        PdfCorpus.PdfPageText(2, "3"),
        PdfCorpus.PdfPageText(3, ". ,"),
    ),
)
