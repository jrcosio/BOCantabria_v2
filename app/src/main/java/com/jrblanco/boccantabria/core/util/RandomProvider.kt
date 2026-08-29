package com.jrblanco.boccantabria.core.util

import kotlin.random.Random

/**
 * Randomness, injected.
 *
 * The retry policy adds jitter so nineteen sources do not come back at the same instant after a
 * failure. Jitter is genuinely random in production and genuinely fixed in tests: without this
 * seam the retry timings would be untestable, and principle V forbids non-deterministic tests.
 */
interface RandomProvider {

    /** A value in `[0, bound)`. */
    fun nextLong(bound: Long): Long
}

class DefaultRandomProvider : RandomProvider {
    override fun nextLong(bound: Long): Long = Random.nextLong(bound)
}
