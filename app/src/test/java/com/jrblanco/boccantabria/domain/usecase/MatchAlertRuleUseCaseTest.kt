package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.fake.alertRule
import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparator, case by case: the sixteen of §24 of the functional document, the eight
 * configurations of its §22, and the literal-text rule.
 */
class MatchAlertRuleUseCaseTest {

    private val matches = MatchAlertRuleUseCase(BocSectionRepositoryImpl())

    private val ganaderia = publication(
        key = "boc:1",
        title = "CONSEJERÍA DE DESARROLLO RURAL: Convocatoria de ayudas para explotaciones ganaderas.",
        issuer = "Consejería de Desarrollo Rural",
        sectionCode = "6",
    ).copy(feedId = "6802095", rawCategories = "6.Subvenciones y Ayudas|Consejería de Desarrollo Rural|ORD")

    // ---------- §24: the comparator ----------

    @Test
    fun `a simple word in the title matches`() {
        assertTrue(matches(alertRule(keywords = listOf("ganaderas")), ganaderia))
    }

    @Test
    fun `case does not matter`() {
        assertTrue(matches(alertRule(keywords = listOf("GANADERAS")), ganaderia))
    }

    @Test
    fun `accents do not matter either way`() {
        assertTrue(matches(alertRule(keywords = listOf("consejeria")), ganaderia))
        assertTrue(matches(alertRule(keywords = listOf("Convocatória")), ganaderia))
    }

    @Test
    fun `a phrase has to be consecutive`() {
        assertTrue(matches(alertRule(keywords = listOf("desarrollo rural")), ganaderia))
        assertFalse(matches(alertRule(keywords = listOf("rural desarrollo")), ganaderia))
    }

    @Test
    fun `any mode matches with one of two`() {
        assertTrue(matches(alertRule(keywords = listOf("ganaderas", "pesca")), ganaderia))
    }

    @Test
    fun `any mode fails with none`() {
        assertFalse(matches(alertRule(keywords = listOf("pesca", "minería")), ganaderia))
    }

    @Test
    fun `all mode matches when every word is there`() {
        assertTrue(matches(alertRule(keywords = listOf("ayudas", "ganaderas"), matchMode = KeywordMatchMode.ALL), ganaderia))
    }

    @Test
    fun `all mode fails when one is missing`() {
        assertFalse(matches(alertRule(keywords = listOf("ayudas", "pesca"), matchMode = KeywordMatchMode.ALL), ganaderia))
    }

    @Test
    fun `a rule by section alone matches that section and no other`() {
        val oposicion = publication(key = "boc:2", sectionCode = "2", subsectionCode = "2.2")
        val nombramiento = publication(key = "boc:3", sectionCode = "2", subsectionCode = "2.1")
        val rule = alertRule(keywords = emptyList(), sectionCodes = setOf("2.2"))

        assertTrue(matches(rule, oposicion))
        assertFalse(matches(rule, nombramiento))
    }

    @Test
    fun `a rule with several sections matches any of them`() {
        val rule = alertRule(keywords = emptyList(), sectionCodes = setOf("2.2", "6"))

        assertTrue(matches(rule, ganaderia))
        assertTrue(matches(rule, publication(key = "boc:2", sectionCode = "2", subsectionCode = "2.2")))
        assertFalse(matches(rule, publication(key = "boc:3", sectionCode = "1")))
    }

    /** Belt and braces: a stored parent code still catches its children. */
    @Test
    fun `a parent code in the rule matches its subsections`() {
        val rule = alertRule(keywords = emptyList(), sectionCodes = setOf("2"))

        assertTrue(matches(rule, publication(key = "boc:2", sectionCode = "2", subsectionCode = "2.3")))
    }

    @Test
    fun `a rule by organisation alone matches a partial normalised name`() {
        val rule = alertRule(keywords = emptyList(), organizationQuery = "pielagos")

        assertTrue(matches(rule, publication(key = "boc:4", issuer = "Ayuntamiento de Piélagos")))
        assertFalse(matches(rule, publication(key = "boc:5", issuer = "Ayuntamiento de Santander")))
    }

    @Test
    fun `the organisation is also looked for in the hierarchy`() {
        val deep = publication(key = "boc:6", issuer = "Servicio de Ganadería").copy(
            organizationPath = listOf("Gobierno de Cantabria", "Consejería de Desarrollo Rural", "Servicio de Ganadería"),
        )

        assertTrue(matches(alertRule(keywords = emptyList(), organizationQuery = "Gobierno de Cantabria"), deep))
    }

    @Test
    fun `word, section and organisation all have to hold`() {
        val complete = alertRule(keywords = listOf("ayudas"), sectionCodes = setOf("6"), organizationQuery = "Desarrollo Rural")

        assertTrue(matches(complete, ganaderia))
        assertFalse(matches(complete.copy(sectionCodes = setOf("1")), ganaderia))
        assertFalse(matches(complete.copy(organizationQuery = "Piélagos"), ganaderia))
        assertFalse(matches(complete.copy(keywords = listOf("pesca")), ganaderia))
    }

    @Test
    fun `a paused rule matches nothing`() {
        assertFalse(matches(alertRule(keywords = listOf("ganaderas"), isEnabled = false), ganaderia))
    }

    @Test
    fun `without categories the title still matches`() {
        assertTrue(matches(alertRule(keywords = listOf("ganaderas")), ganaderia.copy(rawCategories = null)))
    }

    /** The 4.3 feed ships its components permuted; as plain text the order is irrelevant. */
    @Test
    fun `the permuted categories of the 4 3 feed change nothing`() {
        val permuted = publication(key = "boc:7", sectionCode = "4", subsectionCode = "4.3", title = "Resolución sobre cotizaciones.")
            .copy(rawCategories = "ORD|Tesorería General|4.Economía, Hacienda y Seguridad Social|4.3.Actuaciones en materia de Seguridad Social")

        assertTrue(matches(alertRule(keywords = listOf("tesorería")), permuted))
        assertTrue(matches(alertRule(keywords = listOf("seguridad social")), permuted))
    }

    @Test
    fun `the section and subsection names are searchable although the publication does not carry them`() {
        val oposicion = publication(key = "boc:2", sectionCode = "2", subsectionCode = "2.2", title = "Bases de una plaza.")
            .copy(rawCategories = null)

        assertTrue(matches(alertRule(keywords = listOf("oposiciones")), oposicion))
        assertTrue(matches(alertRule(keywords = listOf("autoridades y personal")), oposicion))
    }

    // ---------- FR-027: nothing typed is a pattern ----------

    @Test
    fun `metacharacters are matched literally`() {
        val odd = publication(key = "boc:8", title = "Ayuda del 100% (ayuda) a.b").copy(rawCategories = null)

        assertTrue(matches(alertRule(keywords = listOf("100%")), odd))
        assertTrue(matches(alertRule(keywords = listOf("(ayuda)")), odd))
        assertTrue(matches(alertRule(keywords = listOf("a.b")), odd))
        assertFalse(matches(alertRule(keywords = listOf("a.c")), odd))
    }

    // ---------- §22: the eight configurations ----------

    private val oposicion = publication(key = "boc:20", sectionCode = "2", subsectionCode = "2.2", title = "AYUNTAMIENTO DE SANTOÑA: Bases de la convocatoria.", issuer = "Ayuntamiento de Santoña")
    private val pielagosUrbanismo = publication(key = "boc:21", sectionCode = "7", subsectionCode = "7.1", title = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación del Plan.", issuer = "Ayuntamiento de Piélagos")
    private val santanderUrbanismo = publication(key = "boc:22", sectionCode = "7", subsectionCode = "7.1", title = "AYUNTAMIENTO DE SANTANDER: Aprobación del Plan.", issuer = "Ayuntamiento de Santander")
    private val jovenes = publication(key = "boc:23", sectionCode = "6", title = "Ayudas para jóvenes agricultores.", issuer = "Consejería").copy(rawCategories = null)
    private val cosio = publication(key = "boc:24", sectionCode = "7", subsectionCode = "7.5", title = "JUNTA VECINAL DE COSÍO: Aprobación de cuentas.", issuer = "Junta Vecinal de Cosío")

    @Test
    fun `everything about livestock, any section`() {
        assertTrue(matches(alertRule(keywords = listOf("ganadería")), ganaderia.copy(title = "Ayudas a la ganadería.")))
        assertFalse(matches(alertRule(keywords = listOf("ganadería")), oposicion))
    }

    @Test
    fun `broad rural aid in section six`() {
        val rule = alertRule(keywords = listOf("ganadería", "medio rural"), sectionCodes = setOf("6"))

        assertTrue(matches(rule, ganaderia.copy(title = "Ayudas al medio rural.")))
        assertFalse(matches(rule, oposicion))
    }

    @Test
    fun `aid and youth, both, in section six`() {
        val rule = alertRule(keywords = listOf("ayuda", "jóvenes"), matchMode = KeywordMatchMode.ALL, sectionCodes = setOf("6"))

        assertTrue(matches(rule, jovenes))
        assertFalse(matches(rule, ganaderia))
    }

    @Test
    fun `any competition at all`() {
        val rule = alertRule(keywords = emptyList(), sectionCodes = setOf("2.2"))

        assertTrue(matches(rule, oposicion))
        assertFalse(matches(rule, ganaderia))
    }

    @Test
    fun `everything one town hall publishes`() {
        val rule = alertRule(keywords = emptyList(), organizationQuery = "Ayuntamiento de Piélagos")

        assertTrue(matches(rule, pielagosUrbanismo))
        assertFalse(matches(rule, santanderUrbanismo))
    }

    @Test
    fun `planning in Santander`() {
        val rule = alertRule(keywords = emptyList(), sectionCodes = setOf("7.1"), organizationQuery = "Ayuntamiento de Santander")

        assertTrue(matches(rule, santanderUrbanismo))
        assertFalse(matches(rule, pielagosUrbanismo))
    }

    @Test
    fun `an exact phrase`() {
        assertTrue(matches(alertRule(keywords = listOf("jóvenes agricultores")), jovenes))
        assertFalse(matches(alertRule(keywords = listOf("agricultores jóvenes")), jovenes))
    }

    @Test
    fun `a village name`() {
        assertTrue(matches(alertRule(keywords = listOf("Cosío")), cosio))
        assertTrue(matches(alertRule(keywords = listOf("cosio")), cosio))
        assertFalse(matches(alertRule(keywords = listOf("Cosío")), oposicion))
    }
}
