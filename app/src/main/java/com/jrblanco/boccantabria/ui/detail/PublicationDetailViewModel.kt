package com.jrblanco.boccantabria.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The detail screen's state.
 *
 * The publication arrives as a key and is read from what is stored, so the screen survives process
 * death, corrects itself when a synchronisation improves the record, and can say "this is no longer
 * here" instead of showing nothing.
 *
 * **The document is not fetched when the screen opens**, only when the document tab is shown. Some
 * people open an announcement just to see what it is about, and spending their data on a PDF they
 * will not read is not a decision to make on their behalf.
 */
@Suppress("LongParameterList")
class PublicationDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val observePublication: ObservePublicationUseCase,
    private val observeDocument: ObserveOfficialDocumentUseCase,
    private val openDocument: OpenOfficialDocumentUseCase,
    private val shareDocument: ShareOfficialDocumentUseCase,
    getSections: GetBocSectionsUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val externalKey: String = requireNotNull(savedStateHandle[ARG_EXTERNAL_KEY]) {
        "the detail screen needs a publication key"
    }

    private val sections = getSections()
    private val selectedTab = MutableStateFlow(
        savedStateHandle.get<String>(KEY_TAB)?.let(DetailTab::valueOf) ?: DetailTab.DOCUMENT,
    )
    private val shareState = MutableStateFlow<ShareState>(ShareState.Idle)

    private var openJob: Job? = null
    private var shareJob: Job? = null

    val uiState: StateFlow<PublicationDetailUiState> = combine(
        observePublication(externalKey),
        observeDocument(externalKey),
        selectedTab,
        shareState,
    ) { publication, document, tab, share ->
        PublicationDetailUiState(
            publication = publication,
            section = publication?.let { sectionOf(it) },
            isMissing = publication == null && hasRead,
            selectedTab = tab,
            document = document,
            share = share,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = PublicationDetailUiState(),
    )

    /**
     * Whether the stored copy has been read at least once. Without it the first frame — before any
     * emission — would be indistinguishable from "the publication is gone", and the screen would
     * flash an explanation that is not true yet.
     */
    private var hasRead: Boolean = false

    init {
        analytics.trackScreenView(SCREEN_NAME)
        viewModelScope.launch {
            observePublication(externalKey).collect { hasRead = true }
        }
    }

    fun onTabSelected(tab: DetailTab) {
        selectedTab.value = tab
        if (tab == DetailTab.DOCUMENT) onDocumentTabShown()
    }

    /** Called when the document tab appears. Fetching twice is safe: one download is shared. */
    fun onDocumentTabShown() {
        val publication = uiState.value.publication ?: return
        if (openJob?.isActive == true) return
        openJob = viewModelScope.launch { openDocument(publication) }
    }

    fun onRetry() {
        openJob?.cancel()
        openJob = null
        onDocumentTabShown()
    }

    fun onShare() {
        val publication = uiState.value.publication ?: return
        if (shareJob?.isActive == true) return

        shareJob = viewModelScope.launch {
            shareState.value = ShareState.Preparing
            shareState.value = when (val result = shareDocument(publication)) {
                is AppResult.Success -> {
                    analytics.track(shareEvent(result.data))
                    ShareState.Ready(result.data)
                }

                is AppResult.Failure -> ShareState.Idle
            }
        }
    }

    /** The screen calls back once it has handed the target to the system. */
    fun onShareConsumed() {
        shareState.value = ShareState.Idle
    }

    private fun sectionOf(publication: Publication) =
        sections.firstOrNull { it.code == publication.classificationCode }

    /** The destination only. Nothing that says which announcement was shared. */
    private fun shareEvent(target: com.jrblanco.boccantabria.domain.model.ShareTarget) = AnalyticsEvent(
        name = EVENT_SHARE,
        parameters = mapOf(
            "target" to when (target) {
                is com.jrblanco.boccantabria.domain.model.ShareTarget.Document -> "document"
                is com.jrblanco.boccantabria.domain.model.ShareTarget.Link -> "link"
            },
        ),
    )

    companion object {
        const val SCREEN_NAME: String = "publication_detail"
        const val ARG_EXTERNAL_KEY: String = "externalKey"
        const val EVENT_SHARE: String = "document_share"
        private const val KEY_TAB = "detail_tab"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
