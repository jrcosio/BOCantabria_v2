package com.jrblanco.boccantabria.ui.search.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.SearchSort

const val TAG_SEARCH_SORT: String = "search_sort"

/**
 * Newest or oldest. Two options, and no relevance.
 *
 * A relevance score has to be explainable to whoever is reading the list, and for the volume this
 * application holds, chronological order answers the question people actually ask. Left out by
 * decision, and recorded as such in the specification.
 */
@Composable
fun SortSelector(
    sort: SearchSort,
    onSortChanged: (SearchSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.search_sort_label),
            style = MaterialTheme.typography.labelLarge,
            color = BocTheme.colors.textSecondary,
        )
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(TAG_SEARCH_SORT),
        ) {
            Text(text = stringResource(sort.labelRes()))
            Icon(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = null,
                modifier = Modifier.padding(start = BocTheme.spacing.space1),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SearchSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(option.labelRes())) },
                    onClick = {
                        expanded = false
                        onSortChanged(option)
                    },
                )
            }
        }
    }
}

private fun SearchSort.labelRes(): Int = when (this) {
    SearchSort.NEWEST_FIRST -> R.string.search_sort_newest
    SearchSort.OLDEST_FIRST -> R.string.search_sort_oldest
}
