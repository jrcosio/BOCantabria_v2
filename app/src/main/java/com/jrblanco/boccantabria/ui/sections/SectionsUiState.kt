package com.jrblanco.boccantabria.ui.sections

import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.HomeSelection

/**
 * What the sections panel draws.
 *
 * [expanded] is the **effective** set, not the one the person toggled: filtering by text opens
 * the sections whose subsections match, because a match hidden behind a closed chevron is a
 * match the person will never find.
 */
data class SectionsUiState(
    val query: String = "",
    val rows: List<SectionRow> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val selection: HomeSelection = HomeSelection.TodaysBulletin,
)

data class SectionRow(
    val section: BocSection,
    val children: List<BocSection>,
) {
    val isExpandable: Boolean get() = children.isNotEmpty()
}
