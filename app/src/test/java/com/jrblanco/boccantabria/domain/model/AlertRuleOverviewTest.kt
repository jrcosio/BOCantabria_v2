package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.fake.alertRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlertRuleOverviewTest {

    @Test
    fun `carries the rule and what its card shows`() {
        val overview = AlertRuleOverview(alertRule(), lastMatchedAt = 5L, matchesToday = 2)

        assertEquals("rule-1", overview.rule.id)
        assertEquals(java.lang.Long.valueOf(5L), overview.lastMatchedAt)
        assertEquals(2, overview.matchesToday)
    }

    @Test
    fun `a negative count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AlertRuleOverview(alertRule(), null, -1) }
    }
}
