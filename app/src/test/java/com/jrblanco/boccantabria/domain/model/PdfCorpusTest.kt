package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCorpusTest {

    @Test
    fun `pages are numbered from one`() {
        val corpus = corpus(page(1, "Texto de la primera pagina con contenido suficiente"))

        assertEquals(1, corpus.pages.first().pageNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a page numbered zero is rejected`() {
        PdfCorpus.PdfPageText(pageNumber = 0, text = "algo")
    }

    /** A gap would break every page reference built on top of it. */
    @Test(expected = IllegalArgumentException::class)
    fun `pages with a gap are rejected`() {
        corpus(page(1, "primera"), page(3, "tercera"), totalPages = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pages that do not cover the document are rejected`() {
        corpus(page(1, "solo una"), totalPages = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a document with no pages is rejected`() {
        PdfCorpus(externalKey = "boc:1", pdfSha256 = "a".repeat(64), totalPages = 0, pages = emptyList())
    }

    @Test
    fun `a page below the usable threshold does not count as text`() {
        assertFalse(PdfCorpus.PdfPageText(1, "abc").hasUsableText)
        assertTrue(PdfCorpus.PdfPageText(1, "a".repeat(PdfCorpus.MIN_USABLE_CHARACTERS)).hasUsableText)
    }

    /** Punctuation is not content: a page of dashes and dots is still blank. */
    @Test
    fun `only letters and digits count as usable characters`() {
        assertEquals(0, PdfCorpus.PdfPageText(1, "---- ... ,,,, ;;;;").usableCharacters)
        assertEquals(4, PdfCorpus.PdfPageText(1, "a1-b2").usableCharacters)
    }

    /**
     * FR-012: a scanned document must be recognised by counting, not by waiting for an exception.
     * This is the check that keeps a contextless request from ever reaching the service.
     */
    @Test
    fun `a document of noise has no usable text`() {
        val scanned = corpus(
            page(1, "  "),
            page(2, "3"),
            page(3, ". ,"),
            totalPages = 3,
        )

        assertFalse(scanned.hasUsableText)
    }

    @Test
    fun `a normal bulletin page has usable text`() {
        val real = corpus(
            page(1, "Aprobacion definitiva de la modificacion de la Ordenanza General"),
            page(2, "Contra la presente resolucion cabe interponer recurso contencioso"),
            totalPages = 2,
        )

        assertTrue(real.hasUsableText)
    }

    /**
     * A bulletin may legitimately carry one page that is only a stamp. Half the document blank is
     * where it stops being a legitimate exception.
     */
    @Test
    fun `one blank page among several does not disqualify the document`() {
        // Real bulletin pages carry thousands of characters, not sixty. A fixture of two short
        // lines really would look scanned, and the rule would be right to say so.
        val withStamp = corpus(
            page(1, "Resolucion por la que se aprueba la convocatoria de subvenciones. ".repeat(20)),
            page(2, "Detalle completo de los importes concedidos a cada beneficiario. ".repeat(20)),
            page(3, " "),
            totalPages = 3,
        )

        assertTrue(withStamp.hasUsableText)
        assertEquals(listOf(1, 2), withStamp.pagesWithText.map(PdfCorpus.PdfPageText::pageNumber))
    }

    private fun page(number: Int, text: String) = PdfCorpus.PdfPageText(number, text)

    private fun corpus(vararg pages: PdfCorpus.PdfPageText, totalPages: Int = pages.size) = PdfCorpus(
        externalKey = "boc:439765",
        pdfSha256 = "a".repeat(64),
        totalPages = totalPages,
        pages = pages.toList(),
    )
}
