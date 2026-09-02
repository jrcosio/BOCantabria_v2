package com.jrblanco.boccantabria.domain.model

/**
 * What the application understood of an official document.
 *
 * Every list carries the pages that back it, because in a bulletin a claim without provenance is a
 * claim without support. An **empty list means the document does not say**: it is never padded with
 * "not applicable", and the screen hides the section instead (FR-015).
 *
 * The referenced types are nested rather than top-level for a reason that is enforced, not stylistic:
 * Konsist's eighth rule demands a test file for every top-level domain class, and a carrier of two
 * fields has no behaviour of its own to assert. Same choice `DocumentStatus` and `AppResult` made.
 */
data class AiSummary(
    val documentTitle: String,
    val documentType: String,
    val issuingBody: String,
    val plainLanguageSummary: String,
    val keyPoints: List<ReferencedText>,
    val affectedParties: List<ReferencedText>,
    val datesAndDeadlines: List<ReferencedDate>,
    val amounts: List<ReferencedAmount>,
    val requiredActions: List<RequiredAction>,
    val appealsOrClaims: List<ReferencedText>,
    val warnings: List<String>,
    val coverage: SummaryCoverage,
) {
    init {
        require(plainLanguageSummary.isNotBlank()) {
            "a summary with nothing to say is not a summary"
        }
    }

    /** True when there is no structured section to draw under the card. */
    val hasOnlyPlainSummary: Boolean
        get() = keyPoints.isEmpty() && affectedParties.isEmpty() && datesAndDeadlines.isEmpty() &&
            amounts.isEmpty() && requiredActions.isEmpty() && appealsOrClaims.isEmpty()

    /** Every page cited anywhere in the summary, ordered, without repeats. */
    val citedPages: List<Int>
        get() = (
            keyPoints.flatMap(ReferencedText::pages) +
                affectedParties.flatMap(ReferencedText::pages) +
                datesAndDeadlines.flatMap(ReferencedDate::pages) +
                amounts.flatMap(ReferencedAmount::pages) +
                requiredActions.flatMap(RequiredAction::pages) +
                appealsOrClaims.flatMap(ReferencedText::pages)
            ).distinct().sorted()

    data class ReferencedText(val text: String, val pages: List<Int>)

    data class ReferencedDate(
        val dateOrPeriod: String,
        val description: String,
        val pages: List<Int>,
    )

    data class ReferencedAmount(val amount: String, val concept: String, val pages: List<Int>)

    data class RequiredAction(val action: String, val deadline: String, val pages: List<Int>)

    /**
     * What was actually looked at.
     *
     * This is the type that lets the feature tell the truth about a partial summary, and a partial
     * summary that calls itself complete is worse than no summary at all: it invites trust it has
     * not earned. The `require` below is the **second** line of defence — the first is the validator,
     * which corrects the service's answer before this is ever built (FR-030, SC-012).
     */
    data class SummaryCoverage(
        val pagesAnalyzed: List<Int>,
        val totalPages: Int,
        val complete: Boolean,
    ) {
        init {
            require(totalPages > 0) { "totalPages must be positive, was: $totalPages" }
            require(pagesAnalyzed.all { it in 1..totalPages }) {
                "coverage cannot claim pages the document does not have: $pagesAnalyzed of $totalPages"
            }
            require(!complete || pagesAnalyzed.size == totalPages) {
                "coverage cannot be complete while pages are missing: " +
                    "${pagesAnalyzed.size} of $totalPages"
            }
        }

        val isPartial: Boolean get() = !complete
    }
}
