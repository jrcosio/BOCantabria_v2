package com.jrblanco.boccantabria.core.util

import android.app.Application
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The process lifecycle, read and not observed.
 *
 * Under Robolectric no activity is started, so the process is created but never started: the one
 * state a test can assert without driving an activity is "not visible", which is also the one that
 * matters — it is what makes a cycle post a system notification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AppVisibilityProviderTest {

    @Test
    fun `with no activity started the application is not visible`() {
        assertFalse(ProcessLifecycleAppVisibilityProvider().isAppVisible())
    }
}
