package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.remote.AiDocumentPreparer
import com.jrblanco.boccantabria.data.source.remote.ChatAnswerValidator
import com.jrblanco.boccantabria.data.source.remote.ChatPromptFactory
import com.jrblanco.boccantabria.data.source.remote.ChatTurn
import com.jrblanco.boccantabria.data.source.remote.GeminiApiKeyProvider
import com.jrblanco.boccantabria.data.source.remote.GeminiChatDataSource
import com.jrblanco.boccantabria.data.source.remote.GeminiChatResult
import com.jrblanco.boccantabria.data.source.remote.GeminiRefusal
import com.jrblanco.boccantabria.data.source.remote.PreparationResult
import com.jrblanco.boccantabria.domain.model.AiAnswerScope
import com.jrblanco.boccantabria.domain.model.AiChatConstants
import com.jrblanco.boccantabria.domain.model.AiChatError
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiChatStatus
import com.jrblanco.boccantabria.domain.model.AiConversation
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AiChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The conversation about a publication's document, for as long as the visit lasts.
 *
 * ### At most one, and why that is a structural claim
 *
 * One slot, not a map. «Opening another publication takes the previous conversation away» is checkable
 * about a single slot and merely an intention over a map of unknown size — the same argument that
 * shapes `AiDocumentSessionStore`. It makes FR-011 structural (011 research.md D-312).
 *
 * ### The work does not live on the screen's scope
 *
 * [ask] launches on [work], a scope of this repository's own, and that is the decision this class is
 * built around. Cancelling a request **does not refund the allowance** — it is counted when it goes
 * out — so abandoning one costs exactly the same as finishing it and loses the answer as well. Letting
 * it run means whoever leaves and comes back finds it done, and it settles FR-037 without writing
 * anything: there is no cancellation to report (D-313).
 *
 * The only thing that genuinely cancels is [discard], which is the publication being left.
 *
 * ### Nothing is persisted
 *
 * Not to the database, not to preferences, not to saved state. Persisting would mean a new table, a
 * migration to version 5 and — sooner or later — the project's first delete statement.
 */
@Suppress("LongParameterList")
class AiChatRepositoryImpl(
    private val preparer: AiDocumentPreparer,
    private val prompts: ChatPromptFactory,
    private val chat: GeminiChatDataSource,
    private val validator: ChatAnswerValidator,
    private val apiKeys: GeminiApiKeyProvider,
    private val time: TimeProvider,
    dispatchers: DispatcherProvider,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
    /**
     * What is shown when the answer is out of scope. **Already resolved**, not a resource id: `data`
     * does not read `strings.xml`. It is `DataModule` that resolves it (contracts §3.3).
     */
    private val outOfScopeText: String,
) : AiChatRepository {

    private val work = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val conversation = MutableStateFlow<AiConversation?>(null)

    /** What is in flight, so a second question cannot start and [discard] can stop it. */
    private var inFlight: Job? = null

    /** The prepared document of the open conversation, so a second question does not re-prepare. */
    private var prepared: PreparationResult.Ready? = null

    override fun observeConversation(externalKey: String): Flow<AiConversation> =
        conversation.map { open ->
            if (open?.externalKey == externalKey) open else AiConversation(externalKey)
        }

    /**
     * Whether the service can be asked at all, without asking it.
     *
     * A flow of one value today, because the credential comes from `BuildConfig` and does not change
     * while the application runs. That does not make it wrong: the screen combines it with everything
     * else, and a value you have to decide when to read is a decision at every call site (D-320b).
     */
    override fun observeAvailability(): Flow<Boolean> = flow { emit(apiKeys.apiKey() != null) }

    override fun ask(publication: Publication, question: String) {
        val text = question.trim().take(AiChatConstants.MAX_QUESTION_LENGTH)
        if (text.isBlank()) return

        val key = publication.externalKey
        // **The in-flight guard belongs to the conversation, not to the process** (FR-005, FR-050). A
        // recomposition or a rotation cannot get a second question past this, which is what it is for;
        // but a question about **another** publication means the reader has moved on, and it wins. The
        // guard used to cover every key, which quietly made the cancellation in [currentFor]
        // unreachable — it could only ever cancel a job that had already finished.
        if (conversation.value?.externalKey == key && inFlight?.isActive == true) return

        val open = currentFor(key)
        val asked = AiChatMessage.Question(
            id = "q-${time.nowMillis()}-${open.messages.size}",
            atEpochMillis = time.nowMillis(),
            text = text,
        )
        conversation.value = open.copy(
            messages = open.messages + asked,
            status = AiChatStatus.Preparing(AiChatStatus.Preparing.Phase.FETCHING_DOCUMENT),
        )
        run(publication, asked)
    }

    override fun retry(publication: Publication) {
        val open = conversation.value ?: return
        if (open.externalKey != publication.externalKey) return
        if (inFlight?.isActive == true) return

        val failed = open.status as? AiChatStatus.Failed ?: return
        val questionId = failed.retryableQuestionId ?: return
        val question = open.messages
            .filterIsInstance<AiChatMessage.Question>()
            .lastOrNull { it.id == questionId }
            ?: return

        // The bubble is already there: whoever asked wrote it once, and making them write it again
        // because the service failed would be charging them for somebody else's fault (D-320).
        conversation.value = open.copy(
            status = AiChatStatus.Preparing(AiChatStatus.Preparing.Phase.FETCHING_DOCUMENT),
        )
        run(publication, question)
    }

    override fun discard(externalKey: String) {
        if (conversation.value?.externalKey != externalKey) return
        inFlight?.cancel()
        inFlight = null
        prepared = null
        conversation.value = null
        crashReporter.log("chat: discarded $externalKey")
    }

    /** The open conversation for this key, starting a fresh one if the previous was another's. */
    private fun currentFor(externalKey: String): AiConversation {
        val open = conversation.value
        if (open != null && open.externalKey == externalKey) return open
        // Another publication. At most one lives at a time, so the previous one goes — and its
        // request with it, which is the one cancellation this class performs besides [discard].
        if (open != null) inFlight?.cancel()
        prepared = null
        return AiConversation(externalKey)
    }

    private fun run(publication: Publication, question: AiChatMessage.Question) {
        inFlight = work.launch {
            try {
                answer(publication, question)
            } catch (cancellation: CancellationException) {
                // Only `discard` cancels, and then there is no screen left to tell.
                throw cancellation
            } catch (error: Throwable) {
                crashReporter.log("chat: threw ${error.javaClass.simpleName}: ${error.message}")
                crashReporter.recordNonFatal(error)
                fail(publication.externalKey, AiChatError.Unknown, question.id)
            }
        }
    }

    private suspend fun answer(publication: Publication, question: AiChatMessage.Question) {
        val key = publication.externalKey

        val document = prepared ?: when (
            val outcome = preparer.prepare(publication) { phase -> publish(key, phase.toStatus()) }
        ) {
            is PreparationResult.Ready -> outcome.also { prepared = it }
            is PreparationResult.Unreachable ->
                return fail(key, outcome.error.toChatError(), question.id)
            PreparationResult.Encrypted -> return fail(key, AiChatError.EncryptedPdf, null)
            is PreparationResult.Refused ->
                return fail(key, outcome.reason.toPreparationError(), question.id)
            is PreparationResult.Broken -> {
                crashReporter.recordNonFatal(outcome.cause)
                return fail(key, AiChatError.Unknown, question.id)
            }
        }

        publish(key, AiChatStatus.Thinking)

        val history = historyFor(key, question)
        val system = prompts.systemMessage(publication, document.totalPages)

        val outcome = chat.ask(system = system, history = history, document = document.document)
        val success = when (outcome) {
            is GeminiChatResult.Success -> outcome
            is GeminiChatResult.Rejected ->
                return fail(key, outcome.reason.toChatError(), question.id)
        }

        val validated = validator.validate(success.payload, document.totalPages)
            ?: return fail(key, AiChatError.InvalidResponse, question.id)
                .also { crashReporter.log("chat: blank answer from the service") }

        if (validated.droppedCitations > 0) {
            crashReporter.log(
                "chat: ${validated.droppedCitations} citation(s) dropped, " +
                    "document has ${document.totalPages} pages",
            )
        }

        // **FR-021, and it is one line.** Out of scope, what is shown is ours and not a single
        // character of the service's. It lives here and not on the screen so that no future screen
        // can skip it by accident (contracts §3.3).
        val text = when (validated.scope) {
            AiAnswerScope.OUT_OF_SCOPE -> outOfScopeText
            else -> validated.text
        }

        val open = conversation.value ?: return
        if (open.externalKey != key) return
        conversation.value = open.copy(
            messages = open.messages + AiChatMessage.Answer(
                id = "a-${time.nowMillis()}-${open.messages.size}",
                atEpochMillis = time.nowMillis(),
                text = text,
                scope = validated.scope,
                sources = validated.sources,
            ),
            status = AiChatStatus.Idle,
        )

        // The scope and nothing else: it is an enum of three values and cannot carry anything of the
        // reader's. The publication key would, crossed with little else (D-329).
        analytics.track(
            AnalyticsEvent(EVENT_QUESTION, mapOf(PARAM_SCOPE to validated.scope.name)),
        )
    }

    /**
     * The turns that travel, trimmed to the window.
     *
     * Not to save anything — the document dominates the input — but because a list with no bound is a
     * request with no bound (D-303). The question being answered is always the last turn, even when
     * it is a retry and the messages after it are older failures' leftovers.
     */
    private fun historyFor(externalKey: String, question: AiChatMessage.Question): List<ChatTurn> {
        val open = conversation.value?.takeIf { it.externalKey == externalKey }
        val upToQuestion = open?.messages
            ?.takeWhile { it.id != question.id }
            .orEmpty()

        return (upToQuestion + question)
            .takeLast(AiChatConstants.MAX_HISTORY_MESSAGES)
            .map { message ->
                when (message) {
                    is AiChatMessage.Question ->
                        ChatTurn(ChatTurn.Role.USER, prompts.question(message.text))
                    // What was **shown** goes back, including our own text when the answer was out of
                    // scope. Replaying the model's discarded words would hand the context back
                    // exactly what we decided not to show (contracts §1.2).
                    is AiChatMessage.Answer -> ChatTurn(ChatTurn.Role.MODEL, message.text)
                }
            }
    }

    private fun publish(externalKey: String, status: AiChatStatus) {
        val open = conversation.value ?: return
        if (open.externalKey != externalKey) return
        conversation.value = open.copy(status = status)
    }

    private fun fail(externalKey: String, error: AiChatError, questionId: String?) {
        publish(
            externalKey,
            AiChatStatus.Failed(
                error = error,
                retryableQuestionId = questionId.takeIf { error.isRetryable },
            ),
        )
    }

    private fun AiDocumentPreparer.Phase.toStatus(): AiChatStatus = AiChatStatus.Preparing(
        when (this) {
            AiDocumentPreparer.Phase.FETCHING_DOCUMENT ->
                AiChatStatus.Preparing.Phase.FETCHING_DOCUMENT
            AiDocumentPreparer.Phase.UPLOADING_DOCUMENT ->
                AiChatStatus.Preparing.Phase.UPLOADING_DOCUMENT
        },
    )

    private fun DomainError.toChatError(): AiChatError = when (this) {
        DomainError.Network -> AiChatError.Offline
        DomainError.Unknown -> AiChatError.Unknown
    }

    /**
     * A refusal from the **upload**, which is a different sentence to the reader: the service took the
     * bytes and still could not read the document. From the **answer** the same refusal means the body
     * would not parse, which is [AiChatError.InvalidResponse].
     */
    private fun GeminiRefusal.toPreparationError(): AiChatError = when (this) {
        GeminiRefusal.Malformed, GeminiRefusal.BlankSummary -> AiChatError.UnreadableDocument
        else -> toChatError()
    }

    private fun GeminiRefusal.toChatError(): AiChatError = when (this) {
        GeminiRefusal.NotConfigured -> AiChatError.NotConfigured
        GeminiRefusal.Network -> AiChatError.Offline
        GeminiRefusal.Malformed, GeminiRefusal.BlankSummary -> AiChatError.InvalidResponse
        is GeminiRefusal.QuotaMinute -> AiChatError.QuotaMinute(secondsRemaining)
        GeminiRefusal.QuotaDay -> AiChatError.QuotaDay
        is GeminiRefusal.HttpError -> AiChatError.Unknown
    }

    private companion object {
        const val EVENT_QUESTION = "ai_question_asked"
        const val PARAM_SCOPE = "scope"
    }
}

