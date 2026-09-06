package com.jrblanco.boccantabria.ui.alerts.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocDialogShape
import com.jrblanco.boccantabria.domain.model.AlertRule

const val TAG_ALERT_DELETE_DIALOG: String = "alert_delete_dialog"
const val TAG_ALERT_DELETE_CONFIRM: String = "alert_delete_confirm"
const val TAG_ALERT_DELETE_CANCEL: String = "alert_delete_cancel"

/** The one confirmation this feature asks for (spec §12.4; experience document §12). */
@Composable
fun DeleteAlertDialog(rule: AlertRule, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(TAG_ALERT_DELETE_DIALOG),
        shape = BocDialogShape,
        title = { Text(text = stringResource(R.string.alerts_delete_title)) },
        text = { Text(text = stringResource(R.string.alerts_delete_body, rule.name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(TAG_ALERT_DELETE_CONFIRM),
            ) {
                Text(text = stringResource(R.string.alerts_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(TAG_ALERT_DELETE_CANCEL)) {
                Text(text = stringResource(R.string.alerts_delete_cancel))
            }
        },
    )
}
