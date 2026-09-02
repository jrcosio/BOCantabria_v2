package com.jrblanco.boccantabria.data.source.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AiPreferencesTest {

    private val dispatcher = StandardTestDispatcher()

    /** FR-043: a fresh installation has not been told anything yet. */
    @Test
    fun `a fresh installation has not accepted the notice`() = runTest(dispatcher) {
        assertFalse(preferences().observeNoticeAccepted().first())
    }

    @Test
    fun `accepting is remembered`() = runTest(dispatcher) {
        val preferences = preferences()

        preferences.acceptNotice()

        assertTrue(preferences.observeNoticeAccepted().first())
    }

    /**
     * FR-045: shown once and never again. Surviving a new instance is the point — the flag has to
     * outlive the object that wrote it, or the sheet would come back on the next launch.
     */
    @Test
    fun `the acceptance survives a new instance over the same store`() = runTest(dispatcher) {
        preferences().acceptNotice()

        assertTrue(preferences().observeNoticeAccepted().first())
    }

    private fun preferences() = aiPreferences(
        context = ApplicationProvider.getApplicationContext<Application>(),
        dispatchers = TestDispatcherProvider(dispatcher),
    )
}
