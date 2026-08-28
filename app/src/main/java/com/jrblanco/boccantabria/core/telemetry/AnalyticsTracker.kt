package com.jrblanco.boccantabria.core.telemetry

/**
 * Records usage events.
 *
 * Fire and forget: implementations MUST NOT throw or block the caller. A telemetry failure can
 * never take a screen down.
 */
interface AnalyticsTracker {

    fun track(event: AnalyticsEvent)

    fun trackScreenView(screenName: String)
}

/**
 * Does nothing. Used until the Firebase implementation is wired in, and by any test that does
 * not care about telemetry.
 */
class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit
    override fun trackScreenView(screenName: String) = Unit
}
