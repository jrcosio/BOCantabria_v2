package com.jrblanco.boccantabria.data.source.remote

/**
 * The last gate before anything is shown or stored.
 *
 * The strict schema guarantees the **shape** of the answer, not its **truth**. FR-022, FR-030 and
 * SC-002 cannot depend on the model behaving; coverage is the sharpest case, because a partial
 * summary that calls itself complete invites trust it has not earned (research.md D-018).
 *
 * What it does **not** do is check each claim by looking for it in the text. A summary is a
 * paraphrase, and that check would produce constant false negatives. The guarantee is the prompt,
 * the provenance by page, and being able to open the original.
 *
 * Feature 009 gave it one more job: capping each list at [MAX_ITEMS_PER_SECTION] and saying so. The
 * schema asks for the same cap, but **a schema is a request and this is a guarantee** — and putting
 * it here is what makes it testable without a service (009 FR-007, 009 research.md D-112).
 */
class SummaryValidator {

    /**
     * @param totalPages how many pages the document has, counted **on the device**. It used to
     *   receive the pages that actually went out, because the guardrail could cut a document short;
     *   since feature 010 the whole document is sent, so the set of admissible pages is simply
     *   `1..totalPages`. It matters that this number does not come from the model: not believing the
     *   count it declares is what this whole file is for, and a citation to a page that does not
     *   exist would be a link to nowhere (010 research.md D-205).
     * @return the corrected payload, or `null` when there is nothing worth showing — which the
     *   caller turns into `InvalidResponse`, and neither shows nor stores (FR-019).
     */
    fun validate(raw: SummaryPayload, totalPages: Int): SummaryPayload? {
        if (raw.plainLanguageSummary.isBlank()) return null

        // Every page went out, so every page was submitted. The doctrine has not changed — coverage
        // is still computed from what was **actually sent** and never from what the model claims —
        // it is just that what was sent is now the whole document, so a new summary is always
        // complete. Rows stored before feature 010 can still be partial, and the screen still knows
        // how to say so; that is why the coverage type and its message survive (010 data-model §5.2).
        val sentPages = (1..totalPages).toList()
        val allowed = sentPages.toSet()
        val prose = raw.plainLanguageSummary.trim()
        // The warning follows the *cause*, not the repair: prose with no finished sentence at all is
        // trimmed to nothing and kept as it came, and that case needs saying just as much.
        val arrivedCut = prose.last() !in SENTENCE_ENDINGS

        // Every list is filtered before it is mapped: an entry whose own value is missing would be
        // drawn as a bullet with a blank in it. An empty list already means «the document does not
        // say», and that is the honest thing for it to mean here too.
        val keyPoints = raw.keyPoints
            .filter { it.text.isNotBlank() }
            .map { it.copy(pages = it.pages.keepOnly(allowed)) }
        val affectedParties = raw.affectedParties
            .filter { it.text.isNotBlank() }
            .map { it.copy(pages = it.pages.keepOnly(allowed)) }
        val datesAndDeadlines = raw.datesAndDeadlines
            .filter { it.dateOrPeriod.isNotBlank() }
            .map { it.copy(pages = it.pages.keepOnly(allowed)) }
        val amounts = raw.amounts
            .filter { it.amount.isNotBlank() }
            .map { it.copy(pages = it.pages.keepOnly(allowed)) }
        val requiredActions = raw.requiredActions
            .filter { it.action.isNotBlank() }
            .map { it.copy(pages = it.pages.keepOnly(allowed)) }
        val appealsOrClaims = raw.appealsOrClaims
            .filter { it.text.isNotBlank() }
            .map { it.copy(pages = it.pages.keepOnly(allowed)) }

        // Which sections ran past the cap, so the reader is told rather than quietly shortchanged.
        // Discarding twenty-eight of thirty-eight key points of an official bulletin in silence
        // would be the same half-truth this validator exists to prevent (009 FR-007).
        val capped = listOf(
            SECTION_KEY_POINTS to keyPoints.size,
            SECTION_AFFECTED to affectedParties.size,
            SECTION_DATES to datesAndDeadlines.size,
            SECTION_AMOUNTS to amounts.size,
            SECTION_ACTIONS to requiredActions.size,
            SECTION_APPEALS to appealsOrClaims.size,
        ).filter { (_, size) -> size > MAX_ITEMS_PER_SECTION }.map { (name, _) -> name }

        val notices = buildList {
            addAll(raw.warnings)
            if (arrivedCut) add(TRUNCATED_WARNING)
            if (capped.isNotEmpty()) add(cappedWarning(capped))
        }

        return raw.copy(
            plainLanguageSummary = trimToLastCompleteSentence(prose),
            warnings = notices,
            keyPoints = keyPoints.take(MAX_ITEMS_PER_SECTION),
            affectedParties = affectedParties.take(MAX_ITEMS_PER_SECTION),
            datesAndDeadlines = datesAndDeadlines.take(MAX_ITEMS_PER_SECTION),
            amounts = amounts.take(MAX_ITEMS_PER_SECTION),
            requiredActions = requiredActions.take(MAX_ITEMS_PER_SECTION),
            appealsOrClaims = appealsOrClaims.take(MAX_ITEMS_PER_SECTION),
            coverage = CoverageDto(
                // Replaced, not checked: what was analysed is what was sent, and the service's
                // opinion about it is not evidence.
                pagesAnalyzed = sentPages,
                totalPages = totalPages,
                // Corrected to false when pages are missing, whatever the answer claimed.
                complete = sentPages.size == totalPages,
            ),
        )
    }

    private fun cappedWarning(sections: List<String>): String =
        "El documento sustenta más elementos de los que caben en el resumen. Se han conservado los " +
            "$MAX_ITEMS_PER_SECTION más relevantes de: ${sections.joinToString(", ")}. " +
            "Consulta el documento oficial para el resto."

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

    companion object {
        /**
         * Ten per section.
         *
         * A problem feature 009 created: until then only the first pages that fit were sent, so no
         * card could grow much. The figure sizes the card of §20 of the design document onto a
         * reasonable scroll; it is not a measurement, and raising it is one changed number.
         */
        const val MAX_ITEMS_PER_SECTION: Int = 10

        private const val SECTION_KEY_POINTS = "puntos clave"
        private const val SECTION_AFFECTED = "a quién afecta"
        private const val SECTION_DATES = "fechas y plazos"
        private const val SECTION_AMOUNTS = "importes"
        private const val SECTION_ACTIONS = "actuaciones exigidas"
        private const val SECTION_APPEALS = "recursos"

        private val SENTENCE_ENDINGS = charArrayOf('.', '!', '?', '\u2026', '"', '\u00BB')

        private const val TRUNCATED_WARNING =
            "El resumen llegó incompleto del servicio y se ha recortado hasta la última frase " +
                "terminada. Consulta el documento oficial para el texto completo."
    }
}
