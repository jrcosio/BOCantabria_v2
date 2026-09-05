package com.jrblanco.boccantabria.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.jrblanco.boccantabria.core.ui.theme.BocBottomSheetShape
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_AI_NOTICE_SHEET: String = "ai_notice_sheet"
const val TAG_AI_NOTICE_CONTINUE: String = "ai_notice_continue"
const val TAG_AI_NOTICE_CANCEL: String = "ai_notice_cancel"

/**
 * Told once, before anything leaves the device.
 *
 * The official document goes to a service outside the phone, and finding that out afterwards is
 * finding out too late. Shown on the **first** AI action and never again; cancelling sends nothing
 * (007 FR-043, FR-044, FR-045).
 *
 * It lived in `ui/detail/component` while the summary was the only thing that sent anything. Since
 * feature 011 two screens open it — the summary and the conversation — and one acceptance covers
 * both, so it belongs where this house keeps shared stateless composables. Making the chat import it
 * from another screen's package would be the kind of dependency that ends in a tangle
 * (011 research.md D-316).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiNoticeSheet(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = BocBottomSheetShape,
        modifier = modifier.testTag(TAG_AI_NOTICE_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BocTheme.spacing.space5,
                    end = BocTheme.spacing.space5,
                    bottom = BocTheme.spacing.space8,
                ),
            verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space4),
        ) {
            Text(
                text = stringResource(R.string.ai_notice_title),
                style = MaterialTheme.typography.titleLarge,
                color = BocTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.ai_notice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = BocTheme.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(TAG_AI_NOTICE_CANCEL),
                ) {
                    Text(stringResource(R.string.ai_notice_cancel))
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.testTag(TAG_AI_NOTICE_CONTINUE),
                ) {
                    Text(stringResource(R.string.ai_notice_continue))
                }
            }
        }
    }
}
