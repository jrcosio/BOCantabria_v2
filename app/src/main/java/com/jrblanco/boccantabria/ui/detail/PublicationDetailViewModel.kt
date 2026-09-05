package com.jrblanco.boccantabria.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.model.DetailTab
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.GetBocSectionsUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.AcceptAiNoticeUseCase
import com.jrblanco.boccantabria.domain.usecase.GenerateAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiNoticeAcceptedUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiSummaryUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.ReleaseAiDocumentSessionUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import com.jrblanco.boccantabria.domain.usecase.ShareOfficialDocumentUseCase
import com.jrblanco.boccantabria.ui.share.ShareState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
    private val observeSavedKeys: ObserveSavedKeysUseCase,
    private val setPublicationSaved: SetPublicationSavedUseCase,
    private val observeAiSummary: ObserveAiSummaryUseCase,
    private val generateAiSummary: GenerateAiSummaryUseCase,
    private val observeAiNoticeAccepted: ObserveAiNoticeAcceptedUseCase,
    private val acceptAiNotice: AcceptAiNoticeUseCase,
    private val releaseAiDocumentSession: ReleaseAiDocumentSessionUseCase,
    getSections: GetBocSectionsUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val externalKey: String = requireNotNull(savedStateHandle[ARG_EXTERNAL_KEY]) {
        "the detail screen needs a publication key"
    }

    private val sections = getSections()
    private val selectedTab = MutableStateFlow(restoredTab(savedStateHandle))
    private val shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    private val saveFailed = MutableStateFlow(false)
    private val noticePending = MutableStateFlow(false)

    private var openJob: Job? = null
    private var summaryJob: Job? = null
    private var shareJob: Job? = null
    private var saveJob: Job? = null

    val uiState: StateFlow<PublicationDetailUiState> = combine(
        observePublication(externalKey),
        observeDocument(externalKey),
        selectedTab,
        shareState,
        // `isSaved` se **deriva** del conjunto de claves: un flujo propio para un booleano sería un
        // método de repositorio, un caso de uso y una prueba más para lo mismo (research.md D-004).
        // Quinta fuente: todo lo que la persona ha decidido sobre esta publicación. Va agrupado
        // porque `combine` con seis argumentos cae en la sobrecarga de `vararg`, que exige que
        // todos los flujos tengan el mismo tipo y devuelve `Array<Any?>`. Un tipo propio conserva
        // los tipos y dice qué es cada cosa.
        personalState(),
    ) { publication, document, tab, share, personal ->
        PublicationDetailUiState(
            publication = publication,
            section = publication?.let { sectionOf(it) },
            isMissing = publication == null && hasRead,
            selectedTab = tab,
            document = document,
            share = share,
            isSaved = personal.isSaved,
            saveFailed = personal.saveFailed,
            summary = personal.summary,
            aiNoticeAccepted = personal.noticeAccepted,
            aiNoticePending = personal.noticePending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = PublicationDetailUiState(),
    )

    /**
     * The saved mark, the summary and the notice, folded into one flow.
     *
     * `isSaved` is **derived** from the set of keys rather than observed on its own: a dedicated
     * flow for one boolean would be another repository method, another use case and another test
     * for the same fact (research.md D-004).
     */
    private fun personalState(): Flow<PersonalState> = combine(
        observeSavedKeys(),
        saveFailed,
        observeAiSummary(externalKey),
        observeAiNoticeAccepted(),
        noticePending,
    ) { keys, failed, summary, accepted, pending ->
        PersonalState(
            isSaved = externalKey in keys,
            saveFailed = failed,
            summary = summary,
            noticeAccepted = accepted,
            noticePending = pending,
        )
    }

    private data class PersonalState(
        val isSaved: Boolean,
        val saveFailed: Boolean,
        val summary: AiSummaryStatus,
        val noticeAccepted: Boolean,
        val noticePending: Boolean,
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

    /**
     * The tab a previous instance was on, if it still exists.
     *
     * Matched by name rather than with `valueOf`, which throws. A tab can be withdrawn between
     * versions —«Preguntar» was— and a saved name that no longer matches anything would then take
     * the screen down on the way back from process death, on the one path nobody exercises by hand.
     */
    private fun restoredTab(savedStateHandle: SavedStateHandle): DetailTab {
        val saved = savedStateHandle.get<String>(KEY_TAB)
        return DetailTab.entries.firstOrNull { it.name == saved } ?: DetailTab.DOCUMENT
    }

    fun onTabSelected(tab: DetailTab) {
        selectedTab.value = tab
        if (tab == DetailTab.DOCUMENT) onDocumentTabShown()
    }

    // ---------- Resumen IA (feature 007) ----------

    /**
     * **There is deliberately no `onSummaryTabShown()`.** Showing the tab generates nothing: the
     * allowance is shared and daily, and spending it on publications nobody asked about would empty
     * it in an afternoon (FR-002, SC-004). Opening the tab only observes what is already stored.
     *
     * The first time, this opens the notice instead of working: the text of the document leaves the
     * device, and finding that out afterwards is finding out too late (FR-043).
     */
    fun onGenerateSummary() {
        if (!uiState.value.aiNoticeAccepted) {
            noticePending.value = true
            return
        }
        generate(force = false)
    }

    /** Regenerating is always explicit, and always costs a request (FR-034). */
    fun onRegenerateSummary() = generate(force = true)

    fun onAiNoticeAccepted() {
        noticePending.value = false
        viewModelScope.launch {
            acceptAiNotice()
            generate(force = false)
        }
    }

    /** Cancelling sends nothing at all (FR-044). */
    fun onAiNoticeDismissed() {
        noticePending.value = false
    }

    /**
     * The summary as plain text, **with the warning in front of it**.
     *
     * A summary that leaves the application loses its frame: it arrives in a chat without the card,
     * without the mark and without the screen around it. If the warning is not inside the text, it
     * is not there at all (FR-025).
     *
     * Returns `null` when there is no summary to hand over, so the screen does nothing rather than
     * copying an empty string.
     */
    fun summaryAsSharableText(disclaimer: String): String? {
        val ready = uiState.value.summary as? AiSummaryStatus.Ready ?: return null
        val summary = ready.summary

        return buildString {
            appendLine(disclaimer)
            appendLine()
            uiState.value.publication?.let { appendLine(it.titleWithoutIssuer); appendLine() }
            appendLine(summary.plainLanguageSummary)
            if (summary.keyPoints.isNotEmpty()) {
                appendLine()
                summary.keyPoints.forEach { point -> appendLine("- ${point.text}") }
            }
        }.trimEnd()
    }

    private fun generate(force: Boolean) {
        val publication = uiState.value.publication ?: return
        // A second tap while one is running is the same operation. The repository would share the
        // request anyway; not launching a second job keeps the cancellation story simple (FR-005).
        if (summaryJob?.isActive == true) return
        summaryJob = viewModelScope.launch { generateAiSummary(publication, force) }
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
                    ShareState.Ready(result.data, publication.title)
                }

                is AppResult.Failure -> ShareState.Idle
            }
        }
    }

    /** The screen calls back once it has handed the target to the system. */
    fun onShareConsumed() {
        shareState.value = ShareState.Idle
    }

    /**
     * Saves the publication, or takes it off the list if it was already on it.
     *
     * Does nothing when there is no publication: an announcement that is no longer stored cannot be
     * saved, and the screen does not offer the action then either (FR-008).
     */
    fun onToggleSaved() {
        val publication = uiState.value.publication ?: return
        if (saveJob?.isActive == true) return

        val saved = !uiState.value.isSaved
        saveJob = viewModelScope.launch {
            val result = setPublicationSaved(publication.externalKey, saved)
            if (result is AppResult.Failure) saveFailed.value = true
        }
    }

    /** The screen has said the write failed. Cleared so a rotation does not repeat it. */
    fun onSaveFailureConsumed() {
        saveFailed.value = false
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
        /** Internal, but visible to the test that a withdrawn tab does not take the screen down. */
        const val KEY_TAB: String = "detail_tab"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
    /**
     * Leaving the publication lets go of the document prepared in the service.
     *
     * **This** is the moment, and not the asking screen's or the viewer's: both are pushed on top of
     * this entry, so it stays alive while they are used and is only cleared on the way back out. That
     * asking is only reachable through here is what makes one point enough.
     *
     * The call is not suspending and it cannot be: by the time `onCleared()` runs, `viewModelScope`
     * is already cancelled and anything launched into it would never run. The store keeps a scope of
     * its own for exactly this (FR-009, 010 research.md D-208).
     */
    override fun onCleared() {
        releaseAiDocumentSession(externalKey)
    }

}
