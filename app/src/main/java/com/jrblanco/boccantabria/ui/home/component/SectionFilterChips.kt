package com.jrblanco.boccantabria.ui.home.component

import androidx.compose.foundation.layout.Arrangement
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
const val TAG_CHIP_ALL: String = "home_chip_all"

/**
 * The quick filters of section 14.4.
 *
 * The chip that returns to the day's bulletin is added here rather than in the view model: its
 * label is interface copy, and a view model reaching for a string resource is a view model that
 * has stopped being testable without a device.
 */
@Composable
fun SectionFilterChips(
    chips: List<SectionChip>,
    isTodaySelected: Boolean,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_CHIPS),
        contentPadding = PaddingValues(horizontal = BocTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(BocTheme.spacing.space2),
    ) {
        item {
            Chip(
                label = stringResource(R.string.chip_all),
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
}

fun chipTag(code: String): String = "home_chip_$code"

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
