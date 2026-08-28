package com.jrblanco.boccantabria.data.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jrblanco.boccantabria.core.telemetry.CrashReporter

/** The only place in the app allowed to touch [FirebaseCrashlytics]. */
class FirebaseCrashReporter(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {

    override fun recordNonFatal(throwable: Throwable) {
        // Fire and forget: the reporter failing must never make things worse than the failure
        // it was reporting.
        runCatching { crashlytics.recordException(throwable) }
    }

    override fun log(message: String) {
        runCatching { crashlytics.log(message) }
    }
}

/** See [firebaseAnalyticsTracker] for why the construction lives here and not in `core.di`. */
fun firebaseCrashReporter(): CrashReporter =
    FirebaseCrashReporter(FirebaseCrashlytics.getInstance())
