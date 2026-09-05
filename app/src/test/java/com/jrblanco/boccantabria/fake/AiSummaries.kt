package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AiSummary

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

