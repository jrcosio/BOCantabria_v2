package com.jrblanco.boccantabria.data.source.local

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric's notification manager starts enabled, and API 36 has the runtime permission, which
 * the test application does not hold: that is exactly the pair of states the form and the banner act on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AndroidNotificationStatusDataSourceTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `enabled notifications are granted`() {
        assertEquals(NotificationStatus.GRANTED, AndroidNotificationStatusDataSource(context).status())
    }

    @Test
    fun `disabled without the runtime permission means the permission has to be asked for`() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)

        assertEquals(NotificationStatus.NEEDS_REQUEST, AndroidNotificationStatusDataSource(context).status())
    }

    @Test
    fun `disabled with the permission already granted means switched off in settings`() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(NotificationStatus.DISABLED, AndroidNotificationStatusDataSource(context).status())
    }
}
