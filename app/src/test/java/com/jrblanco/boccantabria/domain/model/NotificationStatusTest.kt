package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Three states, not two: the form acts on one, the banner on another. */
class NotificationStatusTest {

    @Test
    fun `there are exactly three states`() {
        assertEquals(listOf("GRANTED", "NEEDS_REQUEST", "DISABLED"), NotificationStatus.entries.map { it.name })
    }
}
