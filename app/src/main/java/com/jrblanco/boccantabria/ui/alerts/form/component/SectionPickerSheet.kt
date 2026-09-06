package com.jrblanco.boccantabria.ui.alerts.form.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.SectionSelection
import com.jrblanco.boccantabria.ui.alerts.form.SectionPickerRow

const val TAG_ALERT_FORM_SECTIONS_SHEET: String = "alert_form_sections_sheet"
const val TAG_ALERT_FORM_SECTIONS_APPLY: String = "alert_form_sections_apply"
const val TAG_ALERT_FORM_SECTIONS_ALL: String = "alert_form_sections_all"
const val TAG_ALERT_FORM_SECTIONS_COUNT: String = "alert_form_sections_count"

fun alertSectionTag(code: String): String = "alert_form_section_$code"

/**
 * «Seleccionar secciones» (spec §14): the nine sections with their subsections indented, a
 * tri-state box on each parent, «Todas las secciones» to clear, a counter, and «Aplicar».
 *
 * Every toggle goes straight to the view model: there is no draft of the draft, because the model
 * already keeps the hierarchy consistent (research.md D-433).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SectionPickerSheet(
    rows: List<SectionPickerRow>,
    selected: Set<String>,
    selectedLeafCount: Int,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(TAG_ALERT_FORM_SECTIONS_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BocTheme.spacing.space6, end = BocTheme.spacing.space6, bottom = BocTheme.spacing.space10),
        ) {
            Text(
                text = stringResource(R.string.alert_form_sections_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = selected.isEmpty(), onValueChange = { onSelectAll() }, role = Role.Checkbox)
                    .padding(vertical = BocTheme.spacing.space2)
                    .testTag(TAG_ALERT_FORM_SECTIONS_ALL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected.isEmpty(), onCheckedChange = null)
                Text(
                    text = stringResource(R.string.alert_form_sections_all),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = BocTheme.spacing.space2),
                )
            }
            HorizontalDivider(color = BocTheme.colors.divider)
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                rows.forEach { row ->
                    item(key = row.section.code) {
                        SectionLine(
                            label = row.section.displayLabel,
                            state = row.state.toToggleableState(),
                            indent = 0.dp,
                            tag = alertSectionTag(row.section.code),
                            onToggle = { onToggle(row.section.code) },
                        )
                    }
                    items(items = row.children, key = { it.code }) { child ->
                        SectionLine(
                            label = child.displayLabel,
                            state = if (child.code in selected) ToggleableState.On else ToggleableState.Off,
                            indent = CHILD_INDENT,
                            tag = alertSectionTag(child.code),
                            onToggle = { onToggle(child.code) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = BocTheme.spacing.space3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.alert_form_sections_selected, selectedLeafCount, selectedLeafCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BocTheme.colors.textSecondary,
                    modifier = Modifier.testTag(TAG_ALERT_FORM_SECTIONS_COUNT),
                )
                Button(onClick = onApply, modifier = Modifier.testTag(TAG_ALERT_FORM_SECTIONS_APPLY)) {
                    Text(text = stringResource(R.string.alert_form_sections_apply))
                }
            }
        }
    }
}

@Composable
private fun SectionLine(
    label: String,
    state: ToggleableState,
    indent: androidx.compose.ui.unit.Dp,
    tag: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = state == ToggleableState.On, onValueChange = { onToggle() }, role = Role.Checkbox)
            .padding(start = indent, top = BocTheme.spacing.space2, bottom = BocTheme.spacing.space2)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = null)
        Text(
            text = label,
            style = if (indent == 0.dp) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = BocTheme.colors.textPrimary,
            modifier = Modifier.padding(start = BocTheme.spacing.space2),
        )
    }
}

private fun SectionSelection.ToggleState.toToggleableState(): ToggleableState = when (this) {
    SectionSelection.ToggleState.CHECKED -> ToggleableState.On
    SectionSelection.ToggleState.INDETERMINATE -> ToggleableState.Indeterminate
    SectionSelection.ToggleState.UNCHECKED -> ToggleableState.Off
}

private val CHILD_INDENT = 32.dp
