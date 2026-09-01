package com.jrblanco.boccantabria.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.FilterPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveBulletinHeaderUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.RefreshPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseUnusedDocumentsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.ui.share.ShareState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val filterPublications: FilterPublicationsUseCase,
    private val observeSavedKeys: ObserveSavedKeysUseCase,
    private val setPublicationSaved: SetPublicationSavedUseCase,
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

    /**
     * Everything the screen owns by itself: what is being shared, whether a write failed, and the
     * in-place search.
     *
     * One flow rather than three. The `combine` below already used its five-argument overload, and
     * the note that used to sit there warned that a sixth would force the list form and change the
     * type of the block. Folding what was already local state into a single value brings the arity
     * **down** instead of up, and these three change together anyway.
     */
    private val local = MutableStateFlow(LocalState())

    /** Guards against a second share while one is being prepared. */
    private var shareJob: Job? = null

    /** Guards against a second synchronisation while one is in flight. */
    private var refreshJob: Job? = null

    /** Guards against a second write while one is in flight. */
    private var saveJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        observePublications(selection),
        observeHeader(selection),
        syncState,
        observeSavedKeys(),
        local,
    ) { publications, header, sync, savedKeys, own ->
        HomeUiState(
            selection = selection,
            header = header,
            chips = chips,
            content = contentFor(publications, sync, own.search),
            isRefreshing = sync.isRefreshing,
            isOffline = sync.isOffline,
            share = own.share,
            savedKeys = savedKeys,
            saveFailed = own.saveFailed,
            search = own.search,
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
     * The magnifier, from here down.
     */

    /**
     * Opens the in-place search.
     *
     * It navigates nowhere and asks for nothing: what it narrows is already in memory and already
     * on screen. Reopening always starts blank, because [onSearchClosed] clears the text — a filter
     * still applied but no longer visible is worse than no filter at all.
     */
    fun onSearchOpened() {
        local.update { it.copy(search = HomeSearchState(isOpen = true)) }
    }

    fun onSearchQueryChanged(query: String) {
        local.update { it.copy(search = it.search.copy(query = query)) }
    }

    fun onSearchClosed() {
        local.update { it.copy(search = HomeSearchState()) }
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
            local.update { it.copy(share = ShareState.Preparing) }
            val next = when (val result = shareDocument(publication)) {
                is AppResult.Success -> ShareState.Ready(result.data, publication.title)
                // Nothing to say here beyond what the document tab already says when it fails:
                // the screen goes back to rest rather than growing an error of its own.
                is AppResult.Failure -> ShareState.Idle
            }
            local.update { it.copy(share = next) }
        }
    }

    /** The screen has handed the share to the system. Cleared so a rotation does not repeat it. */
    fun onShareConsumed() {
        local.update { it.copy(share = ShareState.Idle) }
    }

    /**
     * Saves the publication, or takes it off the list if it was already on it.
     *
     * The value is worked out from what the state is showing rather than read back from the store: a
     * read before the write would add a round trip and a race to serve a case the interface never
     * produces, because the icon always shows the current state.
     */
    fun onToggleSaved(publication: Publication) {
        if (saveJob?.isActive == true) return

        val saved = publication.externalKey !in uiState.value.savedKeys
        saveJob = viewModelScope.launch {
            val result = setPublicationSaved(publication.externalKey, saved)
            if (result is AppResult.Failure) local.update { it.copy(saveFailed = true) }
        }
    }

    /** The screen has said the write failed. Cleared so a rotation does not repeat it. */
    fun onSaveFailureConsumed() {
        local.update { it.copy(saveFailed = false) }
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
    /**
     * What the list holds, once the in-place search has had its say.
     *
     * The filtering happens **here** and not in the composable: the constitution keeps logic out of
     * the drawing, and the rule itself lives one layer further in, in `FilterPublicationsUseCase`,
     * where it has a test of its own.
     */
    private fun contentFor(
        publications: List<Publication>,
        sync: SyncState,
        search: HomeSearchState,
    ): HomeContentState {
        if (search.isFiltering && publications.isNotEmpty()) {
            val matches = filterPublications(publications, search.query)
            return if (matches.isEmpty()) {
                HomeContentState.NoSearchResults(search.query)
            } else {
                HomeContentState.Publications(matches)
            }
        }
        return contentWithoutSearch(publications, sync)
    }

    private fun contentWithoutSearch(publications: List<Publication>, sync: SyncState): HomeContentState =
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

    /**
     * What the screen owns: the share in flight, a failed write, and the in-place search.
     *
     * Grouped so the `combine` above stays within its five-argument overload. They are all
     * short-lived screen state, none of them comes from the store, and none of them survives the
     * view model.
     */
    private data class LocalState(
        val share: ShareState = ShareState.Idle,
        val saveFailed: Boolean = false,
        val search: HomeSearchState = HomeSearchState(),
    )

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
