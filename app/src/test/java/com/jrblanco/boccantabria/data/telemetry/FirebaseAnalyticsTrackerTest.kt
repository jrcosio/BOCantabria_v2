package com.jrblanco.boccantabria.data.telemetry

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric supplies the real [Bundle] the wrapper builds; MockK stands in for the Firebase
 * client so no test ever contacts the service (FR-015, FR-021).
 *
 * The assertion that matters is the last one: personal data must never leave the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class FirebaseAnalyticsTrackerTest {

    private val firebase = mockk<FirebaseAnalytics>(relaxed = true)
    private val tracker = FirebaseAnalyticsTracker(firebase)

    @Test
    fun `sends the event name and its parameters`() {
        val bundle = slot<Bundle>()
        every { firebase.logEvent(any(), capture(bundle)) } returns Unit

        tracker.track(
            AnalyticsEvent(name = "content_opened", parameters = mapOf("source" to "home")),
        )

        verify { firebase.logEvent("content_opened", any()) }
        assertEquals("home", bundle.captured.getString("source"))
    }

    @Test
    fun `a screen view carries the screen name`() {
        val bundle = slot<Bundle>()
        every { firebase.logEvent(any(), capture(bundle)) } returns Unit

        tracker.trackScreenView("home")

        verify { firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, any()) }
        assertEquals("home", bundle.captured.getString(FirebaseAnalytics.Param.SCREEN_NAME))
    }

    @Test
    fun `never sends personally identifiable parameters`() {
        val bundle = slot<Bundle>()
        every { firebase.logEvent(any(), capture(bundle)) } returns Unit

        tracker.track(
            AnalyticsEvent(
                name = "content_opened",
                parameters = mapOf(
                    "source" to "home",
                    "email" to "alguien@example.com",
                    "phone" to "600000000",
                    "user_id" to "42",
                ),
            ),
        )

        val sent = bundle.captured
        assertEquals("home", sent.getString("source"))
        AnalyticsEvent.SENSITIVE_KEYS.forEach { key ->
            assertNull("'$key' must never be sent to analytics", sent.getString(key))
        }
        assertFalse(sent.keySet().any { it in AnalyticsEvent.SENSITIVE_KEYS })
    }

    @Test
    fun `a failure in the analytics client never reaches the caller`() {
        every { firebase.logEvent(any(), any()) } throws IllegalStateException("analytics down")

        // Fire and forget: a telemetry failure must never take a screen down. If this throws,
        // the test fails.
        tracker.trackScreenView("home")
    }
}
