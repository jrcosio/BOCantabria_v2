package com.jrblanco.boccantabria.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomProviderTest {

    @Test
    fun `the default provider stays inside the requested bound`() {
        val provider = DefaultRandomProvider()

        repeat(1_000) {
            val value = provider.nextLong(500)
            assertTrue("out of bounds: $value", value in 0 until 500)
        }
    }

    @Test
    fun `a fixed provider makes the retry jitter deterministic`() {
        val fixed = object : RandomProvider {
            override fun nextLong(bound: Long): Long = bound / 2
        }

        assertEquals(250, fixed.nextLong(500))
    }
}
