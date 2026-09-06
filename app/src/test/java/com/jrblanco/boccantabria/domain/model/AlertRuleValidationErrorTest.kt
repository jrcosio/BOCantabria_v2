package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Three things stop a draft from being saved, and each has a sentence in the interface. */
class AlertRuleValidationErrorTest {

    @Test
    fun `there are exactly three reasons`() {
        assertEquals(
            listOf("NAME_BLANK", "NAME_TOO_LONG", "NO_CRITERIA"),
            AlertRuleValidationError.entries.map { it.name },
        )
    }
}
