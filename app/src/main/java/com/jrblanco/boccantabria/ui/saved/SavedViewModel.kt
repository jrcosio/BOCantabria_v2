package com.jrblanco.boccantabria.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedPublicationsUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
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
 * The saved screen's state.
 *
 * Takes no navigation argument: this is not a selection of the bulletin but a list of the person's
 * own, so there is no section code to read and no editorial header to build.
 *
 * The order is **not** applied here. It comes from the store, which is the only place that knows
 * when each mark was made; a screen that sorted would be a second place deciding it.
 */
class SavedViewModel(
    observeSaved: ObserveSavedPublicationsUseCase,
    private val setPublicationSaved: SetPublicationSavedUseCase,
    private val shareDocument: ShareOfficialDocumentUseCase,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    private val saveFailed = MutableStateFlow(false)

    /** Guards against a second write while one is in flight. */
    private var saveJob: Job? = null

    /** Guards against a second share while one is being prepared. */
    private var shareJob: Job? = null

    val uiState: StateFlow<SavedUiState> = combine(
        observeSaved(),
        shareState,
        saveFailed,
    ) { publications, share, failed ->
        SavedUiState(
            content = if (publications.isEmpty()) {
                SavedContentState.Empty
            } else {
                SavedContentState.Publications(publications)
            },
            share = share,
            saveFailed = failed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = SavedUiState(),
    )

    init {
        analytics.trackScreenView(SCREEN_NAME)
    }

    /**
     * Everything this screen shows is saved, so the gesture can only be taking it off. Asked of the
     * use case as an explicit value rather than as a toggle: the screen already knows which state it
     * is drawing, and reading before writing would add a round trip and a race for nothing.
     */
    fun onToggleSaved(publication: Publication) {
        if (saveJob?.isActive == true) return

        saveJob = viewModelScope.launch {
            val result = setPublicationSaved(publication.externalKey, saved = false)
            if (result is AppResult.Failure) saveFailed.value = true
        }
    }

    /** The screen has said the write failed. Cleared so a rotation does not repeat it. */
    fun onSaveFailureConsumed() {
        saveFailed.value = false
    }

    /**
     * Sharing from a saved card sends the official document, exactly as it does from the bulletin.
     * The rule of what to send lives in the use case, not here.
     */
    fun onShare(publication: Publication) {
        if (shareJob?.isActive == true) return

        shareJob = viewModelScope.launch {
            shareState.value = ShareState.Preparing
            shareState.value = when (val result = shareDocument(publication)) {
                is AppResult.Success -> ShareState.Ready(result.data, publication.title)
                // Nothing to add beyond what the document tab already says when it fails: the
                // screen goes back to rest rather than growing an error of its own.
                is AppResult.Failure -> ShareState.Idle
            }
        }
    }

    /** The screen has handed the share to the system. Cleared so a rotation does not repeat it. */
    fun onShareConsumed() {
        shareState.value = ShareState.Idle
    }

    companion object {
        const val SCREEN_NAME: String = "saved"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
