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
 * It holds no content of its own — the tree is compiled knowledge — so everything here is about
 * how the tree is presented: what is typed, what is open, and which row is current.
 */
class SectionsViewModel(
    getSections: GetBocSectionsUseCase,
) : ViewModel() {

    private val sections: List<BocSection> = getSections()
    private val topLevel: List<BocSection> = sections.filter { it.isTopLevel }
    private val childrenOf: Map<String, List<BocSection>> =
        sections.filter { !it.isTopLevel }.groupBy { requireNotNull(it.parentCode) }

    private val query = MutableStateFlow("")
    private val toggled = MutableStateFlow(emptySet<String>())

    private val _uiState = MutableStateFlow(stateFor(query = "", toggled = emptySet()))
    val uiState: StateFlow<SectionsUiState> = _uiState.asStateFlow()

    fun onQueryChanged(value: String) {
        query.value = value
        _uiState.value = stateFor(value, toggled.value, _uiState.value.selection)
    }

    fun onToggleExpanded(sectionCode: String) {
        toggled.value = toggled.value.toMutableSet().apply {
            if (!add(sectionCode)) remove(sectionCode)
        }
        _uiState.value = stateFor(query.value, toggled.value, _uiState.value.selection)
    }

    /** The panel lives above the navigation host, so the current selection is told to it. */
    fun onSelectionChanged(selection: HomeSelection) {
        _uiState.value = _uiState.value.copy(selection = selection)
    }

    private fun stateFor(
        query: String,
        toggled: Set<String>,
        selection: HomeSelection = HomeSelection.TodaysBulletin,
    ): SectionsUiState {
        val needle = query.trim()
        val rows = topLevel.mapNotNull { section ->
            val children = childrenOf[section.code].orEmpty()
            if (needle.isEmpty()) return@mapNotNull SectionRow(section, children)

            val matchingChildren = children.filter { it.matches(needle) }
            when {
                section.matches(needle) -> SectionRow(section, children)
                matchingChildren.isNotEmpty() -> SectionRow(section, matchingChildren)
                else -> null
            }
        }

        // While filtering, a section whose subsections matched is opened on its own: leaving the
        // match behind a closed chevron would be the same as not finding it.
        val expanded = if (needle.isEmpty()) {
            toggled
        } else {
            toggled + rows.filter { it.isExpandable && !it.section.matches(needle) }
                .map { it.section.code }
        }

        return SectionsUiState(query = query, rows = rows, expanded = expanded, selection = selection)
    }

    private fun BocSection.matches(needle: String): Boolean =
        name.contains(needle, ignoreCase = true) ||
            shortName.contains(needle, ignoreCase = true) ||
            code.startsWith(needle)
}
