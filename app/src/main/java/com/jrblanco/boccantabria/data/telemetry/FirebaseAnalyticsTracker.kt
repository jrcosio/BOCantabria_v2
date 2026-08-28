package com.jrblanco.boccantabria.data.telemetry

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker

/**
 * The only place in the app allowed to touch [FirebaseAnalytics].
 *
 * Sends [AnalyticsEvent.sanitizedParameters] rather than the raw map, so personal data cannot
 * leave the device even if a caller passes it by mistake.
 */
class FirebaseAnalyticsTracker(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsTracker {

    override fun track(event: AnalyticsEvent) {
        send(event.name, event.sanitizedParameters())
    }

    override fun trackScreenView(screenName: String) {
        send(
            name = FirebaseAnalytics.Event.SCREEN_VIEW,
            parameters = mapOf(FirebaseAnalytics.Param.SCREEN_NAME to screenName),
        )
    }

    private fun send(name: String, parameters: Map<String, String>) {
        // Fire and forget: analytics must never be able to take a screen down.
        runCatching {
            firebaseAnalytics.logEvent(
                name,
                bundleOf(*parameters.map { (key, value) -> key to value }.toTypedArray()),
            )
        }
    }
}

/**
 * Builds the tracker from a context.
 *
 * The dependency module lives in `core.di` and must not import the Firebase SDK — that is a
 * layering rule with a test behind it. Constructing it here keeps the SDK where it belongs.
 */
fun firebaseAnalyticsTracker(context: Context): AnalyticsTracker =
    FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(context))
