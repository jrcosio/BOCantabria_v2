package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme

const val TAG_SUGGESTIONS: String = "ask_suggestions"

fun suggestionTag(index: Int): String = "ask_suggestion_$index"

/**
 * Three ways in, for someone who has just opened a screen with an empty text field.
 *
 * **Fixed, the same for every publication**, and that is a decision rather than laziness: tailoring
 * them to the document would mean a request to the service **before anybody has asked anything**,
 * which is exactly what the first rule of the AI summary forbids — the allowance is shared and daily
 * (011 research.md D-325).
 *
 * Three because they fit and do not push the composer off the screen. They disappear with the first
 * message: once there is a conversation, they are clutter (FR-045).
 */
@Composable
fun SuggestedQuestions(
    onQuestionTapped: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val suggestions = listOf(
        stringResource(R.string.ask_suggestion_affects),
        stringResource(R.string.ask_suggestion_effective),
        stringResource(R.string.ask_suggestion_deadlines),
    )

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_SUGGESTIONS),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
    ) {
        suggestions.forEachIndexed { index, question ->
            AssistChip(
                onClick = { onQuestionTapped(question) },
                enabled = enabled,
                label = { Text(question, style = MaterialTheme.typography.labelLarge) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary,
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = enabled,
                    borderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.testTag(suggestionTag(index)),
            )
        }
    }
}
