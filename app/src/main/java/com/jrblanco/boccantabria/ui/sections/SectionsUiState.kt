package com.jrblanco.boccantabria.ui.sections

import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.HomeSelection

/**
 * What the sections panel draws.
 *
 * [expanded] is exactly what the person has opened, and nothing else. Until feature 013 it was the
 * **effective** set, which also carried the sections a text filter had opened on their own; that
 * filter is gone, and with it the only reason the two ever differed.
 */
data class SectionsUiState(
    val rows: List<SectionRow> = emptyList(),
    val expanded: Set<String> = emptySet(),
    /**
     * The panel lives above the navigation host, so it is told what is currently selected rather
     * than reading it. Nothing to do with the filter that was removed: this stays.
     */
    val selection: HomeSelection = HomeSelection.TodaysBulletin,
)

data class SectionRow(
    val section: BocSection,
    val children: List<BocSection>,
) {
    val isExpandable: Boolean get() = children.isNotEmpty()
}
