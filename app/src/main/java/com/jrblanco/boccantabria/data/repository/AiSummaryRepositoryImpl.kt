package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.AiPreferences
import com.jrblanco.boccantabria.data.source.local.AiSummaryDao
import com.jrblanco.boccantabria.data.source.local.AiSummaryEntity
import com.jrblanco.boccantabria.data.source.local.PdfExtractionResult
import com.jrblanco.boccantabria.data.source.local.PdfTextExtractor
import com.jrblanco.boccantabria.data.source.local.PdfTextNormalizer
import com.jrblanco.boccantabria.data.source.remote.GroqRefusal
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryDataSource
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryPayload
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryResult
import com.jrblanco.boccantabria.data.source.remote.SummaryBudget
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
import com.jrblanco.boccantabria.domain.model.PdfCorpus
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
 * ensureLocalCopy -> extract -> normalise -> budget -> ask -> validate -> store
 * ```
 *
 * Shaped like [DocumentRepositoryImpl] because it is the same problem: progress is published into a
 * map the screen observes, while [generate] does the work. That keeps the view model free of
 * orchestration, which principle III requires (research.md D-025).
 *
 * Three guarantees that cannot be relaxed:
 * - **Observing never generates.** Only [generate] reaches the service (FR-002, SC-004).
 * - **A document without usable text never reaches the service** (FR-012, SC-005).
 * - **Two concurrent generations of the same publication share one request** (FR-005).
 */
@Suppress("LongParameterList")
class AiSummaryRepositoryImpl(
    private val documents: DocumentRepository,
    private val extractor: PdfTextExtractor,
    private val normalizer: PdfTextNormalizer,
    private val prompts: SummaryPromptFactory,
    private val summaries: GroqSummaryDataSource,
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

    private suspend fun run(publication: Publication): AppResult<AiSummary> {
        val key = publication.externalKey

        publish(key, AiSummaryStatus.Preparing(AiSummaryStatus.Preparing.Phase.FETCHING_DOCUMENT))
        val document = when (val fetched = documents.ensureLocalCopy(publication)) {
            is AppResult.Success -> fetched.data
            is AppResult.Failure -> return fail(key, fetched.error.toSummaryError())
        }

        publish(key, AiSummaryStatus.Preparing(AiSummaryStatus.Preparing.Phase.EXTRACTING_TEXT))
        crashReporter.log("summary: document ready, extracting")
        val corpus = when (
            val extracted = extractor.extract(document.localPath, key, document.checksum)
        ) {
            is PdfExtractionResult.Success -> normalizer.normalise(extracted.corpus)
            // The three cases that must never cost a request (FR-012, SC-005).
            PdfExtractionResult.NoExtractableText -> return fail(key, AiSummaryError.NoExtractableText)
            PdfExtractionResult.EncryptedPdf -> return fail(key, AiSummaryError.EncryptedPdf)
            is PdfExtractionResult.Failure -> {
                crashReporter.recordNonFatal(extracted.cause)
                return fail(key, AiSummaryError.Unknown)
            }
        }

        val selected = SummaryBudget.select(corpus)
        val system = prompts.systemMessage()
        val user = prompts.userMessage(publication, selected, corpus.totalPages)

        crashReporter.log(
            "summary: sending pages ${selected.pages.size}/${corpus.totalPages}, " +
                "${selected.text.length} chars, ~${selected.estimatedTokens} tokens",
        )
        publish(key, AiSummaryStatus.Generating(selected.pages.size, corpus.totalPages))
        val answer = summaries.summarise(
            system = system,
            user = user,
            estimatedTokens = selected.estimatedTokens + PROMPT_OVERHEAD_TOKENS,
        )

        val success = when (answer) {
            is GroqSummaryResult.Success -> answer
            is GroqSummaryResult.Rejected -> return fail(key, answer.reason.toSummaryError())
        }

        val corrected = validator.validate(success.payload, corpus, selected.pages)
            // The one case the validator refuses outright: a summary with nothing to say. Worth
            // naming, because on screen it looks the same as a malformed body and is not.
            ?: return fail(key, AiSummaryError.InvalidResponse)
                .also { crashReporter.log("summary rejected: blank prose from the service") }

        val summary = corrected.toDomain()
        store(publication, document.checksum, corrected, success)

        analytics.track(
            AnalyticsEvent(
                name = EVENT_SUMMARY,
                parameters = mapOf(
                    PARAM_CACHED to "false",
                    PARAM_PAGES to selected.pages.size.toString(),
                    PARAM_TOTAL_PAGES to corpus.totalPages.toString(),
                    PARAM_PARTIAL to selected.isPartial.toString(),
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
        payload: GroqSummaryPayload,
        answer: GroqSummaryResult.Success,
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
                promptTokens = answer.usage.promptTokens,
                completionTokens = answer.usage.completionTokens,
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
        json.decodeFromString<GroqSummaryPayload>(summaryJson).toDomain()
    }.getOrNull()

    private fun DomainError.toSummaryError(): AiSummaryError = when (this) {
        DomainError.Network -> AiSummaryError.Offline
        DomainError.Unknown -> AiSummaryError.Unknown
    }

    private fun GroqRefusal.toSummaryError(): AiSummaryError = when (this) {
        GroqRefusal.NotConfigured -> AiSummaryError.NotConfigured
        GroqRefusal.Network -> AiSummaryError.Offline
        GroqRefusal.Malformed, GroqRefusal.BlankSummary -> AiSummaryError.InvalidResponse
        is GroqRefusal.QuotaMinute -> AiSummaryError.QuotaMinute(secondsRemaining)
        GroqRefusal.QuotaDay -> AiSummaryError.QuotaDay
        is GroqRefusal.HttpError -> AiSummaryError.Unknown
    }

    private companion object {
        const val EVENT_SUMMARY = "ai_summary_generated"
        const val PARAM_CACHED = "cached"
        const val PARAM_PAGES = "pages_analyzed"
        const val PARAM_TOTAL_PAGES = "total_pages"
        const val PARAM_PARTIAL = "partial"
        const val PARAM_TOTAL_TOKENS = "total_tokens"

        /** The fixed prompt and the metadata, on top of the document text. */
        const val PROMPT_OVERHEAD_TOKENS = 700
    }
}
