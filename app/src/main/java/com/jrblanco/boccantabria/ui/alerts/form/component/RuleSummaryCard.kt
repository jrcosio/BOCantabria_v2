package com.jrblanco.boccantabria.ui.alerts.form.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.domain.model.SectionSelection
import com.jrblanco.boccantabria.ui.alerts.component.sectionPartText

const val TAG_ALERT_FORM_SUMMARY: String = "alert_form_summary"

/**
 * «Así funcionará», in plain Spanish and updated with every change (spec §13). Never a technical term:
 * no AND, no OR, no field names (FR-025).
 */
@Composable
fun RuleSummaryCard(
    draft: AlertRuleDraft,
    sectionParts: List<SectionSelection.Part>?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(BocTheme.spacing.space4), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON_SIZE),
            )
            Spacer(modifier = Modifier.width(BocTheme.spacing.space3))
            Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space1)) {
                Text(
                    text = stringResource(R.string.alert_form_summary_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = ruleSummaryText(draft, sectionParts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BocTheme.colors.textPrimary,
                    modifier = Modifier.testTag(TAG_ALERT_FORM_SUMMARY),
                )
            }
        }
    }
}

/**
 * The sentence. Sections and words each contribute a fragment; the organisation is appended; a
 * draft with no criterion says what is missing instead.
 */
@Composable
fun ruleSummaryText(draft: AlertRuleDraft, sectionParts: List<SectionSelection.Part>?): String {
    if (!draft.hasCriteria) return stringResource(R.string.alert_form_summary_need_criteria)

    val sections = sectionParts?.let { parts ->
        joinNaturally(parts.map { sectionPartText(it) }, stringResource(R.string.alert_form_summary_join_and))
    }
    val joiner = when (draft.matchMode) {
        KeywordMatchMode.ANY -> stringResource(R.string.alert_form_summary_join_or)
        KeywordMatchMode.ALL -> stringResource(R.string.alert_form_summary_join_and)
    }
    val words = draft.keywords.takeIf { it.isNotEmpty() }?.let { joinNaturally(it.map { word -> "«$word»" }, joiner) }
    val organisation = draft.organizationQuery.trim().takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.alert_form_summary_organization, it) }
        .orEmpty()

    val sentence = when {
        words != null && sections != null -> stringResource(R.string.alert_form_summary_words_in_sections, sections, words)
        words != null -> stringResource(R.string.alert_form_summary_words, words)
        sections != null -> stringResource(R.string.alert_form_summary_any_of_sections, sections)
        else -> stringResource(R.string.alert_form_summary_any)
    }
    return if (organisation.isEmpty()) sentence else sentence.dropLast(1) + organisation + "."
}

/** «a», «a y b», «a, b y c». */
@Composable
private fun joinNaturally(items: List<String>, lastJoiner: String): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString(stringResource(R.string.alert_form_summary_join_comma)) + lastJoiner + items.last()
}

private val ICON_SIZE = 28.dp
