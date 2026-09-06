package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class InAppAlertTest {

    @Test
    fun `a single publication from a single rule names the rule`() {
        assertEquals("Ganadería", InAppAlert(1, "Ganadería").ruleName)
    }

    @Test
    fun `two pending alerts become one that counts both and names nobody`() {
        val merged = InAppAlert(1, "Ganadería") + InAppAlert(2, null)

        assertEquals(3, merged.publicationCount)
        assertNull(merged.ruleName)
    }

    @Test
    fun `an alert about nothing is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { InAppAlert(0, null) }
    }
}
