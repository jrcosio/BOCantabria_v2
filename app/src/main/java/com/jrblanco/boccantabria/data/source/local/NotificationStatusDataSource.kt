package com.jrblanco.boccantabria.data.source.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jrblanco.boccantabria.domain.model.NotificationStatus

interface NotificationStatusDataSource {
    fun status(): NotificationStatus
}

/**
 * Asks Android whether it will show this application's notifications.
 *
 * Three answers rather than two (012 research.md D-427): when notifications are off on Android 13 or
 * later and the runtime permission was never granted, the right move is to **ask** in context, not to
 * send the person to Settings. The platform does not say whether a denial was permanent, so
 * `NEEDS_REQUEST` stays until the permission is granted; after two denials the system dialog stops
 * appearing on its own, which is what "no insistir" costs (D-428).
 */
class AndroidNotificationStatusDataSource(
    private val context: Context,
) : NotificationStatusDataSource {

    override fun status(): NotificationStatus {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return NotificationStatus.GRANTED
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        return if (permissionMissing) NotificationStatus.NEEDS_REQUEST else NotificationStatus.DISABLED
    }
}
