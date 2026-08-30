package com.jrblanco.boccantabria.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.ui.share.ShareState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The home screen's state.
 *
 * The selection arrives as a navigation argument rather than as a shared object: the sections
 * panel lives above the navigation host and could not reach this view model without inventing a
 * channel between view models. As a bonus the selection survives process death for free, which
 * is what the specification asks for.
 */
@Suppress("LongParameterList")
class HomeViewModel(
    savedStateHandle: SavedStateHandle,
    private val observePublications: ObservePublicationsUseCase,
    private val observeHeader: ObserveBulletinHeaderUseCase,
    private val refreshPublications: RefreshPublicationsUseCase,
    private val getSections: GetBocSectionsUseCase,
    private val shareDocument: ShareOfficialDocumentUseCase,
    private val releaseUnusedDocuments: ReleaseUnusedDocumentsUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val selection: HomeSelection = HomeSelection.of(
        sectionCode = savedStateHandle[ARG_SECTION_CODE],
        subsectionCode = savedStateHandle[ARG_SUBSECTION_CODE],
    )

    private val chips: List<SectionChip> = buildChips()

    private val syncState = MutableStateFlow(SyncState())

    private val shareState = MutableStateFlow<ShareState>(ShareState.Idle)

    /** Guards against a second share while one is being prepared. */
    private var shareJob: Job? = null

    /** Guards against a second synchronisation while one is in flight. */
    private var refreshJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        observePublications(selection),
        observeHeader(selection),
        syncState,
        shareState,
    ) { publications, header, sync, share ->
        HomeUiState(
            selection = selection,
            header = header,
            chips = chips,
            content = contentFor(publications, sync),
            isRefreshing = sync.isRefreshing,
            isOffline = sync.isOffline,
            share = share,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = HomeUiState(selection = selection, chips = chips),
    )

    init {
        analytics.trackScreenView(SCREEN_NAME)
        synchronise(force = false)
    }

    /**
     * Sharing from a card sends the official document, exactly as the detail screen does.
     *
     * The rule of what to send lives in the use case, not here: a second place deciding it would
     * be a second place to keep in step, and this one would drift the first time either changed.
     */
    fun onShare(publication: Publication) {
        if (shareJob?.isActive == true) return

        shareJob = viewModelScope.launch {
            shareState.value = ShareState.Preparing
            shareState.value = when (val result = shareDocument(publication)) {
                is AppResult.Success -> ShareState.Ready(result.data, publication.title)
                // Nothing to say here beyond what the document tab already says when it fails:
                // the screen goes back to rest rather than growing an error of its own.
                is AppResult.Failure -> ShareState.Idle
            }
        }
    }

    /** The screen has handed the share to the system. Cleared so a rotation does not repeat it. */
    fun onShareConsumed() {
        shareState.value = ShareState.Idle
    }

    /** The refresh gesture. Always reaches the network, however fresh the stored copy is. */
    fun onRefresh() = synchronise(force = true)

    fun onRetry() = synchronise(force = true)

    private fun synchronise(force: Boolean) {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            syncState.value = syncState.value.copy(isRefreshing = true)
            syncState.value = when (val result = refreshPublications(force)) {
                is AppResult.Success -> syncState.value.copy(
                    isRefreshing = false,
                    isOffline = result.data.allFailed,
                    error = null,
                    hasSynchronised = true,
                )

                is AppResult.Failure -> syncState.value.copy(
                    isRefreshing = false,
                    isOffline = true,
                    error = result.error,
                    hasSynchronised = true,
                )
            }
            // The bulletin has just changed, so yesterday's documents are the ones nobody is about
            // to open. Done here rather than while one is being read, when deleting the file
            // underneath the reader would be the obvious risk.
            releaseUnusedDocuments()
        }
    }

    /**
     * Stored content always wins. The placeholders only hold while the very first
     * synchronisation is running with nothing to show: showing sources as they land would
     * reshuffle the list and change the header date two or three times in a row.
     */
    private fun contentFor(publications: List<Publication>, sync: SyncState): HomeContentState =
        when {
            publications.isNotEmpty() -> HomeContentState.Publications(publications)
            sync.error != null -> HomeContentState.Error(sync.error)
            !sync.hasSynchronised -> HomeContentState.Skeleton
            else -> HomeContentState.Empty
        }

    /**
     * Only the nine sections. The "everything" chip is added by the screen, because its label is
     * interface copy and a view model has no business reaching for string resources.
     */
    private fun buildChips(): List<SectionChip> {
        val selectedCode = (selection as? HomeSelection.Section)?.code
        return getSections()
            .filter { it.isTopLevel }
            .map { section ->
                SectionChip(
                    code = section.code,
                    label = section.shortName,
                    colorGroup = section.colorGroup,
                    isSelected = section.code == selectedCode ||
                        selectedCode?.startsWith("${section.code}.") == true,
                )
            }
    }

    private data class SyncState(
        val isRefreshing: Boolean = false,
        val isOffline: Boolean = false,
        val error: DomainError? = null,
        val hasSynchronised: Boolean = false,
    )

    companion object {
        const val SCREEN_NAME: String = "home"
        const val ARG_SECTION_CODE: String = "sectionCode"
        const val ARG_SUBSECTION_CODE: String = "subsectionCode"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
