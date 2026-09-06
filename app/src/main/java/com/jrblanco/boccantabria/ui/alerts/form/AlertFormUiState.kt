package com.jrblanco.boccantabria.ui.alerts.form

import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AlertRuleValidationError
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.KeywordRejection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SectionSelection

/**
 * What the form draws. Loading only while an existing rule is read; then ready until saved.
 */
sealed interface AlertFormUiState {

    data object Loading : AlertFormUiState

    /**
     * @param keywordRejection one-shot: why the last keyword was refused, until the screen clears it.
     * @param sectionParts the summary of the chosen sections; `null` means every section.
     * @param previewCount `null` while unknown or while the draft has no criterion.
     * @param saveFailed one-shot.
     */
    data class Ready(
        val draft: AlertRuleDraft,
        val errors: Set<AlertRuleValidationError>,
        val keywordRejection: KeywordRejection?,
        val sectionRows: List<SectionPickerRow>,
        val sectionParts: List<SectionSelection.Part>?,
        val selectedLeafCount: Int,
        val organizationSuggestions: List<String>,
        val isEdit: Boolean,
        val isSaving: Boolean,
        val sectionsOpen: Boolean,
        val previewCount: Int?,
        val previewOpen: Boolean,
        val preview: List<Publication>,
        val saveFailed: Boolean,
    ) : AlertFormUiState {
        val canSave: Boolean get() = errors.isEmpty() && !isSaving
    }

    /**
     * Saved. [requestPermission] is true exactly once per installation's first enabled rule when
     * Android has not yet been asked for the permission (research.md D-428).
     */
    data class Saved(val requestPermission: Boolean) : AlertFormUiState
}

/** One row of the section picker: a section, its children, and the state its checkbox shows. */
data class SectionPickerRow(
    val section: BocSection,
    val children: List<BocSection>,
    val state: SectionSelection.ToggleState,
)
