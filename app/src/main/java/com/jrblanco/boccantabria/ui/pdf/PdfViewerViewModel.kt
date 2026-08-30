package com.jrblanco.boccantabria.ui.pdf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.ObserveOfficialDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.OpenOfficialDocumentUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The viewer's state.
 *
 * It fetches on its own rather than trusting that the detail screen already did: the viewer can be
 * reached with the document still missing, and asking again costs nothing because the repository
 * shares a single download.
 */
class PdfViewerViewModel(
    savedStateHandle: SavedStateHandle,
    observePublication: ObservePublicationUseCase,
    observeDocument: ObserveOfficialDocumentUseCase,
    private val openDocument: OpenOfficialDocumentUseCase,
) : ViewModel() {

    private val externalKey: String = requireNotNull(savedStateHandle[ARG_EXTERNAL_KEY]) {
        "the viewer needs a publication key"
    }

    private var openJob: Job? = null
    private var publication: Publication? = null

    val uiState: StateFlow<PdfViewerUiState> = combine(
        observePublication(externalKey),
        observeDocument(externalKey),
    ) { publication, status ->
        this.publication = publication
        when (status) {
            is DocumentStatus.Available ->
                // The abbreviated title the design document asks for in the viewer's bar: the
                // issuer is already implied by the document itself, and repeating it here would
                // leave no room for the part that says what the announcement is.
                PdfViewerUiState.Ready(status.document, publication?.titleWithoutIssuer.orEmpty())

            is DocumentStatus.Failed -> PdfViewerUiState.Error(status.error)

            DocumentStatus.Absent, is DocumentStatus.Downloading -> PdfViewerUiState.Loading
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
                if (found != null) ensureOpen(found)
            }
        }
    }

    fun onRetry() {
        openJob?.cancel()
        openJob = null
        publication?.let { ensureOpen(it) }
    }

    private fun ensureOpen(publication: Publication) {
        if (openJob?.isActive == true) return
        openJob = viewModelScope.launch { openDocument(publication) }
    }

    /**
     * The failure the screen cannot represent: no publication behind the key. Kept explicit so the
     * viewer shows an explanation rather than spinning for ever.
     */
    val missingError: DomainError = DomainError.Unknown

    companion object {
        const val ARG_EXTERNAL_KEY: String = "externalKey"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
