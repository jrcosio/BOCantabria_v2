package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncCycleOutcomeTest {

    @Test
    fun `nothing delivered means no notifications`() {
        val outcome = SyncCycleOutcome(SyncSummary(), emptyList(), AlertDelivery.NONE)

        assertEquals(AlertDelivery.NONE, outcome.delivery)
    }

    @Test
    fun `a delivery with notifications is consistent`() {
        val notification = AlertNotification(publication("boc:1"), listOf("Ganadería"))

        assertEquals(1, SyncCycleOutcome(SyncSummary(), listOf(notification), AlertDelivery.SYSTEM).notifications.size)
    }

    @Test
    fun `a delivery without notifications, or notifications without a delivery, is rejected`() {
        val notification = AlertNotification(publication("boc:1"), listOf("Ganadería"))

        assertThrows(IllegalArgumentException::class.java) {
            SyncCycleOutcome(SyncSummary(), emptyList(), AlertDelivery.SYSTEM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncCycleOutcome(SyncSummary(), listOf(notification), AlertDelivery.NONE)
        }
    }
}
