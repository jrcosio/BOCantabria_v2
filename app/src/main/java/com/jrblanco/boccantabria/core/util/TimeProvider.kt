package com.jrblanco.boccantabria.core.util

/**
 * The current instant, injected.
 *
 * Same reasoning as [DispatcherProvider]: the cache is considered stale after thirty minutes, and
 * a test that read the system clock could only verify that by waiting. Injecting time makes the
 * rule verifiable in microseconds and keeps the suite deterministic, which principle V demands.
 *
 * Named `TimeProvider` rather than `Clock` so it never gets confused with `java.time.Clock` at
 * the point of use.
 */
interface TimeProvider {

    /** Milliseconds since the epoch. */
    fun nowMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
