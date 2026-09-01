package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.telemetry.CrashReporter

/**
 * Keeps what it was told, so a test can assert that a failure was reported instead of swallowed.
 *
 * Shared rather than private to one test class: "the flow survived and somebody was told" is a
 * rule several repositories follow, and two copies of the double would let them drift.
 */
class RecordingCrashReporter : CrashReporter {

    val nonFatals: MutableList<Throwable> = mutableListOf()
    val messages: MutableList<String> = mutableListOf()

    override fun recordNonFatal(throwable: Throwable) {
        nonFatals += throwable
    }

    override fun log(message: String) {
        messages += message
    }
}
