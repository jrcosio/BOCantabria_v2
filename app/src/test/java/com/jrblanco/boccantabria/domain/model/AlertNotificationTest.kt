package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlertNotificationTest {

    @Test
    fun `one publication, every rule it matched`() {
        val notification = AlertNotification(publication("boc:1"), listOf("Ganadería", "Subvenciones rurales"))

        assertEquals(listOf("Ganadería", "Subvenciones rurales"), notification.ruleNames)
    }

    @Test
    fun `a notification without a rule is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AlertNotification(publication("boc:1"), emptyList()) }
    }
}
