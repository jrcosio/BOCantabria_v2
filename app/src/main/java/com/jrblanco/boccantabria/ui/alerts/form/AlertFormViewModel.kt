package com.jrblanco.boccantabria.ui.alerts.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.util.SearchText
import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.model.KeywordAddition
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.domain.model.KeywordRejection
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SectionSelection
import com.jrblanco.boccantabria.domain.usecase.CountAlertRulesUseCase
import com.jrblanco.boccantabria.domain.usecase.GetAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetNotificationStatusUseCase
import com.jrblanco.boccantabria.domain.usecase.GetSearchIssuersUseCase
import com.jrblanco.boccantabria.domain.usecase.PreviewAlertRuleUseCase
import com.jrblanco.boccantabria.domain.usecase.SaveAlertRuleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The form's state: a draft the person edits, what stops it from being saved, and what it would
 * already match.
 *
 * Creating, editing and duplicating are the same screen. The two arguments arrive through the saved
 * state under the same names the route uses — `ruleId`, `duplicateOf` — so the hand-off cannot break in
 * silence. The rules of what can be saved live in [AlertRuleDraft], not here (principle III).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class AlertFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val getRule: GetAlertRuleUseCase,
    private val saveRule: SaveAlertRuleUseCase,
    private val countRules: CountAlertRulesUseCase,
    private val getNotificationStatus: GetNotificationStatusUseCase,
    getSections: GetBocSectionsUseCase,
    getSearchIssuers: GetSearchIssuersUseCase,
    private val previewRule: PreviewAlertRuleUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val ruleId: String? = savedStateHandle[ARG_RULE_ID]
    private val duplicateOf: String? = savedStateHandle[ARG_DUPLICATE_OF]
    private val sections: List<BocSection> = getSections()

    /** `null` while an existing rule is being read. */
    private val draft = MutableStateFlow<AlertRuleDraft?>(null)
    private val local = MutableStateFlow(LocalState())

    private var saveJob: Job? = null

    /** Re-run whenever the draft settles; never writes anything (FR-068). */
    private val preview: Flow<List<Publication>> = draft
        .filterNotNull()
        .debounce(PREVIEW_DEBOUNCE_MILLIS)
        .mapLatest { current -> if (current.hasCriteria) previewRule(current) else emptyList() }
        .onStart { emit(emptyList()) }

    val uiState: StateFlow<AlertFormUiState> = combine(
        draft,
        local,
        getSearchIssuers(),
        preview,
    ) { current, own, issuers, found ->
        when {
            own.saved != null -> own.saved
            current == null -> AlertFormUiState.Loading
            else -> AlertFormUiState.Ready(
                draft = current,
                errors = current.validate(),
                keywordRejection = own.keywordRejection,
                sectionRows = sectionRows(current.sectionCodes),
                sectionParts = SectionSelection.summaryParts(current.sectionCodes, sections),
                selectedLeafCount = SectionSelection.leafCount(current.sectionCodes, sections),
                organizationSuggestions = suggestions(issuers, current.organizationQuery),
                isEdit = ruleId != null,
                isSaving = own.isSaving,
                sectionsOpen = own.sectionsOpen,
                previewCount = if (current.hasCriteria) found.size else null,
                previewOpen = own.previewOpen,
                preview = found,
                saveFailed = own.saveFailed,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = AlertFormUiState.Loading,
    )

    init {
        analytics.trackScreenView(SCREEN_NAME)
        viewModelScope.launch { draft.value = initialDraft() }
    }

    fun onNameChanged(value: String) = updateDraft { it.copy(name = value.take(AlertRuleDraft.NAME_MAX_LENGTH + 1)) }

    /** Adds, or says why not. A blank name takes the first criterion as a proposal (FR-017). */
    fun onKeywordAdded(raw: String) {
        val current = draft.value ?: return
        when (val outcome = current.addingKeyword(raw)) {
            is KeywordAddition.Added -> {
                draft.value = outcome.draft.withSuggestedNameIfBlank()
                local.update { it.copy(keywordRejection = null) }
            }
            is KeywordAddition.Rejected -> local.update { it.copy(keywordRejection = outcome.reason) }
        }
    }

    fun onKeywordRemoved(keyword: String) = updateDraft { it.removingKeyword(keyword) }

    fun onKeywordRejectionConsumed() = local.update { it.copy(keywordRejection = null) }

    fun onMatchModeChanged(mode: KeywordMatchMode) = updateDraft { it.copy(matchMode = mode) }

    fun onSectionsOpened() = local.update { it.copy(sectionsOpen = true) }

    fun onSectionsClosed() = local.update { it.copy(sectionsOpen = false) }

    /** Toggles a parent or a leaf; the hierarchy rule lives in the model (FR-021). */
    fun onSectionToggled(code: String) = updateDraft {
        it.copy(sectionCodes = SectionSelection.toggled(it.sectionCodes, code, sections)).withSuggestedNameIfBlank()
    }

    fun onAllSectionsSelected() = updateDraft { it.copy(sectionCodes = emptySet()) }

    fun onOrganizationChanged(value: String) = updateDraft { it.copy(organizationQuery = value) }

    fun onOrganizationChosen(value: String) = updateDraft { it.copy(organizationQuery = value).withSuggestedNameIfBlank() }

    fun onEnabledChanged(enabled: Boolean) = updateDraft { it.copy(isEnabled = enabled) }

    fun onPreviewOpened() = local.update { it.copy(previewOpen = true) }

    fun onPreviewClosed() = local.update { it.copy(previewOpen = false) }

    fun onSaveFailureConsumed() = local.update { it.copy(saveFailed = false) }

    /**
     * Saves, and decides whether the permission is asked for right after: only for the first rule of
     * the installation, only if it is enabled, and only while Android still has to be asked
     * (research.md D-428).
     */
    fun onSave() {
        val current = draft.value ?: return
        if (!current.isValid || saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            local.update { it.copy(isSaving = true, saveFailed = false) }
            val firstRule = ruleId == null && countRules() == 0
            when (saveRule(current, ruleId)) {
                is AppResult.Success -> {
                    val request = firstRule && current.isEnabled &&
                        getNotificationStatus() == NotificationStatus.NEEDS_REQUEST
                    local.update { it.copy(isSaving = false, saved = AlertFormUiState.Saved(requestPermission = request)) }
                }
                is AppResult.Failure -> local.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** What the person answered to Android. A flag, never anything about the rule (FR-069). */
    fun onPermissionResult(granted: Boolean) {
        analytics.track(AnalyticsEvent(EVENT_PERMISSION, mapOf("granted" to granted.toString())))
    }

    private suspend fun initialDraft(): AlertRuleDraft {
        ruleId?.let { id -> getRule(id)?.let { return AlertRuleDraft.from(it) } }
        duplicateOf?.let { id -> getRule(id)?.let { return AlertRuleDraft.duplicateOf(it) } }
        return AlertRuleDraft()
    }

    private fun updateDraft(transform: (AlertRuleDraft) -> AlertRuleDraft) {
        draft.update { current -> current?.let(transform) }
    }

    private fun AlertRuleDraft.withSuggestedNameIfBlank(): AlertRuleDraft =
        if (name.isBlank()) copy(name = suggestedName(sections).orEmpty()) else this

    private fun sectionRows(selected: Set<String>): List<SectionPickerRow> {
        val childrenOf = sections.filter { !it.isTopLevel }.groupBy { requireNotNull(it.parentCode) }
        return sections.filter { it.isTopLevel }.sortedBy { it.order }.map { section ->
            SectionPickerRow(
                section = section,
                children = childrenOf[section.code].orEmpty().sortedBy { it.order },
                state = SectionSelection.stateOf(section, sections, selected),
            )
        }
    }

    /** Stored issuers that contain what is typed, normalised. Nothing typed offers nothing. */
    private fun suggestions(issuers: List<String>, typed: String): List<String> {
        val needle = SearchText.normalise(typed)
        if (needle.isEmpty()) return emptyList()
        return issuers
            .filter { SearchText.normalise(it).contains(needle) && SearchText.normalise(it) != needle }
            .take(MAX_SUGGESTIONS)
    }

    private data class LocalState(
        val keywordRejection: KeywordRejection? = null,
        val isSaving: Boolean = false,
        val sectionsOpen: Boolean = false,
        val previewOpen: Boolean = false,
        val saveFailed: Boolean = false,
        val saved: AlertFormUiState.Saved? = null,
    )

    companion object {
        const val SCREEN_NAME: String = "alert_form"
        const val EVENT_PERMISSION: String = "alert_permission"

        /** Same names as the route's properties: the keys the arguments arrive under. */
        const val ARG_RULE_ID: String = "ruleId"
        const val ARG_DUPLICATE_OF: String = "duplicateOf"

        const val PREVIEW_DEBOUNCE_MILLIS: Long = 300L
        private const val MAX_SUGGESTIONS = 8
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
