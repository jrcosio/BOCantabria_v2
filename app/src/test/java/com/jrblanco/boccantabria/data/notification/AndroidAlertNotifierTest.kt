package com.jrblanco.boccantabria.data.notification

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.core.notification.AlertIntentExtras
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AlertNotification
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * The only class that touches the notification manager, against Robolectric's shadow: the channel,
 * one notification per publication, the summary from two up, the intents, and the silence when
 * Android will not show anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AndroidAlertNotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val crashReporter = RecordingCrashReporter()
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private lateinit var shadow: ShadowNotificationManager

    private val ganaderia = AlertNotification(publication("boc:1", title = "Ayudas a la ganadería."), listOf("Ganadería"))
    private val pesca = AlertNotification(publication("boc:2", title = "Ayudas a la pesca."), listOf("Pesca", "Rural"))

    @Before
    fun setUp() {
        shadow = shadowOf(manager)
        // API 33+: the runtime permission has to be held, or nothing is posted (FR-062).
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun notifier() = AndroidAlertNotifier(context, crashReporter)

    @Test
    fun `the channel is created, and creating it twice does not fail`() {
        notifier().post(listOf(ganaderia))
        notifier().post(listOf(ganaderia))

        val channel = manager.getNotificationChannel(AndroidAlertNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("Avisos del BOC", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `one publication is one notification and no summary`() {
        notifier().post(listOf(ganaderia))

        assertEquals(1, shadow.size())
        val notification = shadow.getNotification(AndroidAlertNotifier.notificationId("boc:1"))
        assertNotNull(notification)
        assertEquals("Nueva publicación: Ganadería", shadowOf(notification).contentTitle)
        assertEquals("Ayudas a la ganadería.", shadowOf(notification).contentText)
        assertTrue(notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(AndroidAlertNotifier.GROUP_KEY, notification.group)
    }

    @Test
    fun `several rules give the generic title`() {
        notifier().post(listOf(pesca))

        val notification = shadow.getNotification(AndroidAlertNotifier.notificationId("boc:2"))
        assertEquals("Nueva publicación del BOC", shadowOf(notification).contentTitle)
    }

    @Test
    fun `two publications add a summary in the same group`() {
        notifier().post(listOf(ganaderia, pesca))

        assertEquals(3, shadow.size())
        val summary = shadow.getNotification(AndroidAlertNotifier.SUMMARY_ID)
        assertNotNull(summary)
        assertTrue(summary.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals(AndroidAlertNotifier.GROUP_KEY, summary.group)
        assertEquals("2 publicaciones nuevas coinciden con tus avisos", shadowOf(summary).contentTitle)
    }

    @Test
    fun `the intent of a publication carries its key, the summary's carries the news target`() {
        notifier().post(listOf(ganaderia, pesca))

        val single = shadow.getNotification(AndroidAlertNotifier.notificationId("boc:1")).contentIntent
        val summary = shadow.getNotification(AndroidAlertNotifier.SUMMARY_ID).contentIntent
        val singleIntent = shadowOf(single).savedIntent
        val summaryIntent = shadowOf(summary).savedIntent

        assertEquals(AlertIntentExtras.TARGET_PUBLICATION, singleIntent.getStringExtra(AlertIntentExtras.EXTRA_TARGET))
        assertEquals("boc:1", singleIntent.getStringExtra(AlertIntentExtras.EXTRA_EXTERNAL_KEY))
        assertEquals(AlertIntentExtras.TARGET_NEWS, summaryIntent.getStringExtra(AlertIntentExtras.EXTRA_TARGET))
        assertTrue(shadowOf(single).flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    fun `two publications get two different pending intents, the same publication the same`() {
        notifier().post(listOf(ganaderia, pesca))

        val a = shadow.getNotification(AndroidAlertNotifier.notificationId("boc:1")).contentIntent
        val b = shadow.getNotification(AndroidAlertNotifier.notificationId("boc:2")).contentIntent
        assertNotEquals(shadowOf(a).requestCode, shadowOf(b).requestCode)
        assertEquals(AndroidAlertNotifier.notificationId("boc:1"), shadowOf(a).requestCode)
    }

    /** FR-062: nothing is posted, nothing throws; the matches are already stored by then. */
    @Test
    fun `with notifications disabled nothing is posted and it is logged`() {
        shadow.setNotificationsEnabled(false)

        notifier().post(listOf(ganaderia, pesca))

        assertEquals(0, shadow.size())
        assertTrue(crashReporter.messages.any { it == "alerts: notifications disabled, 2 match(es) kept" })
    }

    @Test
    fun `an empty list does nothing`() {
        notifier().post(emptyList())

        assertEquals(0, shadow.size())
        assertTrue(crashReporter.messages.isEmpty())
    }

    /** The log says how many; it never says which. */
    @Test
    fun `the log carries no title and no rule name`() {
        notifier().post(listOf(ganaderia, pesca))

        val log = crashReporter.messages.joinToString("\n")
        assertTrue(log.contains("alerts: posted 2 notification(s) + summary"))
        assertFalse(log.contains("Ganadería"))
        assertFalse(log.contains("ganadería"))
        assertFalse(log.contains("Pesca"))
    }
}
