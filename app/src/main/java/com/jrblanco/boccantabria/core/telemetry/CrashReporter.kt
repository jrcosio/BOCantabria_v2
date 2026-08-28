package com.jrblanco.boccantabria.core.telemetry

/**
 * Reports crashes and non-fatal failures.
 *
 * Fire and forget: implementations MUST NOT throw or block the caller.
 */
interface CrashReporter {

    fun recordNonFatal(throwable: Throwable)

    fun log(message: String)
}

/** Does nothing. See [NoOpAnalyticsTracker]. */
class NoOpCrashReporter : CrashReporter {
    override fun recordNonFatal(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
}
