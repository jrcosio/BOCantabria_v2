package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** One channel per cycle, or none (FR-052). */
class AlertDeliveryTest {

    @Test
    fun `there are exactly three ways a cycle ends`() {
        assertEquals(listOf("NONE", "IN_APP", "SYSTEM"), AlertDelivery.entries.map { it.name })
    }
}
