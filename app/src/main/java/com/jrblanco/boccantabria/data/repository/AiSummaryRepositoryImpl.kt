package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.AiPreferences
import com.jrblanco.boccantabria.data.source.local.AiSummaryDao
import com.jrblanco.boccantabria.data.source.local.AiSummaryEntity
import com.jrblanco.boccantabria.data.source.remote.AiDocumentPreparer
import com.jrblanco.boccantabria.data.source.remote.AiDocumentSessionStore
import com.jrblanco.boccantabria.data.source.remote.PreparationResult
import com.jrblanco.boccantabria.data.source.remote.GeminiRefusal
import com.jrblanco.boccantabria.data.source.remote.GeminiSummaryDataSource
import com.jrblanco.boccantabria.data.source.remote.GeminiSummaryResult
import com.jrblanco.boccantabria.data.source.remote.SummaryPayload
import com.jrblanco.boccantabria.data.source.remote.SummaryPromptFactory
import com.jrblanco.boccantabria.data.source.remote.SummaryValidator
import com.jrblanco.boccantabria.data.source.remote.toDomain
import com.jrblanco.boccantabria.domain.model.AiSummary
import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import com.jrblanco.boccantabria.domain.model.AiSummaryError
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository
import com.jrblanco.boccantabria.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The whole pipeline, in one place.
 *
 * ```
 * ensureLocalCopy -> pageCount -> session.open (uploads) -> ask -> validate -> store
 * ```
 *
 * Shaped like [DocumentRepositoryImpl] because it is the same problem: progress is published into a
 * map the screen observes, while [generate] does the work. That keeps the view model free of
 * orchestration, which principle III requires (research.md D-025).
 *
 * **The order is not incidental.** `pageCount` runs **before** the upload, so a password-protected
 * document never leaves the device (FR-004, SC-007). And the cached row is checked before all of it:
 * a stored summary costs no page count, no upload and no request.
 *
 * Three guarantees that cannot be relaxed:
 * - **Observing never generates.** Only [generate] reaches the service (FR-017, SC-004).
 * - **A password-protected document never leaves the device** (FR-004, SC-007). This replaces the
 *   guarantee of features 007 and 009, "a document without usable text never reaches the service",
 *   which is superseded rather than broken: it existed because what travelled *was* the text, and a
 *   scan is a valid input now (010 research.md D-204).
 * - **Two concurrent generations of the same publication share one request** (FR-022).
 */
@Suppress("LongParameterList")
class AiSummaryRepositoryImpl(
    private val documents: DocumentRepository,
    private val preparer: AiDocumentPreparer,
    private val sessions: AiDocumentSessionStore,
    private val prompts: SummaryPromptFactory,
    private val summaries: GeminiSummaryDataSource,
    private val validator: SummaryValidator,
    private val dao: AiSummaryDao,
    private val preferences: AiPreferences,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) : AiSummaryRepository {

    private val inProgress = MutableStateFlow<Map<String, AiSummaryStatus>>(emptyMap())
    private val inFlight = mutableMapOf<String, CompletableDeferred<AppResult<AiSummary>>>()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * What is stored, what is running, and whether what is stored still matches the document.
     *
     * The document is watched too, because the stale check needs its checksum — and only when it is
     * already downloaded. A stored summary is shown from the moment the tab opens, without waiting
     * for the network, which is what SC-002 asks for.
     */
    override fun observeSummary(externalKey: String): Flow<AiSummaryStatus> = combine(
        inProgress.map { it[externalKey] },
        dao.observe(externalKey),
        documents.observeDocument(externalKey),
    ) { running, stored, document ->
        running ?: stored?.let { entity ->
            val summary = entity.decode()
            if (summary == null) {
                AiSummaryStatus.Idle
            } else {
                AiSummaryStatus.Ready(
                    summary = summary,
                    generatedAtEpochMillis = entity.createdAtEpochMillis,
                    isStale = entity.isStale(document),
                )
            }
        } ?: AiSummaryStatus.Idle
    }

    override suspend fun generate(
        publication: Publication,
        force: Boolean,
    ): AppResult<AiSummary> = withContext(dispatchers.io) {
        val key = publication.externalKey

        if (!force) {
            dao.byExternalKey(key)?.decode()?.let { stored ->
                analytics.track(AnalyticsEvent(EVENT_SUMMARY, mapOf(PARAM_CACHED to "true")))
                return@withContext AppResult.Success(stored)
            }
        }

        // Under the lock, decide whether this call owns the work or waits for the one in flight.
        val owned = lock.withLock {
            inFlight[key]?.let { return@withLock it to false }
            CompletableDeferred<AppResult<AiSummary>>().also { inFlight[key] = it } to true
        }
        val (pending, isOwner) = owned
        if (!isOwner) return@withContext pending.await()

        try {
            val result = run(publication)
            pending.complete(result)
            result
        } catch (cancellation: CancellationException) {
            // Leaving the screen is not a failure (FR-006).
            publish(key, null)
            pending.cancel()
            throw cancellation
        } catch (error: Throwable) {
            crashReporter.log("summary threw: ${error.javaClass.simpleName}: ${error.message}")
            crashReporter.recordNonFatal(error)
            publish(key, AiSummaryStatus.Failed(AiSummaryError.Unknown))
            AppResult.Failure(DomainError.Unknown).also(pending::complete)
        } finally {
            lock.withLock { inFlight.remove(key) }
        }
    }

    override fun observeNoticeAccepted(): Flow<Boolean> = preferences.observeNoticeAccepted()

    override suspend fun acceptNotice() = preferences.acceptNotice()

    /**
     * Not suspending, because the only caller is the detail view model's `onCleared()` and by then
     * its scope is cancelled. The store launches the deletion on a scope of its own.
     */
    override fun releaseDocumentSession(externalKey: String) = sessions.release(externalKey)

    private suspend fun run(publication: Publication): AppResult<AiSummary> {
        val key = publication.externalKey

        // Fetching, counting the pages and uploading, in that order and behind one seam. Counting
        // before uploading is what keeps a password-protected document on the device (FR-004), and it
        // is also where the validator's one un-negotiable number comes from — how many pages the
        // document really has (D-205). It lives in `AiDocumentPreparer` since feature 011, because the
        // conversation needs exactly the same four steps and a duplicated invariant is an invariant
        // that holds until somebody fixes one of the copies (011 research.md D-315).
        val prepared = when (
            val outcome = preparer.prepare(publication) { phase -> publish(key, phase.toStatus()) }
        ) {
            is PreparationResult.Ready -> outcome
            is PreparationResult.Unreachable -> return fail(key, outcome.error.toSummaryError())
            PreparationResult.Encrypted -> return fail(key, AiSummaryError.EncryptedPdf)
            is PreparationResult.Refused -> return fail(key, outcome.reason.toUploadError())
            is PreparationResult.Broken -> {
                crashReporter.recordNonFatal(outcome.cause)
                return fail(key, AiSummaryError.Unknown)
            }
        }
        val uploaded = prepared.document
        val totalPages = prepared.totalPages

        val system = prompts.systemMessage()
        val user = prompts.userMessage(publication, totalPages)

        publish(key, AiSummaryStatus.Generating(totalPages))
        val answer = summaries.summarise(system = system, user = user, document = uploaded)

        val success = when (answer) {
            is GeminiSummaryResult.Success -> answer
            is GeminiSummaryResult.Rejected -> return fail(key, answer.reason.toSummaryError())
        }

        val corrected = validator.validate(success.payload, totalPages)
            // The one case the validator refuses outright: a summary with nothing to say. Worth
            // naming, because on screen it looks the same as a malformed body and is not.
            ?: return fail(key, AiSummaryError.InvalidResponse)
                .also { crashReporter.log("summary rejected: blank prose from the service") }

        val summary = corrected.toDomain()
        store(publication, prepared.pdfSha256, corrected, success)

        analytics.track(
            AnalyticsEvent(
                name = EVENT_SUMMARY,
                parameters = mapOf(
                    PARAM_CACHED to "false",
                    PARAM_TOTAL_PAGES to totalPages.toString(),
                    PARAM_TOTAL_TOKENS to success.usage.totalTokens.toString(),
                ),
            ),
        )

        // Cleared rather than set to `Ready`: from here on the **stored row** is the source of
        // truth, and it is the only one that can recompute staleness when the document changes
        // later in the same session. Leaving a finished status in the map would shadow it forever.
        publish(key, null)
        return AppResult.Success(summary)
    }

    private suspend fun store(
        publication: Publication,
        pdfSha256: String,
        payload: SummaryPayload,
        answer: GeminiSummaryResult.Success,
    ) {
        dao.upsert(
            AiSummaryEntity(
                externalKey = publication.externalKey,
                pdfSha256 = pdfSha256,
                modelId = AiSummaryConstants.MODEL_ID,
                promptVersion = AiSummaryConstants.PROMPT_VERSION,
                schemaVersion = AiSummaryConstants.SCHEMA_VERSION,
                // The **corrected** payload, so what is on disk always tells the truth about
                // coverage even if the service's original answer did not.
                summaryJson = json.encodeToString(payload),
                createdAtEpochMillis = time.nowMillis(),
                promptTokens = answer.usage.totalInputTokens,
                completionTokens = answer.usage.totalOutputTokens,
                totalTokens = answer.usage.totalTokens,
                systemFingerprint = answer.systemFingerprint,
            ),
        )
    }

    /**
     * Every failure leaves through here, and every one of them says so.
     *
     * `Unknown` in particular covers four unrelated situations — a document that would not download,
     * an extraction that broke, an HTTP status with no better home, and anything thrown along the way.
     * On screen they are deliberately the same sentence; in the log they must not be, or there is no
     * way to tell which one happened.
     */
    private fun fail(externalKey: String, error: AiSummaryError): AppResult<AiSummary> {
        crashReporter.log("summary failed: ${error.javaClass.simpleName}")
        publish(externalKey, AiSummaryStatus.Failed(error))
        return AppResult.Failure(DomainError.Unknown)
    }

    private fun publish(externalKey: String, status: AiSummaryStatus?) {
        inProgress.value = inProgress.value.toMutableMap().apply {
            if (status == null) remove(externalKey) else put(externalKey, status)
        }
    }

    /**
     * Stale rather than absent. The row is kept and shown, marked, with the option to make it again;
     * it is never discarded here (FR-035).
     *
     * The checksum is only compared when the document is already downloaded, because until then it
     * is not known — and a stored summary must show without waiting for the network.
     */
    private fun AiSummaryEntity.isStale(document: DocumentStatus): Boolean {
        val configurationChanged = modelId != AiSummaryConstants.MODEL_ID ||
            promptVersion != AiSummaryConstants.PROMPT_VERSION ||
            schemaVersion != AiSummaryConstants.SCHEMA_VERSION
        val documentChanged = document is DocumentStatus.Available &&
            document.document.checksum != pdfSha256
        return configurationChanged || documentChanged
    }

    /** A row that no longer parses is treated as absent rather than crashing the screen. */
    private fun AiSummaryEntity.decode(): AiSummary? = runCatching {
        json.decodeFromString<SummaryPayload>(summaryJson).toDomain()
    }.getOrNull()

    /**
     * The preparer's phases in this screen's words. Two enums with the same two values, and on
     * purpose: the preparer's is a signal of the data layer, and each screen keeps its own status
     * (011 data-model §1.5).
     */
    private fun AiDocumentPreparer.Phase.toStatus(): AiSummaryStatus = AiSummaryStatus.Preparing(
        when (this) {
            AiDocumentPreparer.Phase.FETCHING_DOCUMENT ->
                AiSummaryStatus.Preparing.Phase.FETCHING_DOCUMENT
            AiDocumentPreparer.Phase.UPLOADING_DOCUMENT ->
                AiSummaryStatus.Preparing.Phase.UPLOADING_DOCUMENT
        },
    )

    private fun DomainError.toSummaryError(): AiSummaryError = when (this) {
        DomainError.Network -> AiSummaryError.Offline
        DomainError.Unknown -> AiSummaryError.Unknown
    }

    /**
     * A refusal from the **upload**, which is a different sentence to the reader.
     *
     * `Malformed` here means the service accepted the bytes and still could not process the
     * document, which is exactly what [AiSummaryError.UnreadableDocument] says. From the
     * **generation** the same refusal means the answer would not parse, which is `InvalidResponse`.
     * Same word, two situations, and telling them apart is the whole reason this second map exists.
     */
    private fun GeminiRefusal.toUploadError(): AiSummaryError = when (this) {
        GeminiRefusal.Malformed, GeminiRefusal.BlankSummary -> AiSummaryError.UnreadableDocument
        else -> toSummaryError()
    }

    private fun GeminiRefusal.toSummaryError(): AiSummaryError = when (this) {
        GeminiRefusal.NotConfigured -> AiSummaryError.NotConfigured
        GeminiRefusal.Network -> AiSummaryError.Offline
        GeminiRefusal.Malformed, GeminiRefusal.BlankSummary -> AiSummaryError.InvalidResponse
        is GeminiRefusal.QuotaMinute -> AiSummaryError.QuotaMinute(secondsRemaining)
        GeminiRefusal.QuotaDay -> AiSummaryError.QuotaDay
        is GeminiRefusal.HttpError -> AiSummaryError.Unknown
    }

    private companion object {
        const val EVENT_SUMMARY = "ai_summary_generated"
        const val PARAM_CACHED = "cached"
        const val PARAM_TOTAL_PAGES = "total_pages"
        const val PARAM_TOTAL_TOKENS = "total_tokens"
        // `pages_analyzed` and `partial` are gone rather than kept lying: the whole document is sent,
        // so one would always equal the total and the other would always be false.
    }
}
