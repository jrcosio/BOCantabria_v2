package com.jrblanco.boccantabria.ui.alerts.form.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocDialogShape

const val TAG_ALERT_PERMISSION_DIALOG: String = "alert_permission_dialog"
const val TAG_ALERT_PERMISSION_CONTINUE: String = "alert_permission_continue"
const val TAG_ALERT_PERMISSION_LATER: String = "alert_permission_later"

/**
 * «Activa las notificaciones», shown once, after the first rule is saved and before Android asks
 * (spec §16; experience document §12). The rule is already saved whatever is answered.
 */
@Composable
fun NotificationPermissionDialog(onLater: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag(TAG_ALERT_PERMISSION_DIALOG),
        shape = BocDialogShape,
        title = { Text(text = stringResource(R.string.alert_permission_title)) },
        text = { Text(text = stringResource(R.string.alert_permission_body)) },
        confirmButton = {
            TextButton(onClick = onContinue, modifier = Modifier.testTag(TAG_ALERT_PERMISSION_CONTINUE)) {
                Text(text = stringResource(R.string.alert_permission_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag(TAG_ALERT_PERMISSION_LATER)) {
                Text(text = stringResource(R.string.alert_permission_later))
            }
        },
    )
}
