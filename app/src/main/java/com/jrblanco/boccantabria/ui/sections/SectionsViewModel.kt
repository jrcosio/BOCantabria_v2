package com.jrblanco.boccantabria.ui.sections

import androidx.lifecycle.ViewModel
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sections panel.
 *
 * It holds no content of its own — the tree is compiled knowledge — so everything here is about how
 * the tree is presented: what is open and which row is current.
 *
 * There used to be a third thing, what was typed into a filter field. Feature 013 removed the field
 * and with it everything that served it: the query, the matching predicate and the rule that opened
 * a section whose subsections matched. That last one only ever existed **because** there was a
 * filter — a hit hidden behind a closed chevron is a hit nobody finds — so with no filter it had
 * nothing left to refer to.
 */
class SectionsViewModel(
    getSections: GetBocSectionsUseCase,
) : ViewModel() {

    private val sections: List<BocSection> = getSections()
    private val topLevel: List<BocSection> = sections.filter { it.isTopLevel }
    private val childrenOf: Map<String, List<BocSection>> =
        sections.filter { !it.isTopLevel }.groupBy { requireNotNull(it.parentCode) }

    private val rows: List<SectionRow> =
        topLevel.map { section -> SectionRow(section, childrenOf[section.code].orEmpty()) }

    private val _uiState = MutableStateFlow(SectionsUiState(rows = rows))
    val uiState: StateFlow<SectionsUiState> = _uiState.asStateFlow()

    fun onToggleExpanded(sectionCode: String) {
        _uiState.value = _uiState.value.copy(
            expanded = _uiState.value.expanded.toMutableSet().apply {
                if (!add(sectionCode)) remove(sectionCode)
            },
        )
    }

    /** The panel lives above the navigation host, so the current selection is told to it. */
    fun onSelectionChanged(selection: HomeSelection) {
        _uiState.value = _uiState.value.copy(selection = selection)
    }
}
