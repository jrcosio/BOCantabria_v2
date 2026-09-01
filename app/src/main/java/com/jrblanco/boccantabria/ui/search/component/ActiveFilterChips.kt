package com.jrblanco.boccantabria.ui.search.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.SearchQuery
import java.time.format.DateTimeFormatter

const val TAG_SEARCH_CHIPS: String = "search_chips"
const val TAG_SEARCH_CHIPS_CLEAR_ALL: String = "search_chips_clear_all"

/** `dates`, `section`, `subsection`, `issuer`. */
fun searchChipTag(kind: String): String = "search_chip_$kind"

/**
 * What is currently narrowing the results, in plain sight and one tap from being undone.
 *
 * Visible on the screen rather than only inside the sheet, because a filter you have to open a
 * panel to remember is a filter that quietly explains a short list. Section 11.5 of the design
 * document.
 *
 * Every cross says **which** filter it removes, not just "remove": a screen reader user hearing
 * four identical buttons learns nothing.
 */
@Composable
@Suppress("LongParameterList")
fun ActiveFilterChips(
    query: SearchQuery,
    sectionsByCode: Map<String, BocSection>,
    onRemoveDates: () -> Unit,
    onRemoveSection: () -> Unit,
    onRemoveSubsection: () -> Unit,
    onRemoveIssuer: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!query.hasFilters) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = BocTheme.spacing.screenMargin)
            .testTag(TAG_SEARCH_CHIPS),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (query.from != null || query.to != null) {
            FilterChip(
                label = dateRangeLabel(query),
                kind = "dates",
                onRemove = onRemoveDates,
            )
        }
        query.sectionCode?.let { code ->
            FilterChip(
                label = stringResource(R.string.search_filter_section) + ": " +
                    (sectionsByCode[code]?.name ?: code),
                kind = "section",
                onRemove = onRemoveSection,
            )
        }
        query.subsectionCode?.let { code ->
            FilterChip(
                label = stringResource(R.string.search_filter_subsection) + ": " +
                    (sectionsByCode[code]?.name ?: code),
                kind = "subsection",
                onRemove = onRemoveSubsection,
            )
        }
        query.issuer?.let { issuer ->
            FilterChip(
                label = stringResource(R.string.search_filter_issuer) + ": " + issuer,
                kind = "issuer",
                onRemove = onRemoveIssuer,
            )
        }

        TextButton(
            onClick = onClearAll,
            modifier = Modifier.testTag(TAG_SEARCH_CHIPS_CLEAR_ALL),
        ) {
            Text(text = stringResource(R.string.search_chips_clear_all))
        }
    }
}

@Composable
private fun FilterChip(label: String, kind: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                // Says which filter it removes, not just "remove".
                contentDescription = stringResource(R.string.search_filter_remove, label),
                modifier = Modifier.size(CHIP_ICON_SIZE),
            )
        },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.testTag(searchChipTag(kind)),
    )
}

@Composable
private fun dateRangeLabel(query: SearchQuery): String {
    val from = query.from?.format(SHORT_DATE)
    val to = query.to?.format(SHORT_DATE)
    return when {
        from != null && to != null -> "$from - $to"
        from != null -> stringResource(R.string.search_filter_date_from) + ": " + from
        else -> stringResource(R.string.search_filter_date_to) + ": " + to
    }
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private val CHIP_ICON_SIZE = 20.dp
