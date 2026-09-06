package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlertMatchTest {

    @Test
    fun `a match is a rule, a publication and an instant`() {
        val match = AlertMatch("rule-1", "boc:1", 7L)

        assertEquals("rule-1", match.ruleId)
        assertEquals("boc:1", match.externalKey)
        assertEquals(7L, match.matchedAt)
    }

    @Test
    fun `blank identifiers are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AlertMatch("", "boc:1", 0L) }
        assertThrows(IllegalArgumentException::class.java) { AlertMatch("rule-1", " ", 0L) }
    }
}
