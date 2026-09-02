package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_AI_SUMMARY_COPY: String = "ai_summary_copy"
const val TAG_AI_SUMMARY_SHARE: String = "ai_summary_share"
const val TAG_AI_SUMMARY_REGENERATE: String = "ai_summary_regenerate"

/**
 * Copy, share and make it again.
 *
 * Copying and sharing both put the warning **in front of the text** (FR-025). A summary that leaves
 * the application loses its frame — it arrives in a chat without the card, without the mark and
 * without the screen around it — so if the warning is not inside the text, it is not there at all.
 */
@Composable
fun AiSummaryActions(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
    ) {
        Action(
            iconRes = R.drawable.ic_copy,
            label = stringResource(R.string.ai_summary_copy),
            testTag = TAG_AI_SUMMARY_COPY,
            onClick = onCopy,
        )
        Action(
            iconRes = R.drawable.ic_share,
            label = stringResource(R.string.ai_summary_share),
            testTag = TAG_AI_SUMMARY_SHARE,
            onClick = onShare,
        )
        Action(
            iconRes = R.drawable.ic_ai,
            label = stringResource(R.string.ai_summary_regenerate),
            testTag = TAG_AI_SUMMARY_REGENERATE,
            onClick = onRegenerate,
        )
    }
}

@Composable
private fun Action(iconRes: Int, label: String, testTag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = BocTheme.spacing.space2),
        )
    }
}

private val ICON_SIZE = 18.dp
