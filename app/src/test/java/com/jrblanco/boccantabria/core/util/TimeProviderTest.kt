package com.jrblanco.boccantabria.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeProviderTest {

    @Test
    fun `the system provider returns a plausible current instant`() {
        val before = System.currentTimeMillis()
        val now = SystemTimeProvider().nowMillis()
        val after = System.currentTimeMillis()

        assertTrue("expected $before <= $now <= $after", now in before..after)
    }

    @Test
    fun `a fixed provider is what makes the staleness rule verifiable`() {
        val fixed = object : TimeProvider {
            override fun nowMillis(): Long = 1_700_000_000_000
        }

        assertEquals(1_700_000_000_000, fixed.nowMillis())
    }
}
