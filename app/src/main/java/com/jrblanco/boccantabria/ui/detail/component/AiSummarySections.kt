package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.AiSummary

fun aiSectionTag(key: String): String = "ai_section_$key"

const val SECTION_KEY_POINTS: String = "key_points"
const val SECTION_AFFECTED: String = "affected"
const val SECTION_DATES: String = "dates"
const val SECTION_AMOUNTS: String = "amounts"
const val SECTION_ACTIONS: String = "actions"
const val SECTION_APPEALS: String = "appeals"
const val SECTION_WARNINGS: String = "warnings"

/**
 * The structured half of the summary, under the card.
 *
 * **Every section with an empty list is absent, not empty.** An empty list means the document does
 * not say so, and padding it with «no consta» would be inventing a fact the document does not carry
 * (FR-015).
 *
 * The order is the one the specification fixes: what it is about, who it affects, when, how much,
 * what to do, and how to challenge it.
 */
@Composable
fun AiSummarySections(
    summary: AiSummary,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space6),
    ) {
        ReferencedTextSection(
            key = SECTION_KEY_POINTS,
            title = stringResource(R.string.ai_summary_section_key_points),
            items = summary.keyPoints,
            onOpenPage = onOpenPage,
        )
        ReferencedTextSection(
            key = SECTION_AFFECTED,
            title = stringResource(R.string.ai_summary_section_affected),
            items = summary.affectedParties,
            onOpenPage = onOpenPage,
        )
        Section(
            key = SECTION_DATES,
            title = stringResource(R.string.ai_summary_section_dates),
            items = summary.datesAndDeadlines,
        ) { item ->
            Entry(
                lead = item.dateOrPeriod,
                detail = item.description,
                pages = item.pages,
                onOpenPage = onOpenPage,
            )
        }
        Section(
            key = SECTION_AMOUNTS,
            title = stringResource(R.string.ai_summary_section_amounts),
            items = summary.amounts,
        ) { item ->
            Entry(
                lead = item.amount,
                detail = item.concept,
                pages = item.pages,
                onOpenPage = onOpenPage,
            )
        }
        Section(
            key = SECTION_ACTIONS,
            title = stringResource(R.string.ai_summary_section_actions),
            items = summary.requiredActions,
        ) { item ->
            Entry(
                lead = item.action,
                detail = item.deadline,
                pages = item.pages,
                onOpenPage = onOpenPage,
            )
        }
        ReferencedTextSection(
            key = SECTION_APPEALS,
            title = stringResource(R.string.ai_summary_section_appeals),
            items = summary.appealsOrClaims,
            onOpenPage = onOpenPage,
        )
        Section(
            key = SECTION_WARNINGS,
            title = stringResource(R.string.ai_summary_section_warnings),
            items = summary.warnings,
        ) { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium,
                color = BocTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ReferencedTextSection(
    key: String,
    title: String,
    items: List<AiSummary.ReferencedText>,
    onOpenPage: (Int) -> Unit,
) {
    Section(key = key, title = title, items = items) { item ->
        Entry(lead = item.text, detail = null, pages = item.pages, onOpenPage = onOpenPage)
    }
}

/** Draws nothing at all when [items] is empty. That absence is the requirement. */
@Composable
private fun <T> Section(
    key: String,
    title: String,
    items: List<T>,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(aiSectionTag(key)),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        items.forEach { item -> content(item) }
    }
}

@Composable
private fun Entry(
    lead: String,
    detail: String?,
    pages: List<Int>,
    onOpenPage: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2)) {
        Text(
            text = lead,
            style = MaterialTheme.typography.bodyLarge,
            color = BocTheme.colors.textPrimary,
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = BocTheme.colors.textSecondary,
            )
        }
        PageChips(pages = pages, onOpenPage = onOpenPage)
    }
}
