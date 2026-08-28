package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * The invariants are here and not in the parser on purpose: whatever route a publication takes
 * into the application —network, database, test fixture— it cannot exist in an invalid shape.
 */
class PublicationTest {

    @Test
    fun `a well formed publication is accepted`() {
        val publication = publication()

        assertEquals("boc:439765", publication.externalKey)
        assertEquals(LocalDate.of(2026, 8, 26), publication.publicationDate)
    }

    @Test
    fun `the subsection is the most specific classification when there is one`() {
        assertEquals("2.2", publication(sectionCode = "2", subsectionCode = "2.2").classificationCode)
    }

    @Test
    fun `the section is the classification when there is no subsection`() {
        assertEquals("1", publication(sectionCode = "1", subsectionCode = null).classificationCode)
    }

    @Test
    fun `a blank external key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { publication(externalKey = "  ") }
    }

    @Test
    fun `a blank title is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { publication(title = "") }
    }

    @Test
    fun `a document url that is not https is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            publication(documentUrl = "http://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1")
        }
    }

    @Test
    fun `a blank element inside the organization path is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            publication(organizationPath = listOf("Consejería de Salud", "   "))
        }
    }

    @Test
    fun `a very long title is kept whole`() {
        val long = "AYUNTAMIENTO DE SANTANDER: " + "Aprobación definitiva de la ordenanza ".repeat(20)

        assertEquals(long, publication(title = long).title)
    }

    private fun publication(
        externalKey: String = "boc:439765",
        title: String = "AYUNTAMIENTO DE CAMPOO DE ENMEDIO: Aprobación definitiva.",
        sectionCode: String = "1",
        subsectionCode: String? = null,
        documentUrl: String = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
        organizationPath: List<String> = listOf("Ayuntamiento de Campoo de Enmedio"),
    ) = Publication(
        externalKey = externalKey,
        blobId = "439765",
        idSource = IdSource.BLOB_ID,
        feedId = "6802081",
        sectionCode = sectionCode,
        subsectionCode = subsectionCode,
        title = title,
        issuer = organizationPath.lastOrNull(),
        organizationPath = organizationPath,
        editionType = EditionType.ORDINARY,
        publicationDate = LocalDate.of(2026, 8, 26),
        documentUrl = documentUrl,
        rawCategories = "1.Disposiciones Generales|Ayuntamiento de Campoo de Enmedio|ORD",
    )
}
