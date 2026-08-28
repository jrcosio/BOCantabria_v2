package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import com.jrblanco.boccantabria.domain.model.ParserWarning
import com.jrblanco.boccantabria.domain.model.Publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The two promises this class makes, checked case by case: the source decides the
 * classification, and untidy input is never a reason to lose an announcement.
 */
class PublicationNormalizerTest {

    private val normalizer = PublicationNormalizer()

    private val feed1 = BocFeedCatalog.byFeedId("6802081")!!   // 1, sin subsección
    private val feed22 = BocFeedCatalog.byFeedId("6802085")!!  // 2.2
    private val feed43 = BocFeedCatalog.byFeedId("6802091")!!  // 4.3, el anómalo
    private val feed71 = BocFeedCatalog.byFeedId("6802097")!!  // 7.1

    // ---------- Depth of `categorias` ----------

    @Test
    fun `three components - section, organisation and edition`() {
        val publication = accept(
            categories = "3.Contratación Administrativa|Junta Vecinal de Cosío|ORD",
            definition = BocFeedCatalog.byFeedId("6802087")!!,
        )

        assertEquals(listOf("Junta Vecinal de Cosío"), publication.organizationPath)
        assertEquals(EditionType.ORDINARY, publication.editionType)
        assertTrue(publication.warnings.isEmpty())
    }

    @Test
    fun `four components - section, subsection, organisation and edition`() {
        val publication = accept(
            categories = "4.Economía, Hacienda y Seguridad Social|4.2.Actuaciones en materia Fiscal|" +
                "Ayuntamiento de Rasines|ORD",
            definition = BocFeedCatalog.byFeedId("6802090")!!,
        )

        assertEquals(listOf("Ayuntamiento de Rasines"), publication.organizationPath)
        assertTrue(publication.warnings.isEmpty())
    }

    @Test
    fun `five components - the organisation path keeps its hierarchy`() {
        val publication = accept(
            categories = "2.Autoridades y Personal|2.3.Otros|Consejería de Salud|Secretaría General|ORD",
            definition = BocFeedCatalog.byFeedId("6802086")!!,
        )

        assertEquals(listOf("Consejería de Salud", "Secretaría General"), publication.organizationPath)
        assertEquals("Secretaría General", publication.issuer)
    }

    // ---------- The edition token, anywhere ----------

    @Test
    fun `the edition token at the end is the documented shape and raises nothing`() {
        val publication = accept(categories = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD")

        assertEquals(EditionType.ORDINARY, publication.editionType)
        assertFalse(ParserWarning.CATEGORY_ORDER_UNRELIABLE in publication.warnings)
    }

    @Test
    fun `the edition token at the start is found, and the order is flagged`() {
        val publication = accept(
            categories = "ORD|4.3.Actuaciones en materia de Seguridad Social|" +
                "Ayuntamiento de Limpias|4.Economía, Hacienda y Seguridad Social",
            definition = feed43,
        )

        assertEquals(EditionType.ORDINARY, publication.editionType)
        assertEquals(listOf("Ayuntamiento de Limpias"), publication.organizationPath)
        assertTrue(ParserWarning.CATEGORY_ORDER_UNRELIABLE in publication.warnings)
    }

    @Test
    fun `the edition token in the middle is found, and the order is flagged`() {
        val publication = accept(
            categories = "Ayuntamiento de Miengo|ORD|4.Economía, Hacienda y Seguridad Social|" +
                "4.3.Actuaciones en materia de Seguridad Social",
            definition = feed43,
        )

        assertEquals(EditionType.ORDINARY, publication.editionType)
        assertEquals(listOf("Ayuntamiento de Miengo"), publication.organizationPath)
        assertTrue(ParserWarning.CATEGORY_ORDER_UNRELIABLE in publication.warnings)
    }

    @Test
    fun `an extraordinary edition is recognised`() {
        assertEquals(
            EditionType.EXTRAORDINARY,
            accept(categories = "1.Disposiciones Generales|Gobierno de Cantabria|EXT").editionType,
        )
    }

    @Test
    fun `no edition token means unknown, with a warning, and the announcement is kept`() {
        val publication = accept(categories = "1.Disposiciones Generales|Ayuntamiento de Suances")

        assertEquals(EditionType.UNKNOWN, publication.editionType)
        assertTrue(ParserWarning.EDITION_TYPE_MISSING in publication.warnings)
    }

    // ---------- The source decides ----------

    @Test
    fun `the classification comes from the source, not from the field`() {
        val publication = accept(
            categories = "1.Disposiciones Generales|Ayuntamiento de Val de San Vicente|ORD",
            definition = feed71,
        )

        assertEquals("7", publication.sectionCode)
        assertEquals("7.1", publication.subsectionCode)
        assertTrue(ParserWarning.CATEGORY_DOES_NOT_MATCH_FEED in publication.warnings)
    }

    @Test
    fun `a matching classification raises no warning`() {
        val publication = accept(
            categories = "7.Otros Anuncios|7.1.Urbanismo|Ayuntamiento de Santa María de Cayón|ORD",
            definition = feed71,
        )

        assertTrue(publication.warnings.isEmpty())
    }

    @Test
    fun `the original field is kept verbatim, whatever shape it had`() {
        val raw = "ORD|4.3.Actuaciones en materia de Seguridad Social|Ayuntamiento de Limpias"

        assertEquals(raw, accept(categories = raw, definition = feed43).rawCategories)
    }

    // ---------- Untidy input ----------

    @Test
    fun `empty components are dropped without shifting anything`() {
        val publication = accept(categories = "6.Subvenciones y Ayudas||   |Ayuntamiento de Reocín|ORD")

        assertEquals(listOf("Ayuntamiento de Reocín"), publication.organizationPath)
    }

    @Test
    fun `a trailing separator does not produce an empty organisation`() {
        val publication = accept(categories = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD|")

        assertEquals(listOf("Ayuntamiento de Piélagos"), publication.organizationPath)
    }

    @Test
    fun `no categories at all still yields a publication, classified by its source`() {
        val publication = accept(categories = null, definition = feed22)

        assertEquals("2", publication.sectionCode)
        assertEquals("2.2", publication.subsectionCode)
        assertEquals(EditionType.UNKNOWN, publication.editionType)
        assertTrue(publication.organizationPath.isEmpty())
        assertEquals(
            setOf(ParserWarning.CATEGORIES_ABSENT, ParserWarning.EDITION_TYPE_MISSING),
            publication.warnings,
        )
    }

    @Test
    fun `without an organisation path the issuer falls back to the title prefix`() {
        val publication = accept(
            title = "AYUNTAMIENTO DE PIÉLAGOS: Extracto de la convocatoria de becas.",
            categories = null,
        )

        assertEquals("AYUNTAMIENTO DE PIÉLAGOS", publication.issuer)
    }

    @Test
    fun `a title with no prefix leaves the issuer empty rather than inventing one`() {
        assertNull(accept(title = "Anuncio sin organismo en el título", categories = null).issuer)
    }

    @Test
    fun `the organisation path always wins over the title prefix`() {
        val publication = accept(
            title = "CONSEJERÍA DE SALUD: Resolución.",
            categories = "2.Autoridades y Personal|2.3.Otros|Consejería de Salud|Secretaría General|ORD",
            definition = BocFeedCatalog.byFeedId("6802086")!!,
        )

        assertEquals("Secretaría General", publication.issuer)
    }

    // ---------- Identity ----------

    @Test
    fun `the identifier comes from the link when it carries one`() {
        val publication = accept(
            link = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
        )

        assertEquals("boc:439765", publication.externalKey)
        assertEquals("439765", publication.blobId)
        assertEquals(IdSource.BLOB_ID, publication.idSource)
    }

    @Test
    fun `a link with other parameters still yields its identifier`() {
        val publication = accept(
            link = "https://boc.cantabria.es/boces/verAnuncioAction.do?lang=es&idAnuBlob=439765",
        )

        assertEquals("boc:439765", publication.externalKey)
    }

    @Test
    fun `a distinguishing link without an identifier becomes the key itself`() {
        val link = "https://boc.cantabria.es/boces/otraAccion.do?anuncio=abc"
        val publication = accept(link = link)

        assertEquals(link, publication.externalKey)
        assertNull(publication.blobId)
        assertEquals(IdSource.CANONICAL_URL, publication.idSource)
    }

    @Test
    fun `a bare endpoint falls through to a content digest, so two of them do not collapse`() {
        val bare = "https://boc.cantabria.es/boces/verAnuncioAction.do"
        val first = accept(link = bare, title = "Primer anuncio")
        val second = accept(link = bare, title = "Segundo anuncio")

        assertEquals(IdSource.CONTENT_HASH, first.idSource)
        assertTrue(first.externalKey.startsWith("hash:"))
        assertTrue("dos anuncios distintos comparten clave", first.externalKey != second.externalKey)
    }

    @Test
    fun `the digest is stable across runs`() {
        val bare = "https://boc.cantabria.es/boces/verAnuncioAction.do"

        assertEquals(accept(link = bare).externalKey, accept(link = bare).externalKey)
    }

    // ---------- Rejections ----------

    @Test
    fun `a blank title is rejected with its reason`() {
        assertEquals(RejectionReason.BLANK_TITLE, reject(title = "   "))
        assertEquals(RejectionReason.BLANK_TITLE, reject(title = null))
    }

    @Test
    fun `a link that is not https is rejected`() {
        assertEquals(
            RejectionReason.INVALID_LINK,
            reject(link = "http://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=1"),
        )
        assertEquals(RejectionReason.INVALID_LINK, reject(link = null))
        assertEquals(RejectionReason.INVALID_LINK, reject(link = "https://"))
    }

    @Test
    fun `a date that is not the documented format is rejected`() {
        assertEquals(RejectionReason.INVALID_DATE, reject(date = "26/08/2026"))
        assertEquals(RejectionReason.INVALID_DATE, reject(date = "Wed, 26 Aug 2026 00:00:00 GMT"))
        assertEquals(RejectionReason.INVALID_DATE, reject(date = "2026-13-01"))
        assertEquals(RejectionReason.INVALID_DATE, reject(date = null))
    }

    // ---------- Titles ----------

    @Test
    fun `a very long title is kept whole`() {
        val long = "AYUNTAMIENTO DE SANTANDER: " + "Aprobación definitiva de la ordenanza ".repeat(30)

        assertEquals(long.trim(), accept(title = long).title)
    }

    @Test
    fun `the date is read as a plain calendar date`() {
        assertEquals(LocalDate.of(2026, 8, 26), accept(date = "2026-08-26").publicationDate)
    }

    // ---------- Helpers ----------

    private fun accept(
        title: String? = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
        link: String? = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
        date: String? = "2026-08-26",
        categories: String? = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD",
        definition: BocFeedDefinition = feed1,
    ): Publication {
        val result = normalizer.normalize(RssItemDto(title, link, date, categories), definition)
        return (result as NormalizationResult.Accepted).publication
    }

    private fun reject(
        title: String? = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
        link: String? = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
        date: String? = "2026-08-26",
        categories: String? = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD",
    ): RejectionReason {
        val result = normalizer.normalize(RssItemDto(title, link, date, categories), feed1)
        return (result as NormalizationResult.Rejected).reason
    }
}
