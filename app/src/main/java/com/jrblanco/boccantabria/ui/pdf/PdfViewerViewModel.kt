package com.jrblanco.boccantabria.ui.pdf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.pdf.PdfDocument
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The viewer's state, and the owner of the open document.
 *
 * It fetches on its own rather than trusting the detail screen already did: the viewer can be
 * reached with the document still missing, and asking again costs nothing because the repository
 * shares a single download.
 */
@Suppress("LongParameterList")
class PdfViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val observePublication: ObservePublicationUseCase,
    observeDocument: ObserveOfficialDocumentUseCase,
    private val openDocument: OpenOfficialDocumentUseCase,
    private val loader: PdfDocumentLoader,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    /**
     * Where to open, 0-based. Comes from the route so a page reference in the AI summary can be
     * followed all the way to the page it names (FR-021). Zero for every other caller.
     */
    val initialPage: Int = savedStateHandle.get<Int>(ARG_PAGE) ?: FIRST_PAGE

    private val externalKey: String = requireNotNull(savedStateHandle[ARG_EXTERNAL_KEY]) {
        "the viewer needs a publication key"
    }

    /** The open handle. Held here so it lives exactly as long as the screen does. */
    private val opened = MutableStateFlow<PdfDocument?>(null)

    /** A file that downloaded correctly but could not be opened. Not the same as a failed fetch. */
    private val unreadable = MutableStateFlow(false)

    private var fetchJob: Job? = null
    private var openJob: Job? = null
    private var publication: Publication? = null

    val uiState: StateFlow<PdfViewerUiState> = combine(
        observePublication(externalKey),
        observeDocument(externalKey),
        opened,
        unreadable,
    ) { publication, status, document, unreadable ->
        when {
            unreadable -> PdfViewerUiState.Error(DomainError.Unknown)
            status is DocumentStatus.Failed -> PdfViewerUiState.Error(status.error)
            document != null && status is DocumentStatus.Available -> PdfViewerUiState.Ready(
                pdf = document,
                document = status.document,
                // The abbreviated title section 24.1 asks for: the issuer is already implied by
                // the document being open, and repeating it would leave no room for the part that
                // says what the announcement is.
                title = publication?.titleWithoutIssuer.orEmpty(),
            )

            else -> PdfViewerUiState.Loading
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = PdfViewerUiState.Loading,
    )

    init {
        viewModelScope.launch {
            observePublication(externalKey).collect { found ->
                publication = found
                if (found != null) ensureFetched(found)
            }
        }
        viewModelScope.launch {
            observeDocument(externalKey).collect { status ->
                if (status is DocumentStatus.Available) ensureOpened(status.document.localPath)
            }
        }
    }

    fun onRetry() {
        fetchJob?.cancel()
        fetchJob = null
        unreadable.value = false
        closeOpen()
        publication?.let { ensureFetched(it) }
    }

    override fun onCleared() {
        closeOpen()
    }

    private fun ensureFetched(publication: Publication) {
        if (fetchJob?.isActive == true) return
        fetchJob = viewModelScope.launch { openDocument(publication) }
    }

    private fun ensureOpened(localPath: String) {
        if (opened.value != null || openJob?.isActive == true) return
        openJob = viewModelScope.launch {
            try {
                opened.value = loader.open(localPath)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                // The bytes are ours and verified, so this is not a network problem: something
                // about the file itself. Saying «check your connection» would send the reader
                // looking in the wrong place.
                crashReporter.log("viewer open failed: ${error.javaClass.simpleName}: ${error.message}")
                unreadable.value = true
            }
        }
    }

    /**
     * Closing cannot take the application down.
     *
     * `onCleared()` runs on the main thread, and `close()` is a synchronous binder call into the
     * sandboxed process. That process dies on its own — it is isolated and short-lived — so closing a
     * document whose process has already gone throws `DeadObjectException`. Uncaught, in `onCleared`,
     * that is a crash on the way out of a screen the reader has already left.
     */
    private fun closeOpen() {
        openJob?.cancel()
        openJob = null
        runCatching { opened.value?.close() }
        opened.value = null
    }

    companion object {
        const val ARG_EXTERNAL_KEY: String = "externalKey"
        const val ARG_PAGE: String = "page"
        const val FIRST_PAGE: Int = 0
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
