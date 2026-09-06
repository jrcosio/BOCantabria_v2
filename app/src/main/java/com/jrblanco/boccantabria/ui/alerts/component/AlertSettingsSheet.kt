package com.jrblanco.boccantabria.ui.alerts.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_ALERTS_SETTINGS_SHEET: String = "alerts_settings_sheet"

/**
 * The settings behind the sliders icon (spec §12.1): the state of the permission, a way to Android's
 * settings, and when the bulletin was last checked. **No frequency to choose**: Android does not let
 * the application promise one (FR-065).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsSheet(
    status: NotificationStatus,
    lastSyncAt: Long?,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(TAG_ALERTS_SETTINGS_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BocTheme.spacing.space6, end = BocTheme.spacing.space6, bottom = BocTheme.spacing.space10),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
        ) {
            Text(
                text = stringResource(R.string.alerts_settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = when (status) {
                    NotificationStatus.GRANTED -> stringResource(R.string.alerts_settings_permission_granted)
                    NotificationStatus.NEEDS_REQUEST -> stringResource(R.string.alerts_settings_permission_needed)
                    NotificationStatus.DISABLED -> stringResource(R.string.alerts_settings_permission_disabled)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = BocTheme.colors.textPrimary,
            )
            Text(
                text = lastSyncAt?.let {
                    stringResource(R.string.alerts_settings_last_check, Instant.ofEpochMilli(it).atZone(zone).format(DATE_TIME))
                } ?: stringResource(R.string.alerts_settings_never_checked),
                style = MaterialTheme.typography.bodyMedium,
                color = BocTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.alerts_settings_periodic),
                style = MaterialTheme.typography.bodySmall,
                color = BocTheme.colors.textMuted,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenSettings) {
                    Text(text = stringResource(R.string.alerts_permission_open_settings))
                }
            }
        }
    }
}

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-ES"))
