package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.fake.alertRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRuleTest {

    @Test
    fun `a keyword alone is a criterion`() {
        assertTrue(alertRule(keywords = listOf("ganadería")).hasCriteria)
    }

    @Test
    fun `a section alone is a criterion`() {
        assertTrue(alertRule(keywords = emptyList(), sectionCodes = setOf("2.2")).hasCriteria)
    }

    @Test
    fun `an organisation alone is a criterion`() {
        assertTrue(alertRule(keywords = emptyList(), organizationQuery = "Piélagos").hasCriteria)
    }

    /** "Notify me of everything" is never what somebody meant (FR-026). */
    @Test
    fun `a rule without any criterion is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            alertRule(keywords = emptyList(), sectionCodes = emptySet(), organizationQuery = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            alertRule(keywords = emptyList(), organizationQuery = "   ")
        }
    }

    @Test
    fun `more than ten keywords are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            alertRule(keywords = (1..11).map { "palabra$it" })
        }
    }

    @Test
    fun `a blank name or id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { alertRule(name = " ") }
        assertThrows(IllegalArgumentException::class.java) { alertRule(id = "") }
    }

    @Test
    fun `a blank organisation does not count as one`() {
        assertFalse(alertRule(keywords = listOf("x1"), organizationQuery = " ").organizationQuery.isNullOrBlank().not())
    }
}
