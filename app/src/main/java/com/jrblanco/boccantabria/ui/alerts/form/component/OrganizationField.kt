package com.jrblanco.boccantabria.ui.alerts.form.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R

const val TAG_ALERT_FORM_ORGANIZATION: String = "alert_form_organization"

/**
 * Free text with suggestions from what is stored (spec §5.5). Typing is enough — the suggestions
 * are a shortcut, not a constraint — and an empty field means any organisation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationField(
    value: String,
    suggestions: List<String>,
    onChange: (String) -> Unit,
    onChosen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val open = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onChange(it)
                expanded = true
            },
            singleLine = true,
            placeholder = { Text(text = stringResource(R.string.alert_form_organization_hint)) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .testTag(TAG_ALERT_FORM_ORGANIZATION),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(text = suggestion) },
                    onClick = {
                        expanded = false
                        onChosen(suggestion)
                    },
                )
            }
        }
    }
}
