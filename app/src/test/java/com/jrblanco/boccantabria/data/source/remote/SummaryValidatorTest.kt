package com.jrblanco.boccantabria.data.source.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-022, FR-030, FR-036 and SC-012. The strict schema guarantees the shape of the answer; this is
 * what guarantees it does not lie about what it read.
 */
class SummaryValidatorTest {

    private val validator = SummaryValidator()

    // ---------- References ----------

    @Test
    fun `a page the document does not have is dropped from the reference`() {
        val corrected = validator.validate(
            raw = payload(keyPoints = listOf(ReferencedTextDto("Se aprueba", listOf(1, 40)))),
            document = sent(listOf(1, 2, 3)),
            totalPages = 3,
        )

        assertEquals(listOf(1), corrected!!.keyPoints.single().pages)
    }

    /** A real page that was never sent cannot back anything either: the model did not read it. */
    @Test
    fun `a real page that was not sent is dropped too`() {
        val corrected = validator.validate(
            raw = payload(keyPoints = listOf(ReferencedTextDto("Se aprueba", listOf(1, 3)))),
            document = sent(listOf(1, 2)),
            totalPages = 3,
        )

        assertEquals(listOf(1), corrected!!.keyPoints.single().pages)
    }

    /**
     * Losing the claim would be worse than losing its citation, and the summary still says which
     * pages it read as a whole.
     */
    @Test
    fun `an element left without any reference keeps its text`() {
        val corrected = validator.validate(
            raw = payload(keyPoints = listOf(ReferencedTextDto("Se aprueba", listOf(40)))),
            document = sent(listOf(1, 2, 3)),
            totalPages = 3,
        )

        assertEquals("Se aprueba", corrected!!.keyPoints.single().text)
        assertEquals(emptyList<Int>(), corrected.keyPoints.single().pages)
    }

    @Test
    fun `references are deduplicated and ordered`() {
        val corrected = validator.validate(
            raw = payload(keyPoints = listOf(ReferencedTextDto("Se aprueba", listOf(3, 1, 3, 2)))),
            document = sent(listOf(1, 2, 3)),
            totalPages = 3,
        )

        assertEquals(listOf(1, 2, 3), corrected!!.keyPoints.single().pages)
    }

    @Test
    fun `every kind of reference is checked, not just the first`() {
        val corrected = validator.validate(
            raw = payload(
                keyPoints = listOf(ReferencedTextDto("uno", listOf(9))),
                affectedParties = listOf(ReferencedTextDto("dos", listOf(9))),
                datesAndDeadlines = listOf(ReferencedDateDto("15 dias", "alegaciones", listOf(9))),
                amounts = listOf(ReferencedAmountDto("100 EUR", "credito", listOf(9))),
                requiredActions = listOf(RequiredActionDto("solicitar", "15 dias", listOf(9))),
                appealsOrClaims = listOf(ReferencedTextDto("recurso", listOf(9))),
            ),
            document = sent(listOf(1, 2)),
            totalPages = 2,
        )!!

        assertTrue(corrected.keyPoints.single().pages.isEmpty())
        assertTrue(corrected.affectedParties.single().pages.isEmpty())
        assertTrue(corrected.datesAndDeadlines.single().pages.isEmpty())
        assertTrue(corrected.amounts.single().pages.isEmpty())
        assertTrue(corrected.requiredActions.single().pages.isEmpty())
        assertTrue(corrected.appealsOrClaims.single().pages.isEmpty())
    }

    // ---------- Entries with nothing in them ----------

    /**
     * **Regression from a real answer.** It came back with `amounts: [{amount: "", concept: "Dotación
     * mensual de cada beca"}]` — the concept without the figure. On screen that is a bullet with a
     * blank where the money should be, which reads as a bug and tells nobody anything.
     *
     * An entry whose own value is missing is dropped. An empty list already means «the document does
     * not say», and that is the honest thing for it to mean here too.
     */
    @Test
    fun `an amount without a figure is dropped rather than drawn blank`() {
        val corrected = validator.validate(
            raw = payload(
                amounts = listOf(
                    ReferencedAmountDto(amount = "", concept = "Dotación mensual", pages = listOf(1)),
                    ReferencedAmountDto(amount = "1.200,00 euros", concept = "Beca", pages = listOf(1)),
                ),
            ),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals(1, corrected.amounts.size)
        assertEquals("1.200,00 euros", corrected.amounts.single().amount)
    }

    @Test
    fun `a point, a party or an appeal with no text is dropped`() {
        val corrected = validator.validate(
            raw = payload(
                keyPoints = listOf(
                    ReferencedTextDto("", listOf(1)),
                    ReferencedTextDto("Se aprueba la ordenanza", listOf(1)),
                ),
                affectedParties = listOf(ReferencedTextDto("   ", listOf(1))),
                appealsOrClaims = listOf(ReferencedTextDto("", listOf(1))),
            ),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals(1, corrected.keyPoints.size)
        assertTrue(corrected.affectedParties.isEmpty())
        assertTrue(corrected.appealsOrClaims.isEmpty())
    }

    @Test
    fun `a date without a period and an action without an action are dropped`() {
        val corrected = validator.validate(
            raw = payload(
                datesAndDeadlines = listOf(
                    ReferencedDateDto("", "Alegaciones", listOf(1)),
                    ReferencedDateDto("15 días hábiles", "Alegaciones", listOf(1)),
                ),
                requiredActions = listOf(RequiredActionDto("", "15 días", listOf(1))),
            ),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals(1, corrected.datesAndDeadlines.size)
        assertTrue(corrected.requiredActions.isEmpty())
    }

    /** A description that is missing is fine: the value is what carries the meaning. */
    @Test
    fun `an entry keeps its value even when the description is empty`() {
        val corrected = validator.validate(
            raw = payload(
                amounts = listOf(ReferencedAmountDto("12.000 euros", concept = "", pages = listOf(1))),
            ),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals("12.000 euros", corrected.amounts.single().amount)
    }

    // ---------- Coverage: the one that matters most ----------

    /** SC-012. What was analysed is what was sent; the service's opinion about it is not evidence. */
    @Test
    fun `coverage is replaced by what was actually sent`() {
        val corrected = validator.validate(
            raw = payload(coverage = CoverageDto(listOf(1, 2, 3, 4, 5), totalPages = 5, complete = true)),
            document = sent(listOf(1, 2, 3)),
            totalPages = 14,
        )

        assertEquals(listOf(1, 2, 3), corrected!!.coverage.pagesAnalyzed)
        assertEquals(14, corrected.coverage.totalPages)
    }

    /** FR-030: corrected to false even though the answer insisted otherwise. */
    @Test
    fun `a partial summary is never allowed to call itself complete`() {
        val corrected = validator.validate(
            raw = payload(coverage = CoverageDto(listOf(1), totalPages = 14, complete = true)),
            document = sent(listOf(1, 2, 3, 4, 5, 6)),
            totalPages = 14,
        )

        assertFalse(corrected!!.coverage.complete)
    }

    @Test
    fun `a summary of the whole document is complete`() {
        val corrected = validator.validate(
            raw = payload(coverage = CoverageDto(emptyList(), totalPages = 0, complete = false)),
            document = sent(listOf(1, 2)),
            totalPages = 2,
        )

        assertTrue(corrected!!.coverage.complete)
        assertEquals(listOf(1, 2), corrected.coverage.pagesAnalyzed)
    }

    /**
     * **Regression, taken from the first real answer the service ever gave.**
     *
     * It came back with `coverage: {pagesAnalyzed: [], totalPages: 1, complete: true}` — complete
     * coverage over an empty list of pages. Without this correction the domain model's own check
     * (`!complete || pagesAnalyzed.size == totalPages`) would have thrown, and the very first
     * summary a person generated would have taken the screen down.
     *
     * The strict schema guarantees the **shape** of an answer, never its **sense**. This is the
     * cheapest possible proof of that.
     */
    @Test
    fun `an answer claiming complete coverage over no pages is corrected, not trusted`() {
        val corrected = validator.validate(
            raw = payload(coverage = CoverageDto(pagesAnalyzed = emptyList(), totalPages = 1, complete = true)),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals(listOf(1), corrected.coverage.pagesAnalyzed)
        assertTrue(corrected.coverage.complete)
        // Y sobre todo: construir el modelo de dominio ya no lanza.
        assertEquals(1, corrected.toDomain().coverage.totalPages)
    }

    /** The same lie, but over a document it did not read whole. */
    @Test
    fun `the same claim over a partial reading is corrected to partial`() {
        val corrected = validator.validate(
            raw = payload(coverage = CoverageDto(pagesAnalyzed = emptyList(), totalPages = 9, complete = true)),
            document = sent(listOf(1, 2)),
            totalPages = 9,
        )!!

        assertEquals(listOf(1, 2), corrected.coverage.pagesAnalyzed)
        assertFalse(corrected.coverage.complete)
        assertTrue(corrected.toDomain().coverage.isPartial)
    }

    // ---------- Prose that arrived cut ----------

    /**
     * **Regression from the first real answers.** Three of the four came back cut mid-word —
     * «…los requisitos de nacionalidad,» — because the prose ran into a ceiling. Showing half a
     * sentence reads as broken, so it is trimmed back to the last one that finished.
     */
    @Test
    fun `prose that stops mid sentence is trimmed to the last complete one`() {
        val corrected = validator.validate(
            raw = payload(
                plain = "El Ayuntamiento aprueba la ordenanza. El documento detalla los requisitos de nacionalidad,",
            ),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals(
            "El Ayuntamiento aprueba la ordenanza.",
            corrected.plainLanguageSummary,
        )
    }

    /** And it is said out loud, because a shortened summary is a summary with something missing. */
    @Test
    fun `trimming the prose is reported in the warnings`() {
        val corrected = validator.validate(
            raw = payload(plain = "Se aprueba la ordenanza. Y el credito asciende a"),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertTrue(corrected.warnings.any { it.contains("incompleto") })
    }

    @Test
    fun `prose that ends properly is left alone`() {
        val whole = "Se aprueba definitivamente la modificacion de la ordenanza."
        val corrected = validator.validate(payload(plain = whole), sent(listOf(1)), 1)!!

        assertEquals(whole, corrected.plainLanguageSummary)
        assertTrue(corrected.warnings.isEmpty())
    }

    @Test
    fun `a question or an ellipsis also count as a finished sentence`() {
        listOf("¿Que se aprueba? Esto.", "Se aprueba lo siguiente…").forEach { whole ->
            val corrected = validator.validate(payload(plain = whole), sent(listOf(1)), 1)!!
            assertEquals(whole, corrected.plainLanguageSummary)
        }
    }

    /**
     * Losing the whole summary would be worse than showing it cut: with nothing that finished, what
     * arrived is kept, and the warning is what tells the reader.
     */
    @Test
    fun `prose with no complete sentence at all is kept rather than emptied`() {
        val fragment = "El Ayuntamiento de Pielagos aprueba definitivamente la modificacion de"
        val corrected = validator.validate(payload(plain = fragment), sent(listOf(1)), 1)!!

        assertEquals(fragment, corrected.plainLanguageSummary)
        assertTrue(corrected.warnings.any { it.contains("incompleto") })
    }

    // ---------- The cap per section ----------

    /**
     * 009 FR-007 and SC-013. The schema asks for the same cap, but **a schema is a request and this
     * is a guarantee**: the service can exceed it, and the card of §20 of the design document is not
     * sized for a hundred and thirty items.
     */
    @Test
    fun `a section with more than ten items is cut down to ten`() {
        val thirteen = (1..13).map { ReferencedTextDto("Punto numero $it", listOf(1)) }

        val corrected = validator.validate(
            raw = payload(keyPoints = thirteen),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals(SummaryValidator.MAX_ITEMS_PER_SECTION, corrected.keyPoints.size)
        assertEquals("debe conservar los primeros, no unos cualesquiera", "Punto numero 1", corrected.keyPoints.first().text)
    }

    /** Discarding in silence is what this refuses to do. */
    @Test
    fun `a section that was cut down says so in the warnings`() {
        val thirteen = (1..13).map { ReferencedTextDto("Punto numero $it", listOf(1)) }

        val corrected = validator.validate(
            raw = payload(keyPoints = thirteen),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertTrue(
            corrected.warnings.toString(),
            corrected.warnings.any { it.contains("puntos clave") },
        )
    }

    @Test
    fun `a section within the cap says nothing`() {
        val corrected = validator.validate(
            raw = payload(keyPoints = (1..10).map { ReferencedTextDto("Punto $it", listOf(1)) }),
            document = sent(listOf(1)),
            totalPages = 1,
        )!!

        assertEquals(10, corrected.keyPoints.size)
        assertTrue(corrected.warnings.isEmpty())
    }

    // ---------- Nothing worth showing ----------

    /** FR-036: not shown, not stored. */
    @Test
    fun `a blank plain language summary is refused`() {
        assertNull(
            validator.validate(payload(plain = "   "), sent(listOf(1)), 1),
        )
    }

    @Test
    fun `an empty answer is refused`() {
        assertNull(validator.validate(SummaryPayload(), sent(listOf(1)), 1))
    }

    /** The corrected payload maps to a domain summary without tripping any of its own checks. */
    @Test
    fun `what survives validation is a valid domain summary`() {
        val corrected = validator.validate(
            raw = payload(
                keyPoints = listOf(ReferencedTextDto("Se aprueba", listOf(1, 99))),
                coverage = CoverageDto(listOf(1, 2, 3), totalPages = 3, complete = true),
            ),
            document = sent(listOf(1, 2)),
            totalPages = 5,
        )!!

        val summary = corrected.toDomain()

        assertEquals(listOf(1, 2), summary.coverage.pagesAnalyzed)
        assertEquals(5, summary.coverage.totalPages)
        assertTrue(summary.coverage.isPartial)
        assertEquals(listOf(1), summary.citedPages)
    }

    private fun payload(
        plain: String = "Se aprueba definitivamente la modificacion de la ordenanza.",
        keyPoints: List<ReferencedTextDto> = emptyList(),
        affectedParties: List<ReferencedTextDto> = emptyList(),
        datesAndDeadlines: List<ReferencedDateDto> = emptyList(),
        amounts: List<ReferencedAmountDto> = emptyList(),
        requiredActions: List<RequiredActionDto> = emptyList(),
        appealsOrClaims: List<ReferencedTextDto> = emptyList(),
        coverage: CoverageDto = CoverageDto(listOf(1), totalPages = 1, complete = true),
    ) = SummaryPayload(
        documentTitle = "Aprobacion definitiva",
        documentType = "Anuncio",
        issuingBody = "Ayuntamiento de Pielagos",
        plainLanguageSummary = plain,
        keyPoints = keyPoints,
        affectedParties = affectedParties,
        datesAndDeadlines = datesAndDeadlines,
        amounts = amounts,
        requiredActions = requiredActions,
        appealsOrClaims = appealsOrClaims,
        warnings = emptyList(),
        coverage = coverage,
    )

    /**
     * Exactly what went out, which is the only evidence of what was analysed. The corpus alone could
     * not say, because the guardrail can cut a document short.
     */
    private fun sent(pages: List<Int>) = RenderedDocument(
        text = pages.joinToString("\n\n") { "[PÁGINA $it]\nContenido de la pagina $it." },
        pages = pages,
        isPartial = false,
    )
}
