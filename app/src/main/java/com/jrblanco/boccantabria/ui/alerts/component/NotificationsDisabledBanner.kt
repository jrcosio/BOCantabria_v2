package com.jrblanco.boccantabria.ui.alerts.component

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocBannerShape
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_ALERTS_PERMISSION_BANNER: String = "alerts_permission_banner"
const val TAG_ALERTS_OPEN_SETTINGS: String = "alerts_open_settings"

/**
 * Rules that would fire, and an Android that will not show them (FR-014). Persistent, not blocking,
 * and it never touches the rules.
 */
@Composable
fun NotificationsDisabledBanner(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_ALERTS_PERMISSION_BANNER),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = BocBannerShape,
    ) {
        Column(modifier = Modifier.padding(BocTheme.spacing.space4)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(R.drawable.ic_notifications_off),
                    contentDescription = null,
                    tint = BocTheme.colors.warning,
                    modifier = Modifier.size(ICON_SIZE),
                )
                Spacer(modifier = Modifier.width(BocTheme.spacing.space3))
                Text(
                    text = stringResource(R.string.alerts_permission_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BocTheme.colors.textPrimary,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag(TAG_ALERTS_OPEN_SETTINGS)) {
                    Text(text = stringResource(R.string.alerts_permission_open_settings))
                }
            }
        }
    }
}

/**
 * Android's notification settings for this application. Launched by the screen, the way Info opens
 * its links: the view model never touches an `Intent` (research.md D-429).
 */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private val ICON_SIZE = 24.dp
