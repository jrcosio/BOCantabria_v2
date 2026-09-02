package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.PdfCorpus

/**
 * The last gate before anything is shown or stored.
 *
 * The strict schema guarantees the **shape** of the answer, not its **truth**. FR-022, FR-030 and
 * SC-012 cannot depend on the model behaving; coverage is the sharpest case, because a partial
 * summary that calls itself complete invites trust it has not earned (research.md D-018).
 *
 * What it does **not** do is check each claim by looking for it in the text. A summary is a
 * paraphrase, and that check would produce constant false negatives. The guarantee is the prompt,
 * the provenance by page, and being able to open the original.
 */
class SummaryValidator {

    /**
     * @param sentPages exactly the pages that went out, from `SummaryBudget`.
     * @return the corrected payload, or `null` when there is nothing worth showing — which the
     *   caller turns into `InvalidResponse`, and neither shows nor stores (FR-036).
     */
    fun validate(
        raw: GroqSummaryPayload,
        corpus: PdfCorpus,
        sentPages: List<Int>,
    ): GroqSummaryPayload? {
        if (raw.plainLanguageSummary.isBlank()) return null

        val allowed = sentPages.toSet()
        val prose = raw.plainLanguageSummary.trim()
        // The warning follows the *cause*, not the repair: prose with no finished sentence at all is
        // trimmed to nothing and kept as it came, and that case needs saying just as much.
        val arrivedCut = prose.last() !in SENTENCE_ENDINGS

        return raw.copy(
            plainLanguageSummary = trimToLastCompleteSentence(prose),
            warnings = if (arrivedCut) raw.warnings + TRUNCATED_WARNING else raw.warnings,
            // Every list is filtered before it is mapped: an entry whose own value is missing would
            // be drawn as a bullet with a blank in it. An empty list already means «the document does
            // not say», and that is the honest thing for it to mean here too.
            keyPoints = raw.keyPoints
                .filter { it.text.isNotBlank() }
                .map { it.copy(pages = it.pages.keepOnly(allowed)) },
            affectedParties = raw.affectedParties
                .filter { it.text.isNotBlank() }
                .map { it.copy(pages = it.pages.keepOnly(allowed)) },
            datesAndDeadlines = raw.datesAndDeadlines
                .filter { it.dateOrPeriod.isNotBlank() }
                .map { it.copy(pages = it.pages.keepOnly(allowed)) },
            amounts = raw.amounts
                .filter { it.amount.isNotBlank() }
                .map { it.copy(pages = it.pages.keepOnly(allowed)) },
            requiredActions = raw.requiredActions
                .filter { it.action.isNotBlank() }
                .map { it.copy(pages = it.pages.keepOnly(allowed)) },
            appealsOrClaims = raw.appealsOrClaims
                .filter { it.text.isNotBlank() }
                .map { it.copy(pages = it.pages.keepOnly(allowed)) },
            coverage = CoverageDto(
                // Replaced, not checked: what was analysed is what was sent, and the service's
                // opinion about it is not evidence.
                pagesAnalyzed = sentPages,
                totalPages = corpus.totalPages,
                // Corrected to false when pages are missing, whatever the answer claimed.
                complete = sentPages.size == corpus.totalPages,
            ),
        )
    }

    /**
     * Prose that stops mid-word reads as broken, and it happened on three of the first four real
     * answers: «…los requisitos de nacionalidad,». It is trimmed back to the last sentence that
     * finished, and the loss is declared in [TRUNCATED_WARNING].
     *
     * If nothing finished, what arrived is kept: losing the whole summary would be worse than showing
     * it short, and the warning is what tells the reader either way.
     */
    private fun trimToLastCompleteSentence(prose: String): String {
        if (prose.isEmpty() || prose.last() in SENTENCE_ENDINGS) return prose

        val lastEnding = prose.indexOfLast { it in SENTENCE_ENDINGS }
        if (lastEnding < 0) return prose
        return prose.take(lastEnding + 1).trimEnd()
    }

    /**
     * Drops pages the document does not have and pages that were never sent. An element left
     * without a reference keeps its text: losing the claim would be worse than losing its citation,
     * and the summary as a whole still says which pages it read.
     */
    private fun List<Int>.keepOnly(allowed: Set<Int>): List<Int> =
        filter { it in allowed }.distinct().sorted()

    private companion object {
        val SENTENCE_ENDINGS = charArrayOf('.', '!', '?', '\u2026', '"', '\u00BB')

        const val TRUNCATED_WARNING =
            "El resumen llegó incompleto del servicio y se ha recortado hasta la última frase " +
                "terminada. Consulta el documento oficial para el texto completo."
    }
}
