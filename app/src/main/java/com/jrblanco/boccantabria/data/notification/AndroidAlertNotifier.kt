package com.jrblanco.boccantabria.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.notification.AlertIntentExtras
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.domain.model.AlertNotification
import com.jrblanco.boccantabria.domain.repository.AlertNotifier

/**
 * The system notifications of the alerts: the **only** place that touches the notification manager.
 *
 * One notification per publication, all in one group, and — from two publications up — a summary
 * that counts them. The group's alert behaviour is `GROUP_ALERT_SUMMARY`, so a burst rings once
 * (FR-047). Tapping a publication lands on its detail; tapping the summary lands on Novedades. The
 * `Intent` is the launch intent plus extras whose keys live in `core`, so this class never names the
 * activity (012 research.md D-417, D-425).
 *
 * Nothing is posted when Android will not show it. The matches are already stored by then, and the
 * badge and the Novedades tab carry them (FR-062). That guard is also what satisfies the
 * `MissingPermission` lint on `notify`.
 *
 * The small icon is a **dedicated monochrome** bell: the status bar paints an icon's alpha in one
 * colour, and the shield would come out as an unreadable silhouette (D-418).
 *
 * The log says how many were posted, never which: a title names what the person follows.
 */
class AndroidAlertNotifier(
    private val context: Context,
    private val crashReporter: CrashReporter,
) : AlertNotifier {

    override fun post(notifications: List<AlertNotification>) {
        if (notifications.isEmpty()) return
        try {
            val manager = NotificationManagerCompat.from(context)
            ensureChannel(manager)
            if (!manager.areNotificationsEnabled() || !hasPostPermission()) {
                crashReporter.log("alerts: notifications disabled, ${notifications.size} match(es) kept")
                return
            }

            postAll(manager, notifications)
            crashReporter.log(
                "alerts: posted ${notifications.size} notification(s)" +
                    if (notifications.size >= SUMMARY_THRESHOLD) " + summary" else "",
            )
        } catch (unexpected: RuntimeException) {
            // A notification that cannot be shown is not a failed cycle.
            crashReporter.recordNonFatal(unexpected)
        }
    }

    /**
     * The runtime permission, checked explicitly and right before `notify`: `areNotificationsEnabled()`
     * already implies it, but the lint analyser only follows `checkSelfPermission`.
     */
    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun postAll(manager: NotificationManagerCompat, notifications: List<AlertNotification>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        notifications.forEach { notification ->
            manager.notify(notificationId(notification.publication.externalKey), build(notification))
        }
        if (notifications.size >= SUMMARY_THRESHOLD) {
            manager.notify(SUMMARY_ID, buildSummary(notifications))
        }
    }

    private fun build(notification: AlertNotification): android.app.Notification {
        val title = if (notification.ruleNames.size == 1) {
            context.getString(R.string.alert_notification_title_single, notification.ruleNames.single())
        } else {
            context.getString(R.string.alert_notification_title_multi)
        }
        val body = notification.publication.title
        val style = NotificationCompat.BigTextStyle().bigText(body)
        if (notification.ruleNames.size > 1) {
            style.setSummaryText(
                context.getString(R.string.alert_notification_matches, notification.ruleNames.joinInSpanish()),
            )
        }
        return baseBuilder()
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setContentIntent(publicationIntent(notification.publication.externalKey))
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .build()
    }

    private fun buildSummary(notifications: List<AlertNotification>): android.app.Notification {
        val count = notifications.size
        val title = context.resources.getQuantityString(R.plurals.alert_notification_summary, count, count)
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        notifications.take(INBOX_LINES).forEach { inbox.addLine(it.publication.title) }
        return baseBuilder()
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alert_notification_channel_description))
            .setStyle(inbox)
            .setContentIntent(newsIntent())
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_bell)
        .setColor(ContextCompat.getColor(context, R.color.splash_background))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        .setAutoCancel(true)

    private fun publicationIntent(externalKey: String): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(externalKey),
        launchIntent()
            .putExtra(AlertIntentExtras.EXTRA_TARGET, AlertIntentExtras.TARGET_PUBLICATION)
            .putExtra(AlertIntentExtras.EXTRA_EXTERNAL_KEY, externalKey),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun newsIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        SUMMARY_ID,
        launchIntent().putExtra(AlertIntentExtras.EXTRA_TARGET, AlertIntentExtras.TARGET_NEWS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The launcher intent, so this class never names `MainActivity`. */
    private fun launchIntent(): Intent =
        (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Idempotent and cheap: Android ignores a channel that already exists. */
    private fun ensureChannel(manager: NotificationManagerCompat) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.alert_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun List<String>.joinInSpanish(): String = when (size) {
        0 -> ""
        1 -> single()
        else -> dropLast(1).joinToString(", ") + " y " + last()
    }

    companion object {
        const val CHANNEL_ID: String = "boc_alerts"
        const val GROUP_KEY: String = "boc_alerts_group"
        const val SUMMARY_ID: Int = 1

        /** From this many publications up, a summary joins the group. */
        const val SUMMARY_THRESHOLD: Int = 2
        private const val INBOX_LINES = 5

        /** Stable per publication, never `SUMMARY_ID`, never negative-only collisions with it. */
        fun notificationId(externalKey: String): Int {
            val hash = externalKey.hashCode()
            return if (hash == SUMMARY_ID) SUMMARY_ID + 1 else hash
        }
    }
}
