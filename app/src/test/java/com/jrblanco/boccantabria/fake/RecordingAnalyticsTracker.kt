package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker

/** Keeps every event received so tests can assert on what was reported. */
class RecordingAnalyticsTracker : AnalyticsTracker {

    private val _events = mutableListOf<AnalyticsEvent>()
    val events: List<AnalyticsEvent> get() = _events.toList()

    val screenViews: List<String>
        get() = _events
            .filter { it.name == SCREEN_VIEW_EVENT }
            .mapNotNull { it.parameters[AnalyticsEvent.PARAM_SCREEN_NAME] }

    override fun track(event: AnalyticsEvent) {
        _events += event
    }

    override fun trackScreenView(screenName: String) {
        track(
            AnalyticsEvent(
                name = SCREEN_VIEW_EVENT,
                parameters = mapOf(AnalyticsEvent.PARAM_SCREEN_NAME to screenName),
            ),
        )
    }

    companion object {
        const val SCREEN_VIEW_EVENT: String = "screen_view"
    }
}
