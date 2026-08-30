package com.jrblanco.boccantabria.ui.detail.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jrblanco.boccantabria.R
import com.jrblanco.boccantabria.core.ui.theme.BocTheme
import com.jrblanco.boccantabria.domain.model.DetailTab

const val TAG_DETAIL_TABS: String = "detail_tabs"
const val TAG_TAB_DOCUMENT: String = "detail_tab_document"
const val TAG_TAB_SUMMARY: String = "detail_tab_summary"
const val TAG_TAB_ASK: String = "detail_tab_ask"

/**
 * The three tabs of section 18.4.
 *
 * The two unbuilt ones are selectable rather than disabled: their content says «Próximamente» in
 * its own voice, which tells the reader what is coming. A greyed-out tab says only that something
 * is broken.
 */
@Composable
fun DetailTabs(
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier.testTag(TAG_DETAIL_TABS),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { positions ->
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(positions[selected.ordinal]),
                height = INDICATOR_HEIGHT,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        DetailTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag(tab.testTag),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = BocTheme.colors.textSecondary,
            ) {
                TabLabel(tab)
            }
        }
    }
}

@Composable
private fun TabLabel(tab: DetailTab) {
    Row(
        modifier = Modifier.padding(
            horizontal = BocTheme.spacing.space3,
            vertical = BocTheme.spacing.space3,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only the summary carries the AI mark, per section 18.4. «Preguntar» keeps its identity
        // through the action bar's own icon, and two sparkles side by side would say nothing.
        if (tab == DetailTab.AI_SUMMARY) {
            Icon(
                painter = painterResource(R.drawable.ic_ai),
                contentDescription = null,
                tint = BocTheme.colors.aiAccent,
                modifier = Modifier
                    .size(TAB_ICON_SIZE)
                    .padding(end = 0.dp),
            )
        }
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = if (tab == DetailTab.AI_SUMMARY) BocTheme.spacing.space2 else 0.dp),
        )
    }
}

private val DetailTab.labelRes: Int
    get() = when (this) {
        DetailTab.DOCUMENT -> R.string.detail_tab_document
        DetailTab.AI_SUMMARY -> R.string.detail_tab_summary
        DetailTab.ASK -> R.string.detail_tab_ask
    }

private val DetailTab.testTag: String
    get() = when (this) {
        DetailTab.DOCUMENT -> TAG_TAB_DOCUMENT
        DetailTab.AI_SUMMARY -> TAG_TAB_SUMMARY
        DetailTab.ASK -> TAG_TAB_ASK
    }

private val INDICATOR_HEIGHT = 3.dp
private val TAB_ICON_SIZE = 16.dp
