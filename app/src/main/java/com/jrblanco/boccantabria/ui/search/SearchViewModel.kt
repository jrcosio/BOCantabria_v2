package com.jrblanco.boccantabria.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.domain.model.SearchResults
import com.jrblanco.boccantabria.domain.model.SearchSort
import com.jrblanco.boccantabria.domain.usecase.GetSearchIssuersUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.SearchPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.ui.share.ShareState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The search screen's state.
 *
 * Two things shape it.
 *
 * **The query lives in the `SavedStateHandle`, not just here.** The bottom bar navigates with
 * `popUpTo(start) { saveState = true }`, and that **destroys the view model** when the tab changes;
 * keeping the query only in memory would lose it on the way to the bulletin and back. Writing it
 * through the handle also covers process death for free.
 *
 * **The text key is `query`, deliberately the same one the typed route uses.** `Route.Search`
 * declares a `query` property, so that is where the handed-over term from the bulletin's in-place
 * search arrives: the route seeds it, this model writes over it. Two different keys would break the
 * hand-off **silently** — no error, no exception, just a search screen that opened empty.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    searchPublications: SearchPublicationsUseCase,
    getSearchIssuers: GetSearchIssuersUseCase,
    observeSavedKeys: ObserveSavedKeysUseCase,
    private val setPublicationSaved: SetPublicationSavedUseCase,
    private val shareDocument: ShareOfficialDocumentUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val query = MutableStateFlow(restoreQuery())
    private val shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    private val saveFailed = MutableStateFlow(false)

    /** Guards against a second write while one is in flight. */
    private var saveJob: Job? = null

    /** Guards against a second share while one is being prepared. */
    private var shareJob: Job? = null

    /**
     * The last search already reported, so a state that re-emits for an unrelated reason —somebody
     * saving a result, say— does not report the same search twice.
     *
     * Held in memory only, and never written anywhere: it is the normalised text, which is exactly
     * what must not leave the device.
     */
    private var lastReported: Pair<String, Boolean>? = null

    private val results: Flow<SearchResults> = query
        // Typing is not a search per keystroke. The floor of two characters lives in the use case,
        // so a short query never reaches the store at all.
        .debounce(DEBOUNCE_MILLIS)
        .flatMapLatest { current -> searchPublications(current) }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        results,
        getSearchIssuers(),
        observeSavedKeys().combine(saveFailed) { keys, failed -> keys to failed },
        shareState,
    ) { current, found, issuers, (savedKeys, failed), share ->
        SearchUiState(
            query = current,
            content = contentFor(current, found),
            issuers = issuers,
            share = share,
            savedKeys = savedKeys,
            saveFailed = failed,
        )
    }
        // An effect, and written as one. Reporting from inside the mapping above would hide a side
        // effect in a function that looks pure, and it would run again every time the state
        // re-emitted for an unrelated reason.
        .onEach(::reportSearch)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = SearchUiState(query = query.value),
        )

    init {
        analytics.trackScreenView(SCREEN_NAME)
    }

    // ---------- The query ----------

    fun onQueryChanged(text: String) = update { it.copy(text = text) }

    fun onClearQuery() = update { it.copy(text = "") }

    fun onSortChanged(sort: SearchSort) = update { it.copy(sort = sort) }

    fun onFiltersApplied(filters: SearchQuery) = update { current ->
        // Only the filters and the order travel: the text belongs to the field, not to the sheet.
        current.copy(
            from = filters.from,
            to = filters.to,
            sectionCode = filters.sectionCode,
            subsectionCode = filters.subsectionCode,
            issuer = filters.issuer,
        )
    }

    /** Clearing filters **never** clears the text. That is the requirement that breaks easiest. */
    fun onClearFilters() = update { it.clearedFilters() }

    fun onRemoveDateRange() = update { it.withoutDateRange() }

    fun onRemoveSection() = update { it.withoutSection() }

    fun onRemoveSubsection() = update { it.withoutSubsection() }

    fun onRemoveIssuer() = update { it.withoutIssuer() }

    // ---------- The results ----------

    /**
     * Saving from a result behaves exactly as it does on the bulletin: the state is derived from
     * what is stored, never guessed at, so a write that failed leaves the bookmark as it was.
     */
    fun onToggleSaved(publication: Publication) {
        if (saveJob?.isActive == true) return

        val saved = publication.externalKey !in uiState.value.savedKeys
        saveJob = viewModelScope.launch {
            val result = setPublicationSaved(publication.externalKey, saved = saved)
            if (result is AppResult.Failure) saveFailed.value = true
        }
    }

    fun onSaveFailureConsumed() {
        saveFailed.value = false
    }

    fun onShare(publication: Publication) {
        if (shareJob?.isActive == true) return

        shareJob = viewModelScope.launch {
            shareState.value = ShareState.Preparing
            shareState.value = when (val result = shareDocument(publication)) {
                is AppResult.Success -> ShareState.Ready(result.data, publication.title)
                is AppResult.Failure -> ShareState.Idle
            }
        }
    }

    fun onShareConsumed() {
        shareState.value = ShareState.Idle
    }

    // ---------- Internals ----------

    private fun contentFor(current: SearchQuery, found: SearchResults): SearchContentState = when {
        !current.isRunnable -> SearchContentState.Initial
        found.isEmpty -> SearchContentState.Empty
        else -> SearchContentState.Results(found.items, found.isTruncated)
    }

    /**
     * What happened, never what was typed.
     *
     * The constitution forbids logging personal data, and a query written by hand can carry it — a
     * name, a plate, an address. A bucket answers the only question worth asking, which is whether
     * people find anything, without keeping anybody's words.
     */
    private fun reportSearch(state: SearchUiState) {
        val current = state.query
        if (!current.isRunnable) return

        val fingerprint = current.normalisedText to current.hasFilters
        if (fingerprint == lastReported) return
        lastReported = fingerprint

        val count = when (val content = state.content) {
            is SearchContentState.Results -> content.items.size
            else -> 0
        }

        analytics.track(
            AnalyticsEvent(
                name = EVENT_SEARCH,
                parameters = mapOf(
                    "has_filters" to current.hasFilters.toString(),
                    "results" to bucketOf(count),
                ),
            ),
        )
    }

    private fun update(transform: (SearchQuery) -> SearchQuery) {
        val next = transform(query.value)
        query.value = next
        persist(next)
    }

    private fun persist(current: SearchQuery) {
        savedStateHandle[KEY_QUERY] = current.text
        savedStateHandle[KEY_FROM] = current.from?.toString()
        savedStateHandle[KEY_TO] = current.to?.toString()
        savedStateHandle[KEY_SECTION] = current.sectionCode
        savedStateHandle[KEY_SUBSECTION] = current.subsectionCode
        savedStateHandle[KEY_ISSUER] = current.issuer
        savedStateHandle[KEY_SORT] = current.sort.name
    }

    private fun restoreQuery(): SearchQuery = SearchQuery(
        text = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
        from = savedStateHandle.get<String>(KEY_FROM)?.let(LocalDate::parse),
        to = savedStateHandle.get<String>(KEY_TO)?.let(LocalDate::parse),
        sectionCode = savedStateHandle[KEY_SECTION],
        subsectionCode = savedStateHandle[KEY_SUBSECTION],
        issuer = savedStateHandle[KEY_ISSUER],
        // By name, never `valueOf`: a saved name this version no longer has would otherwise bring
        // the screen down on the one path nobody walks by hand.
        sort = SearchSort.byNameOrDefault(savedStateHandle[KEY_SORT]),
    )

    private fun bucketOf(count: Int): String = when {
        count == 0 -> "0"
        count < 10 -> "1-9"
        count < 100 -> "10-99"
        else -> "100+"
    }

    companion object {
        const val SCREEN_NAME: String = "search"
        const val EVENT_SEARCH: String = "boc_search"

        /** The key the typed route uses for the handed-over term. They must not diverge. */
        const val KEY_QUERY: String = "query"
        const val KEY_FROM: String = "searchFrom"
        const val KEY_TO: String = "searchTo"
        const val KEY_SECTION: String = "searchSection"
        const val KEY_SUBSECTION: String = "searchSubsection"
        const val KEY_ISSUER: String = "searchIssuer"
        const val KEY_SORT: String = "searchSort"

        /** Long enough not to search per keystroke, short enough to feel immediate. */
        const val DEBOUNCE_MILLIS: Long = 250

        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
