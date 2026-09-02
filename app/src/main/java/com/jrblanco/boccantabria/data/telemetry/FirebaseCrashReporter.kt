package com.jrblanco.boccantabria.data.telemetry

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jrblanco.boccantabria.BuildConfig
import com.jrblanco.boccantabria.core.telemetry.CrashReporter

/**
 * The only place in the app allowed to touch [FirebaseCrashlytics].
 *
 * It also echoes to `logcat` **in debug builds only**, and that is a deliberate addition. The first
 * time this feature ran on a real phone, three separate `catch` blocks swallowed whatever the
 * sandboxed PDF process threw and wrote nothing anywhere: the screen showed a generic message and the
 * log showed nothing at all, so there was no way to tell a dead process from a bad file. Reporting to
 * Crashlytics alone is no help while you are holding the phone.
 *
 * **Never the document text and never the credential** — only the kind of failure and where it came
 * from (FR-047, SC-009).
 */
class FirebaseCrashReporter(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {

    override fun recordNonFatal(throwable: Throwable) {
        if (BuildConfig.DEBUG) Log.w(TAG, throwable.javaClass.simpleName, throwable)
        // Fire and forget: the reporter failing must never make things worse than the failure
        // it was reporting.
        runCatching { crashlytics.recordException(throwable) }
    }

    override fun log(message: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, message)
        runCatching { crashlytics.log(message) }
    }

    private companion object {
        const val TAG = "BOC"
    }
}

/** See [firebaseAnalyticsTracker] for why the construction lives here and not in `core.di`. */
fun firebaseCrashReporter(): CrashReporter =
    FirebaseCrashReporter(FirebaseCrashlytics.getInstance())
