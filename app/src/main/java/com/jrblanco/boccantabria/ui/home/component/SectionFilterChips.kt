package com.jrblanco.boccantabria.ui.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.ui.home.SectionChip

const val TAG_CHIPS: String = "home_chips"
const val TAG_SUBCHIPS: String = "home_subchips"
const val TAG_CHIP_WHOLE_SECTION: String = "home_chip_whole_section"

/**
 * The chip that returns to the day's bulletin.
 *
 * The name stays `ALL` although the label no longer says «Todo»: three instrumented classes reach
 * for this tag to get back to the bulletin, and renaming it would turn a change of copy into a
 * change of three test classes for nothing.
 */
const val TAG_CHIP_ALL: String = "home_chip_all"

/**
 * The quick filters of section 14.4.
 *
 * The chip that returns to the day's bulletin is added here rather than in the view model: its
 * label is interface copy, and a view model reaching for a string resource is a view model that
 * has stopped being testable without a device.
 *
 * That chip says «Boletín de hoy» and not «Todo», which is what it used to say. The word was a
 * promise the query never made: it shows the most recent published date across every section, while
 * a section chip shows that section's whole archive with no date restriction. Somebody comparing 39
 * announcements here with 336 under «Personal» concluded the application was losing data — and was
 * reading the label correctly. The behaviour was right; the word was wrong (feature 013, FR-001).
 *
 * Since that same feature there is a **second row**, with the subsections of whichever section is
 * selected. It is derived from the selection and holds no state of its own: it exists when
 * [subsections] is not empty and it does not otherwise. Its first entry returns to the whole
 * section, and like the bulletin chip it is added here because its label is interface copy.
 */
@Composable
@Suppress("LongParameterList")
fun SectionFilterChips(
    chips: List<SectionChip>,
    isTodaySelected: Boolean,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    subsections: List<SectionChip> = emptyList(),
    sectionCode: String? = null,
    isWholeSectionSelected: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_CHIPS),
            contentPadding = PaddingValues(horizontal = BocTheme.spacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
        ) {
            item {
                Chip(
                    label = stringResource(R.string.chip_todays_bulletin),
                    selected = isTodaySelected,
                    onClick = { onSelect(null) },
                    modifier = Modifier.testTag(TAG_CHIP_ALL),
                )
            }
            items(items = chips, key = { it.code }) { chip ->
                Chip(
                    label = chip.label,
                    selected = chip.isSelected,
                    onClick = { onSelect(chip.code) },
                    modifier = Modifier.testTag(chipTag(chip.code)),
                )
            }
        }

        if (subsections.isNotEmpty() && sectionCode != null) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_SUBCHIPS),
                contentPadding = PaddingValues(horizontal = BocTheme.spacing.screenMargin),
                horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
            ) {
                item {
                    SubsectionChip(
                        label = stringResource(R.string.chip_whole_section),
                        selected = isWholeSectionSelected,
                        onClick = { onSelect(sectionCode) },
                        modifier = Modifier.testTag(TAG_CHIP_WHOLE_SECTION),
                    )
                }
                items(items = subsections, key = { it.code }) { chip ->
                    SubsectionChip(
                        label = chip.label,
                        selected = chip.isSelected,
                        onClick = { onSelect(chip.code) },
                        modifier = Modifier.testTag(chipTag(chip.code)),
                    )
                }
            }
        }
    }
}

fun chipTag(code: String): String = "home_chip_$code"

/**
 * One chip of the second row.
 *
 * Lighter than [Chip] on purpose: the hierarchy has to say that this row depends on the one above,
 * and a hierarchy is said with weight and colour. A divider or an indent would say the opposite —
 * that these are two separate lists — and the indent would be lost the moment the row scrolls. The
 * resting fill is `surfaceSoft`, the same one the sections panel uses for its expanded subsections:
 * one vocabulary for the two places where subsections appear.
 */
@Composable
private fun SubsectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = BocTheme.colors.surfaceSoft,
            labelColor = BocTheme.colors.textSecondary,
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.secondary,
        ),
    )
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = BocTheme.colors.textPrimary,
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.secondary,
        ),
    )
}
