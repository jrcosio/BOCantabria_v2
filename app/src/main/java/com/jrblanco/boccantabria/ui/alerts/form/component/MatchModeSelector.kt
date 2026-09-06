package com.jrblanco.boccantabria.ui.alerts.form.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode

const val TAG_ALERT_FORM_MODE_ANY: String = "alert_form_mode_any"
const val TAG_ALERT_FORM_MODE_ALL: String = "alert_form_mode_all"

/** «Cualquiera de las palabras» / «Todas las palabras» (spec §5.3). The whole row is the target. */
@Composable
fun MatchModeSelector(
    selected: KeywordMatchMode,
    onSelect: (KeywordMatchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        Option(R.string.alert_form_match_any, TAG_ALERT_FORM_MODE_ANY, selected == KeywordMatchMode.ANY) {
            onSelect(KeywordMatchMode.ANY)
        }
        Option(R.string.alert_form_match_all, TAG_ALERT_FORM_MODE_ALL, selected == KeywordMatchMode.ALL) {
            onSelect(KeywordMatchMode.ALL)
        }
    }
}

@Composable
private fun Option(labelRes: Int, tag: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = BocTheme.spacing.space1)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = BocTheme.colors.textPrimary,
            modifier = Modifier.padding(start = BocTheme.spacing.space2),
        )
    }
}
