package com.jrblanco.boccantabria.ui.alerts.form.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.KeywordRejection

const val TAG_ALERT_FORM_KEYWORD_INPUT: String = "alert_form_keyword_input"
const val TAG_ALERT_FORM_KEYWORD_ADD: String = "alert_form_keyword_add"
const val TAG_ALERT_FORM_KEYWORD_ERROR: String = "alert_form_keyword_error"

fun alertKeywordChipTag(keyword: String): String = "alert_form_keyword_$keyword"

/**
 * Words and phrases as removable chips (spec §5.2). Enter or «+» adds; the cross removes; a refused
 * term says why under the field, and the field keeps the text so it can be corrected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordChipsInput(
    keywords: List<String>,
    rejection: KeywordRejection?,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    // Only what is not yet in the draft leaves the field: a refused term stays put to be fixed.
    var lastSubmitted by rememberSaveable { mutableStateOf<String?>(null) }
    if (lastSubmitted != null && rejection == null && keywords.any { it == lastSubmitted?.trim() }) {
        typed = ""
        lastSubmitted = null
    }

    fun submit() {
        val candidate = typed
        if (candidate.isBlank()) return
        lastSubmitted = candidate
        onAdd(candidate)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            placeholder = { Text(text = stringResource(R.string.alert_form_keywords_hint)) },
            trailingIcon = {
                IconButton(onClick = ::submit, modifier = Modifier.testTag(TAG_ALERT_FORM_KEYWORD_ADD)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.alert_form_keywords_add),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            isError = rejection != null,
            supportingText = rejection?.let { reason ->
                { Text(text = rejectionText(reason), modifier = Modifier.testTag(TAG_ALERT_FORM_KEYWORD_ERROR)) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_ALERT_FORM_KEYWORD_INPUT),
        )
        if (keywords.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
                verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space1),
            ) {
                keywords.forEach { keyword ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(keyword) },
                        label = { Text(text = keyword, style = MaterialTheme.typography.labelLarge) },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.alert_form_keyword_remove, keyword),
                                modifier = Modifier.size(CHIP_ICON_SIZE),
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.testTag(alertKeywordChipTag(keyword)),
                    )
                }
            }
        }
    }
}

@Composable
private fun rejectionText(reason: KeywordRejection): String = when (reason) {
    KeywordRejection.BLANK, KeywordRejection.TOO_SHORT ->
        stringResource(R.string.alert_form_keyword_rejected_short, AlertRuleDraft.KEYWORD_MIN_LENGTH)
    KeywordRejection.TOO_LONG -> stringResource(R.string.alert_form_keyword_rejected_long, AlertRuleDraft.KEYWORD_MAX_LENGTH)
    KeywordRejection.DUPLICATE -> stringResource(R.string.alert_form_keyword_rejected_duplicate)
    KeywordRejection.LIMIT_REACHED -> stringResource(R.string.alert_form_keyword_rejected_limit, AlertRuleDraft.MAX_KEYWORDS)
}

private val CHIP_ICON_SIZE = 18.dp
