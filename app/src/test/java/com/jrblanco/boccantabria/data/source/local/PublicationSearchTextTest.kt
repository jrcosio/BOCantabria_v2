package com.jrblanco.boccantabria.data.source.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a publication looks like to a search.
 *
 * The last test here is the one that matters most, and it is not about searching: the output can
 * never be empty, and that is what lets `search_text = ''` mean "this row predates the column" with
 * no flag to store anywhere. Break it and the backfill either loops forever or stops too soon.
 */
class PublicationSearchTextTest {

    @Test
    fun `the title, the issuer and the hierarchy all end up in it`() {
        val text = buildSearchText(
            title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva del presupuesto",
            issuer = "Ayuntamiento de Piélagos",
            organizationPath = listOf("Administración Local", "Ayuntamiento de Piélagos"),
            blobId = "439765",
            sectionName = null,
            subsectionName = null,
        )

        assertTrue(text.contains("aprobacion definitiva del presupuesto"))
        assertTrue(text.contains("ayuntamiento de pielagos"))
        assertTrue(text.contains("administracion local"))
    }

    /** The reference the detail screen shows. Somebody who has the number can paste it. */
    @Test
    fun `the reference is searchable`() {
        val text = buildSearchText(
            title = "Anuncio",
            issuer = null,
            organizationPath = emptyList(),
            blobId = "439765",
            sectionName = null,
            subsectionName = null,
        )

        assertTrue(text.contains("439765"))
    }

    /**
     * The table stores `section_code = "3"`, never the name. Without this, typing `contratacion`
     * would find nothing at all — which is exactly the search somebody would try first.
     */
    @Test
    fun `the section and subsection names are searchable even though the table stores codes`() {
        val text = buildSearchText(
            title = "Anuncio de licitación",
            issuer = "Gobierno de Cantabria",
            organizationPath = emptyList(),
            blobId = null,
            sectionName = "Contratación administrativa",
            subsectionName = "Urbanismo",
        )

        assertTrue(text.contains("contratacion administrativa"))
        assertTrue(text.contains("urbanismo"))
    }

    @Test
    fun `nulls leave no hole and no literal null`() {
        val text = buildSearchText(
            title = "Anuncio",
            issuer = null,
            organizationPath = emptyList(),
            blobId = null,
            sectionName = null,
            subsectionName = null,
        )

        assertEquals("anuncio", text)
        assertFalse(text.contains("null"))
        assertFalse(text.contains("  "))
    }

    @Test
    fun `the output is already normalised`() {
        val text = buildSearchText(
            title = "  AYUNTAMIENTO   DE  PIÉLAGOS  ",
            issuer = null,
            organizationPath = emptyList(),
            blobId = null,
            sectionName = null,
            subsectionName = null,
        )

        assertEquals("ayuntamiento de pielagos", text)
    }

    /**
     * The property the whole backfill rests on. `title` cannot be blank —`Publication` requires
     * it— so no row written by this version can carry an empty `search_text`, and an empty one is
     * therefore proof that the row was written before the column existed.
     */
    @Test
    fun `it is never empty, which is what makes the empty marker trustworthy`() {
        val text = buildSearchText(
            title = "A",
            issuer = null,
            organizationPath = emptyList(),
            blobId = null,
            sectionName = null,
            subsectionName = null,
        )

        assertTrue(text.isNotEmpty())
    }

    /** A derived title adds no match and doubles the column. It is deliberately left out. */
    @Test
    fun `the issuer prefix of the title is not stored a second time`() {
        val text = buildSearchText(
            title = "AYUNTAMIENTO DE SANTOÑA: Bases",
            issuer = "Ayuntamiento de Santoña",
            organizationPath = emptyList(),
            blobId = null,
            sectionName = null,
            subsectionName = null,
        )

        assertEquals(2, text.split("ayuntamiento de santona").size - 1)
    }
}
