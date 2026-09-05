package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_CHAT_ERROR: String = "ask_error"
const val TAG_CHAT_RETRY: String = "ask_error_retry"

/**
 * What went wrong, in one plain sentence, and a way out when there is one.
 *
 * **No status codes, no numbers, no provider name** (FR-031). And the failed question stays in the
 * list above: whoever asked already wrote it, and making them write it again because the service
 * failed would be charging them for somebody else's fault (D-320).
 */
@Composable
fun ChatErrorRow(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_CHAT_ERROR),
    ) {
        Column(modifier = Modifier.padding(BocTheme.spacing.space4)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                )
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }

            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(TAG_CHAT_RETRY),
                ) {
                    Text(stringResource(R.string.ask_retry))
                }
            }
        }
    }
}

private val ICON_SIZE = 20.dp
