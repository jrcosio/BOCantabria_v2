package com.jrblanco.boccantabria.ui.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.core.ui.theme.sectionColor
import com.jrblanco.boccantabria.domain.model.BocSection

const val TAG_SECTIONS_DRAWER: String = "sections_drawer"
const val TAG_SECTIONS_QUERY: String = "sections_query"
const val TAG_SECTIONS_EMPTY: String = "sections_empty"

fun sectionRowTag(code: String): String = "section_row_$code"
fun sectionToggleTag(code: String): String = "section_toggle_$code"

/**
 * The sections of the bulletin, as a side panel.
 *
 * The design document described this as a screen of its own; the owner asked for a panel, and
 * the document has been updated to match. What has not changed is what it contains: a filter
 * field, the nine sections and their subsections. There are no bells and no alerts card: since
 * feature 012 the alerts live in the bottom bar, and a rule is created from there, not from a row here.
 */
@Composable
fun SectionsDrawerContent(
    state: SectionsUiState,
    onQueryChanged: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onSelect: (BocSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .testTag(TAG_SECTIONS_DRAWER),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.sections_search_hint)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = BocTheme.colors.textMuted,
                )
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(BocTheme.spacing.space4)
                .testTag(TAG_SECTIONS_QUERY),
        )

        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.sections_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = BocTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BocTheme.spacing.space6)
                    .testTag(TAG_SECTIONS_EMPTY),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = state.rows, key = { it.section.code }) { row ->
                SectionRowItem(
                    row = row,
                    isExpanded = row.section.code in state.expanded,
                    onToggle = { onToggleExpanded(row.section.code) },
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun SectionRowItem(
    row: SectionRow,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (BocSection) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) CHEVRON_OPEN_DEGREES else 0f,
        label = "chevron",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .clickable { onSelect(row.section) }
            .padding(horizontal = BocTheme.spacing.space4)
            .testTag(sectionRowTag(row.section.code)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(row.section.iconRes()),
            contentDescription = null,
            tint = sectionColor(row.section.colorGroup),
            modifier = Modifier.size(SECTION_ICON_SIZE),
        )
        Text(
            text = row.section.displayLabel,
            style = MaterialTheme.typography.titleMedium,
            color = BocTheme.colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = BocTheme.spacing.space4),
        )
        if (row.isExpandable) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(
                    if (isExpanded) R.string.sections_collapse else R.string.sections_expand,
                    row.section.name,
                ),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(SECTION_ICON_SIZE)
                    .rotate(chevronRotation)
                    .clickable(onClick = onToggle)
                    .testTag(sectionToggleTag(row.section.code)),
            )
        }
    }

    AnimatedVisibility(visible = isExpanded && row.isExpandable) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BocTheme.spacing.space4)
                .clip(MaterialTheme.shapes.small)
                .background(BocTheme.colors.surfaceSoft),
        ) {
            row.children.forEach { child ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CHILD_MIN_HEIGHT)
                        .clickable { onSelect(child) }
                        .padding(
                            start = BocTheme.spacing.space6,
                            end = BocTheme.spacing.space4,
                        )
                        .testTag(sectionRowTag(child.code)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space3),
                ) {
                    Box(
                        modifier = Modifier
                            .size(BULLET_SIZE)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                    )
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = BocTheme.colors.textPrimary,
                    )
                }
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** One icon per section, from section 9.2 of the design document. */
private fun BocSection.iconRes(): Int = when (parentCode ?: code) {
    "1" -> R.drawable.ic_section_general
    "2" -> R.drawable.ic_section_personnel
    "3" -> R.drawable.ic_section_contracting
    "4" -> R.drawable.ic_section_economy
    "5" -> R.drawable.ic_section_expropriation
    "6" -> R.drawable.ic_section_grants
    "7" -> R.drawable.ic_section_announcements
    "8" -> R.drawable.ic_section_judicial
    else -> R.drawable.ic_section_elections
}

private val ROW_MIN_HEIGHT = 72.dp
private val CHILD_MIN_HEIGHT = 56.dp
private val SECTION_ICON_SIZE = 28.dp
private val BULLET_SIZE = 6.dp
private const val CHEVRON_OPEN_DEGREES = 180f
