package com.jrblanco.boccantabria.data.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.io.IOException

/**
 * Pure JVM: the wrapper only delegates, so there is nothing Android-specific to stand up.
 */
class FirebaseCrashReporterTest {

    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
    private val reporter = FirebaseCrashReporter(crashlytics)

    @Test
    fun `delegates non-fatal failures`() {
        val throwable = IOException("offline")

        reporter.recordNonFatal(throwable)

        verify { crashlytics.recordException(throwable) }
    }

    @Test
    fun `delegates log messages`() {
        reporter.log("loading home")

        verify { crashlytics.log("loading home") }
    }

    @Test
    fun `a failure in the crash client never reaches the caller`() {
        every { crashlytics.recordException(any()) } throws IllegalStateException("crashlytics down")
        every { crashlytics.log(any<String>()) } throws IllegalStateException("crashlytics down")

        reporter.recordNonFatal(IOException("offline"))
        reporter.log("loading home")
    }
}
