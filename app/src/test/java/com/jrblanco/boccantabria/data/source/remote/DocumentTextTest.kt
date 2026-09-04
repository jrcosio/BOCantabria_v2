package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.PdfCorpus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What goes into one request.
 *
 * Feature 009 took the rationing out of here. What used to be a token budget calibrated for another
 * provider's per-minute allowance is now a single guardrail that **no ordinary publication reaches**,
 * so most of these assertions are about the document going in whole (009 FR-001, FR-004, FR-005).
 *
 * The five assertions this test lost were about a token ceiling and a characters-per-token estimate
 * that no longer exist. They were not disabled to go green: the code they measured is gone
 * (009 research.md D-104).
 */
class DocumentTextTest {

    @Test
    fun `a short document goes in whole`() {
        val rendered = DocumentText.render(corpus("Primera pagina.", "Segunda pagina."))

        assertEquals(listOf(1, 2), rendered.pages)
        assertFalse(rendered.isPartial)
        assertTrue(rendered.text.contains("Primera pagina."))
        assertTrue(rendered.text.contains("Segunda pagina."))
    }

    /**
     * 009 FR-001 and SC-001. The whole point of the feature: a document that the previous provider
     * would have read in part now goes in entire, so nothing warns about coverage.
     */
    @Test
    fun `an ordinary twenty-page publication goes in whole`() {
        val rendered = DocumentText.render(corpus(*Array(20) { "Texto de la pagina. ".repeat(120) }))

        assertEquals((1..20).toList(), rendered.pages)
        assertFalse("una publicacion ordinaria no puede ser parcial", rendered.isPartial)
    }

    @Test
    fun `every sent page carries its marker`() {
        val rendered = DocumentText.render(corpus("Uno", "Dos", "Tres"))

        assertTrue(rendered.text.contains("[PÁGINA 1]"))
        assertTrue(rendered.text.contains("[PÁGINA 2]"))
        assertTrue(rendered.text.contains("[PÁGINA 3]"))
    }

    /** The guardrail. It is what stops a pathological document from taking the request down. */
    @Test
    fun `the character ceiling is never crossed`() {
        val rendered = DocumentText.render(corpus(*Array(200) { "x".repeat(3_000) }))

        assertTrue(
            "se enviaron ${rendered.text.length} caracteres",
            rendered.text.length <= DocumentText.MAX_CHARACTERS,
        )
    }

    /**
     * Whole pages, because a page reference only means something if the page went in entire. Half a
     * page produces citations that cannot be checked against the document.
     */
    @Test
    fun `pages are taken whole and in order`() {
        val rendered = DocumentText.render(corpus(*Array(200) { "y".repeat(3_000) }))

        assertEquals((1..rendered.pages.size).toList(), rendered.pages)
        rendered.pages.forEach { page ->
            assertTrue(rendered.text.contains("[PÁGINA $page]"))
        }
    }

    @Test
    fun `a document that does not fit is reported as partial`() {
        val rendered = DocumentText.render(corpus(*Array(200) { "z".repeat(3_000) }))

        assertTrue(rendered.isPartial)
        assertTrue("no cabrian las 200 paginas", rendered.pages.size < 200)
    }

    /** 009 FR-005: the one case where cutting inside a page is allowed. */
    @Test
    fun `a first page that does not fit on its own is cut at a paragraph boundary`() {
        val paragraphs = (1..15_000).joinToString("\n\n") { "Parrafo numero $it con texto." }
        val rendered = DocumentText.render(corpus(paragraphs))

        assertEquals(listOf(1), rendered.pages)
        assertTrue(rendered.isPartial)
        assertTrue(rendered.text.length <= DocumentText.MAX_CHARACTERS)
        assertTrue("debe cortar en un limite natural", rendered.text.trimEnd().endsWith("."))
    }

    @Test
    fun `a single page that fits is not reported as partial`() {
        val rendered = DocumentText.render(corpus("Una sola pagina que cabe de sobra."))

        assertFalse(rendered.isPartial)
        assertEquals(listOf(1), rendered.pages)
    }

    /**
     * A near-empty page costs its marker and nothing else. Keeping the run contiguous is what lets
     * coverage mean «pages 1 to N of M» instead of a list with holes in it.
     */
    @Test
    fun `a blank page is still sent so the run stays contiguous`() {
        val rendered = DocumentText.render(corpus("Primera", " ", "Tercera"))

        assertEquals(listOf(1, 2, 3), rendered.pages)
        assertFalse(rendered.isPartial)
    }

    /** No clock, no randomness: the same document always produces the same request. */
    @Test
    fun `rendering is deterministic`() {
        val corpus = corpus(*Array(200) { "w".repeat(3_000) })

        val first = DocumentText.render(corpus)
        val second = DocumentText.render(corpus)

        assertEquals(first.text, second.text)
        assertEquals(first.pages, second.pages)
    }

    private fun corpus(vararg pages: String) = PdfCorpus(
        externalKey = "boc:439765",
        pdfSha256 = "a".repeat(64),
        totalPages = pages.size,
        pages = pages.mapIndexed { index, text -> PdfCorpus.PdfPageText(index + 1, text) },
    )
}
