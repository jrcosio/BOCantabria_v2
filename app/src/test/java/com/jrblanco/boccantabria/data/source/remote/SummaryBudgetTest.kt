package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.PdfCorpus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-027, FR-028 and FR-031. What goes into one request, and what the screen is told about it
 * **before** the quota is spent.
 */
class SummaryBudgetTest {

    @Test
    fun `a short document goes in whole`() {
        val selected = SummaryBudget.select(corpus("Primera pagina.", "Segunda pagina."))

        assertEquals(listOf(1, 2), selected.pages)
        assertFalse(selected.isPartial)
        assertTrue(selected.text.contains("Primera pagina."))
        assertTrue(selected.text.contains("Segunda pagina."))
    }

    @Test
    fun `every sent page carries its marker`() {
        val selected = SummaryBudget.select(corpus("Uno", "Dos", "Tres"))

        assertTrue(selected.text.contains("[PÁGINA 1]"))
        assertTrue(selected.text.contains("[PÁGINA 2]"))
        assertTrue(selected.text.contains("[PÁGINA 3]"))
    }

    /** The guardrail. Going over it is what a 429 is made of. */
    @Test
    fun `the character ceiling is never crossed`() {
        val selected = SummaryBudget.select(corpus(*Array(40) { "x".repeat(2_000) }))

        assertTrue(
            "se enviaron ${selected.text.length} caracteres",
            selected.text.length <= SummaryBudget.MAX_DOCUMENT_CHARACTERS,
        )
    }

    @Test
    fun `the token ceiling is never crossed`() {
        val selected = SummaryBudget.select(corpus(*Array(40) { "x".repeat(2_000) }))

        assertTrue(selected.estimatedTokens <= SummaryBudget.MAX_DOCUMENT_TOKENS)
    }

    /**
     * Whole pages, because a page reference only means something if the page went in entire. Half a
     * page produces citations that cannot be checked against the document.
     */
    @Test
    fun `pages are taken whole and in order`() {
        val selected = SummaryBudget.select(corpus(*Array(20) { "y".repeat(3_000) }))

        assertEquals((1..selected.pages.size).toList(), selected.pages)
        selected.pages.forEach { page ->
            assertTrue(selected.text.contains("[PÁGINA $page]"))
        }
    }

    @Test
    fun `a document that does not fit is reported as partial`() {
        val selected = SummaryBudget.select(corpus(*Array(20) { "z".repeat(3_000) }))

        assertTrue(selected.isPartial)
        assertTrue("no cabrian las 20 paginas", selected.pages.size < 20)
    }

    /** FR-031: the one case where cutting inside a page is allowed. */
    @Test
    fun `a first page that does not fit on its own is cut at a paragraph boundary`() {
        val paragraphs = (1..400).joinToString("\n\n") { "Parrafo numero $it con texto suficiente." }
        val selected = SummaryBudget.select(corpus(paragraphs))

        assertEquals(listOf(1), selected.pages)
        assertTrue(selected.isPartial)
        assertTrue(selected.text.length <= SummaryBudget.MAX_DOCUMENT_CHARACTERS)
        assertTrue("debe cortar en un limite natural", selected.text.trimEnd().endsWith("."))
    }

    @Test
    fun `a single page that fits is not reported as partial`() {
        val selected = SummaryBudget.select(corpus("Una sola pagina que cabe de sobra."))

        assertFalse(selected.isPartial)
        assertEquals(listOf(1), selected.pages)
    }

    /**
     * A near-empty page costs its marker and nothing else. Keeping the run contiguous is what lets
     * coverage mean «pages 1 to N of M» instead of a list with holes in it.
     */
    @Test
    fun `a blank page is still sent so the run stays contiguous`() {
        val selected = SummaryBudget.select(corpus("Primera", " ", "Tercera"))

        assertEquals(listOf(1, 2, 3), selected.pages)
        assertFalse(selected.isPartial)
    }

    /** No clock, no randomness: the same document always produces the same request. */
    @Test
    fun `selection is deterministic`() {
        val corpus = corpus(*Array(20) { "w".repeat(3_000) })

        val first = SummaryBudget.select(corpus)
        val second = SummaryBudget.select(corpus)

        assertEquals(first.text, second.text)
        assertEquals(first.pages, second.pages)
    }

    @Test
    fun `the estimate is conservative and never zero for real text`() {
        assertEquals(0, SummaryBudget.estimateTokens(""))
        assertTrue(SummaryBudget.estimateTokens("a".repeat(320)) >= 100)
    }

    private fun corpus(vararg pages: String) = PdfCorpus(
        externalKey = "boc:439765",
        pdfSha256 = "a".repeat(64),
        totalPages = pages.size,
        pages = pages.mapIndexed { index, text -> PdfCorpus.PdfPageText(index + 1, text) },
    )
}
