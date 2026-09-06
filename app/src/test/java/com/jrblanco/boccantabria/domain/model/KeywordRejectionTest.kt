package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Five ways a keyword is refused, each with a sentence in the interface. */
class KeywordRejectionTest {

    @Test
    fun `there are exactly five reasons`() {
        assertEquals(
            listOf("BLANK", "TOO_SHORT", "TOO_LONG", "DUPLICATE", "LIMIT_REACHED"),
            KeywordRejection.entries.map { it.name },
        )
    }
}
