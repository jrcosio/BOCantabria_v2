package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.domain.model.PdfCorpus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-011. The normaliser exists to free budget for real content, and every rule here is one that
 * would change what the document says if it went one step further.
 */
class PdfTextNormalizerTest {

    private val normalizer = PdfTextNormalizer()

    // ---------- What must survive untouched ----------

    /** A date the normaliser reformatted would be a date the summary got wrong. */
    @Test
    fun `dates are left exactly as written`() {
        val text = normaliseOne("Publicado el 27 de agosto de 2026 y con efectos desde 1/9/2026.")

        assertTrue(text.contains("27 de agosto de 2026"))
        assertTrue(text.contains("1/9/2026"))
    }

    @Test
    fun `amounts keep their separators and their symbol`() {
        val text = normaliseOne("Credito de 1.234.567,89 EUR y una tasa del 21%.")

        assertTrue(text.contains("1.234.567,89 EUR"))
        assertTrue(text.contains("21%"))
    }

    @Test
    fun `numbering and legal references survive`() {
        val text = normaliseOne("Articulo 3.2 b) del Real Decreto-Ley 8/2026, de 14 de mayo.")

        assertTrue(text.contains("Articulo 3.2 b)"))
        assertTrue(text.contains("Real Decreto-Ley 8/2026"))
    }

    /** A relative deadline must reach the model as written; turning it into a date is interpretation. */
    @Test
    fun `a relative deadline is not rewritten`() {
        val text = normaliseOne("Plazo de quince dias habiles desde la publicacion.")

        assertEquals("Plazo de quince dias habiles desde la publicacion.", text)
    }

    // ---------- What gets cleaned ----------

    @Test
    fun `repeated spaces collapse to one`() {
        assertEquals("Ayuntamiento de Pielagos", normaliseOne("Ayuntamiento     de   Pielagos"))
    }

    @Test
    fun `windows line endings become plain ones`() {
        assertEquals("Primera\nSegunda", normaliseOne("Primera\r\nSegunda"))
    }

    @Test
    fun `runs of blank lines collapse`() {
        assertEquals("Primera\n\nSegunda", normaliseOne("Primera\n\n\n\n\nSegunda"))
    }

    @Test
    fun `control characters from the extractor are dropped`() {
        assertEquals("Resolucion", normaliseOne("Reso\u0007lucion"))
    }

    /**
     * **Regression.** A PDF with an unusual font can hand back a UTF-16 surrogate with no pair. It is
     * not a character: encoded into a JSON body it becomes invalid UTF-8 and the service rejects the
     * whole request with a 400 — the same document failing every single time, with nothing on screen
     * to say why. One stray code unit on page four is enough.
     */
    @Test
    fun `a surrogate with no pair is dropped`() {
        val broken = "Resoluci" + "\uD83D" + "on del Ayuntamiento"

        val text = normaliseOne(broken)

        assertEquals("Resolucion del Ayuntamiento", text)
        assertFalse(text.any(Char::isHighSurrogate))
        assertFalse(text.any(Char::isLowSurrogate))
    }

    @Test
    fun `a lone low surrogate is dropped too`() {
        assertEquals("Ayuntamiento", normaliseOne("Ayunta\uDE00miento"))
    }

    /** But a real pair is a real character, and emoji do appear in scanned signatures. */
    @Test
    fun `a proper surrogate pair survives whole`() {
        val withPair = "Anexo I \uD83D\uDE00 firmado"

        assertEquals(withPair, normaliseOne(withPair))
    }

    // ---------- The delicate rule: hyphens ----------

    /** A word split by the typesetter is one word. */
    @Test
    fun `a word split across lines is joined`() {
        assertEquals("subvencion concedida", normaliseOne("sub-\nvencion concedida"))
    }

    /**
     * `Decreto-Ley` is two words that happen to meet at a line end. Joining them would invent a word
     * that is not in the document, which is the failure this rule exists to avoid.
     */
    @Test
    fun `a compound name is not joined when the next line starts in upper case`() {
        val text = normaliseOne("Real Decreto-\nLey 8/2026")

        assertTrue("no debe fabricarse DecretoLey", text.contains("Decreto-"))
        assertFalse(text.contains("DecretoLey"))
    }

    // ---------- Headers and footers ----------

    @Test
    fun `a line repeated across most pages is removed`() {
        val corpus = normalizer.normalise(
            corpus(
                "BOLETIN OFICIAL DE CANTABRIA\nAprobacion definitiva de la ordenanza",
                "BOLETIN OFICIAL DE CANTABRIA\nDetalle de los importes concedidos",
                "BOLETIN OFICIAL DE CANTABRIA\nRecursos que caben contra la resolucion",
            ),
        )

        corpus.pages.forEach { page ->
            assertFalse(page.text.contains("BOLETIN OFICIAL DE CANTABRIA"))
        }
        assertTrue(corpus.pages[0].text.contains("Aprobacion definitiva"))
    }

    /** Below the threshold it is content that happens to repeat, not boilerplate. */
    @Test
    fun `a line repeated on a minority of pages is kept`() {
        val corpus = normalizer.normalise(
            corpus(
                "Ayuntamiento de Pielagos\nPrimera",
                "Segunda",
                "Tercera",
                "Cuarta",
                "Quinta",
            ),
        )

        assertTrue(corpus.pages[0].text.contains("Ayuntamiento de Pielagos"))
    }

    /** With two pages there is no way to tell boilerplate from content that appears twice. */
    @Test
    fun `nothing is treated as boilerplate in a two page document`() {
        val corpus = normalizer.normalise(corpus("Encabezado\nPrimera", "Encabezado\nSegunda"))

        assertTrue(corpus.pages[0].text.contains("Encabezado"))
        assertTrue(corpus.pages[1].text.contains("Encabezado"))
    }

    // ---------- Pages ----------

    @Test
    fun `pages keep their number and never mix`() {
        val corpus = normalizer.normalise(corpus("Primera pagina", "Segunda pagina", "Tercera pagina"))

        assertEquals(listOf(1, 2, 3), corpus.pages.map(PdfCorpus.PdfPageText::pageNumber))
        assertEquals("Primera pagina", corpus.pages[0].text)
        assertEquals("Segunda pagina", corpus.pages[1].text)
        assertFalse(corpus.pages[0].text.contains("Segunda"))
    }

    @Test
    fun `the document keeps its identity`() {
        val corpus = normalizer.normalise(corpus("Una pagina"))

        assertEquals("boc:439765", corpus.externalKey)
        assertEquals("a".repeat(64), corpus.pdfSha256)
        assertEquals(1, corpus.totalPages)
    }

    private fun normaliseOne(text: String): String =
        normalizer.normalise(corpus(text)).pages.first().text

    private fun corpus(vararg pages: String) = PdfCorpus(
        externalKey = "boc:439765",
        pdfSha256 = "a".repeat(64),
        totalPages = pages.size,
        pages = pages.mapIndexed { index, text -> PdfCorpus.PdfPageText(index + 1, text) },
    )
}
