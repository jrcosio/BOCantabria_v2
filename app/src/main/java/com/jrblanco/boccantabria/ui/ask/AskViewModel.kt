package com.jrblanco.boccantabria.ui.ask

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrblanco.boccantabria.domain.model.AiChatConstants
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.usecase.AcceptAiNoticeUseCase
import com.jrblanco.boccantabria.domain.usecase.AskAboutDocumentUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiAvailabilityUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiConversationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveAiNoticeAcceptedUseCase
import com.jrblanco.boccantabria.domain.usecase.ObservePublicationUseCase
import com.jrblanco.boccantabria.domain.usecase.ObserveSavedKeysUseCase
import com.jrblanco.boccantabria.domain.usecase.RetryLastQuestionUseCase
import com.jrblanco.boccantabria.domain.usecase.SetPublicationSavedUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The conversation screen's state.
 *
 * ### Why the flows are grouped
 *
 * Seven things feed this screen: the publication, whether it is saved, the conversation, whether the
 * notice was accepted, whether the service is configured, the draft and whether the notice is open.
 * **`combine` with more than five flows falls into the `vararg` overload**, which requires them all to
 * have the same type and hands back `Array<Any?>`. `PublicationDetailViewModel` hit that wall and
 * solved it by grouping; this does the same rather than rediscovering it (011 research.md D-327b).
 *
 * ### The conversation does not live here
 *
 * It lives in the repository, because it has to survive going back to the detail and returning while
 * this view model does not. And the request runs on the repository's scope, so leaving the screen does
 * not cancel what has already been paid for (D-312, D-313).
 */
@Suppress("LongParameterList")
class AskViewModel(
    savedStateHandle: SavedStateHandle,
    private val observePublication: ObservePublicationUseCase,
    private val observeConversation: ObserveAiConversationUseCase,
    private val observeAvailability: ObserveAiAvailabilityUseCase,
    private val askAboutDocument: AskAboutDocumentUseCase,
    private val retryLastQuestion: RetryLastQuestionUseCase,
    private val observeSavedKeys: ObserveSavedKeysUseCase,
    private val setPublicationSaved: SetPublicationSavedUseCase,
    private val observeAiNoticeAccepted: ObserveAiNoticeAcceptedUseCase,
    private val acceptAiNotice: AcceptAiNoticeUseCase,
) : ViewModel() {

    private val externalKey: String = requireNotNull(savedStateHandle[ARG_EXTERNAL_KEY]) {
        "the conversation screen needs a publication key"
    }

    private val draft = MutableStateFlow("")
    private val noticePending = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)

    private var saveJob: Job? = null

    /** What the person controls, grouped so the `combine` below stays under the typed overloads. */
    private data class Composer(
        val draft: String,
        val noticePending: Boolean,
        val saveFailed: Boolean,
    )

    /** The publication and whether it is theirs. Two facts about the same thing. */
    private data class PublicationState(
        val publication: Publication?,
        val isSaved: Boolean,
    )

    /** What the service will and will not let us do. */
    private data class ServiceState(
        val noticeAccepted: Boolean,
        val isConfigured: Boolean,
    )

    private fun publicationState(): Flow<PublicationState> = combine(
        observePublication(externalKey),
        observeSavedKeys().map { externalKey in it },
        ::PublicationState,
    )

    private fun serviceState(): Flow<ServiceState> = combine(
        observeAiNoticeAccepted(),
        observeAvailability(),
        ::ServiceState,
    )

    private fun composer(): Flow<Composer> =
        combine(draft, noticePending, saveFailed, ::Composer)

    /**
     * Four flows, not seven, and that is the whole reason the three types above exist: **`combine`
     * with more than five falls into the `vararg` overload**, which requires the same type for all of
     * them and hands back `Array<Any?>`. Grouping keeps this typed, and keeps the compiler able to
     * complain (D-327b).
     */
    val uiState: StateFlow<AskUiState> = combine(
        publicationState(),
        observeConversation(externalKey),
        serviceState(),
        composer(),
    ) { publication, conversation, service, composer ->
        AskUiState(
            publication = publication.publication,
            isSaved = publication.isSaved,
            messages = conversation.messages,
            status = conversation.status,
            draft = composer.draft,
            noticePending = composer.noticePending,
            noticeAccepted = service.noticeAccepted,
            isServiceConfigured = service.isConfigured,
            saveFailed = composer.saveFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AskUiState(),
    )

    fun onDraftChange(value: String) {
        draft.value = value
    }

    fun onSend() = trySend(uiState.value.draft)

    /**
     * Tapping a suggestion sends **the value**, not what the state says a moment later.
     *
     * Setting the draft and then calling [onSend] looked equivalent and was a race: `uiState` is a
     * `StateFlow` built by `combine`, so it has not recomposed by the time the next line runs and
     * `onSend` would read the previous, empty draft. The draft is still set, because the notice sheet
     * needs something to come back to and because seeing what is about to be sent is right — but what
     * travels is the argument.
     */
    fun onSuggestionTapped(question: String) {
        draft.value = question
        trySend(question)
    }

    /**
     * The first time, this opens the notice instead of asking: the official document leaves the
     * device, and finding that out afterwards is finding out too late (FR-042). The acceptance is the
     * same one the summary uses, and it is asked for once for both.
     */
    private fun trySend(question: String) {
        val state = uiState.value
        val publication = state.publication ?: return
        if (!state.isServiceConfigured || state.isBusy) return
        if (question.isBlank() || question.length > AiChatConstants.MAX_QUESTION_LENGTH) return
        if (!state.noticeAccepted) {
            noticePending.value = true
            return
        }
        send(publication, question)
    }

    fun onRetry() {
        val publication = uiState.value.publication ?: return
        retryLastQuestion(publication)
    }

    fun onNoticeAccepted() {
        noticePending.value = false
        val state = uiState.value
        val publication = state.publication ?: return
        val pending = state.draft
        viewModelScope.launch {
            acceptAiNotice()
            if (pending.isNotBlank()) send(publication, pending)
        }
    }

    /** Cancelling sends nothing at all, and keeps what was written. */
    fun onNoticeDismissed() {
        noticePending.value = false
    }

    fun onToggleSaved() {
        val publication = uiState.value.publication ?: return
        val target = !uiState.value.isSaved
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            saveFailed.value = false
            if (setPublicationSaved(publication.externalKey, target) is AppResult.Failure) {
                saveFailed.value = true
            }
        }
    }

    fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private fun send(publication: Publication, question: String) {
        draft.value = ""
        askAboutDocument(publication, question.trim().take(AiChatConstants.MAX_QUESTION_LENGTH))
    }

    /** The last question, so a failed turn can be retried without retyping it. */
    val lastQuestion: AiChatMessage.Question?
        get() = uiState.value.messages.filterIsInstance<AiChatMessage.Question>().lastOrNull()

    val canRetry: Boolean
        get() = (uiState.value.status as? AiChatStatus.Failed)?.retryableQuestionId != null

    companion object {
        const val ARG_EXTERNAL_KEY = "externalKey"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
