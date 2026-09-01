package com.jrblanco.boccantabria.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The one place where accents stop mattering.
 *
 * Everything the application can find depends on this function agreeing with itself: the stored
 * `search_text` column is written through it and every query is normalised through it too. If it
 * ever changes, the already-written column is out of step with the queries and the whole archive
 * has to be rebuilt — which is why its guarantees are pinned here rather than left implied.
 */
class SearchTextTest {

    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `case is folded`() {
        assertEquals("ayuntamiento de pielagos", SearchText.normalise("AYUNTAMIENTO DE PIÉLAGOS"))
    }

    @Test
    fun `accents are dropped, so the query and the text meet in the middle`() {
        assertEquals("pielagos", SearchText.normalise("Piélagos"))
        assertEquals("pielagos", SearchText.normalise("pielagos"))
        assertEquals("contratacion administrativa", SearchText.normalise("Contratación administrativa"))
        assertEquals("economia hacienda", SearchText.normalise("Economía, Hacienda").replace(",", ""))
    }

    /**
     * The tilde of `ñ` is a diacritic like any other and it goes with them. That is the behaviour
     * we want: somebody typing `espana` on a keyboard without the key must find `España`, and
     * somebody typing `España` must find the same thing.
     */
    @Test
    fun `the n with tilde becomes a plain n, on both sides`() {
        assertEquals("espana", SearchText.normalise("España"))
        assertEquals("espana", SearchText.normalise("espana"))
        assertEquals(SearchText.normalise("ESPAÑA"), SearchText.normalise("espana"))
    }

    @Test
    fun `surrounding and repeated whitespace is collapsed`() {
        assertEquals("pielagos", SearchText.normalise("  Piélagos  "))
        assertEquals(
            "contratacion administrativa",
            SearchText.normalise("Contratación   administrativa"),
        )
        assertEquals("uno dos tres", SearchText.normalise("uno\tdos\ntres"))
    }

    @Test
    fun `nothing in means nothing out, and it never throws`() {
        assertEquals("", SearchText.normalise(""))
        assertEquals("", SearchText.normalise("   "))
        assertEquals("", SearchText.normalise("\n\t "))
    }

    @Test
    fun `normalising twice changes nothing`() {
        val once = SearchText.normalise("AYUNTAMIENTO DE PIÉLAGOS:  Aprobación   definitiva")

        assertEquals(once, SearchText.normalise(once))
    }

    /**
     * The regression that would be invisible in Spain. `lowercase()` without an explicit locale
     * takes the system's, and in Turkish `I` lowercases to a dotless `ı`. The column is written on
     * one day and queried on another, possibly after the phone changed language: if the two runs
     * disagreed, a publication would simply stop being findable.
     */
    @Test
    fun `the result does not depend on the system locale`() {
        val inSpanish = SearchText.normalise("INFORMACIÓN PÚBLICA")

        Locale.setDefault(Locale.forLanguageTag("tr"))
        val inTurkish = SearchText.normalise("INFORMACIÓN PÚBLICA")

        assertEquals(inSpanish, inTurkish)
        assertEquals("informacion publica", inTurkish)
    }

    @Test
    fun `digits and punctuation are left alone`() {
        assertEquals("boc 439765/2026 (100%)", SearchText.normalise("BOC 439765/2026 (100%)"))
        assertTrue(SearchText.normalise("a_b").contains("_"))
    }
}
