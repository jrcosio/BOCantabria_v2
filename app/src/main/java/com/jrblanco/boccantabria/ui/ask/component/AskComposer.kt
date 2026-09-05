package com.jrblanco.boccantabria.ui.ask.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AiChatConstants

const val TAG_COMPOSER: String = "ask_composer"
const val TAG_COMPOSER_FIELD: String = "ask_composer_field"
const val TAG_COMPOSER_SEND: String = "ask_composer_send"
const val TAG_COMPOSER_COUNTER: String = "ask_composer_counter"

/**
 * Where a question is written and sent.
 *
 * ### The window insets are applied here, by hand, and that is not optional
 *
 * This is the `bottomBar` of a `Scaffold`, and **a `Scaffold` with a bottom bar discards its own
 * bottom window inset**, replacing it with the measured height of the bar anchored to the raw window
 * edge. Setting `contentWindowInsets` changes nothing. The bar is the only thing that can hold itself
 * above the three system buttons, and it does so by applying
 * `windowInsetsPadding(systemBars.only(Horizontal + Bottom))` **inside** its own `Surface` — which is
 * exactly what `NavigationBar` does, and exactly what the detail's action bar had to learn
 * (`DetailActionBarInsetTest`, 011 research.md D-324).
 *
 * `imePadding()` is the other half: without it the keyboard covers the field it was opened to fill.
 *
 * A caution for whoever runs the tests: the inset assertion **only bites with three-button
 * navigation**. With gestures the inset can be zero and the test passes having checked nothing.
 * `adb shell settings put secure navigation_mode 0` before the instrumented run.
 */
@Composable
fun AskComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    enabled: Boolean,
    showCounter: Boolean,
    isOverLimit: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = BocTheme.elevation.level2,
        modifier = modifier.testTag(TAG_COMPOSER),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                            androidx.compose.foundation.layout.WindowInsetsSides.Bottom,
                    ),
                ),
        ) {
            HorizontalDivider(color = BocTheme.colors.divider)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = BocTheme.spacing.space4,
                        vertical = BocTheme.spacing.space3,
                    ),
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = enabled,
                        placeholder = { Text(stringResource(R.string.ask_composer_hint)) },
                        shape = MaterialTheme.shapes.extraLarge,
                        maxLines = MAX_LINES,
                        isError = isOverLimit,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TAG_COMPOSER_FIELD),
                    )

                    // The limit has to be visible **before** sending, not discovered after (FR-007).
                    if (showCounter) {
                        Text(
                            text = stringResource(
                                R.string.ask_counter,
                                draft.length,
                                AiChatConstants.MAX_QUESTION_LENGTH,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverLimit) {
                                MaterialTheme.colorScheme.error
                            } else {
                                BocTheme.colors.textMuted
                            },
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    end = BocTheme.spacing.space3,
                                    top = BocTheme.spacing.space1,
                                )
                                .testTag(TAG_COMPOSER_COUNTER),
                        )
                    }
                }

                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .size(SEND_SIZE)
                        .testTag(TAG_COMPOSER_SEND),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = stringResource(R.string.ask_send),
                        modifier = Modifier.size(SEND_ICON),
                    )
                }
            }
        }
    }
}

private const val MAX_LINES = 4
private val SEND_SIZE = 48.dp
private val SEND_ICON = 20.dp
