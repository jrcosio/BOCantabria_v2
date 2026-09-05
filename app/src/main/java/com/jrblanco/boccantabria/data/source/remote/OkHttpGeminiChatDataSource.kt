package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The conversation, over the same wire the summary uses.
 *
 * Line for line this is [OkHttpGeminiSummaryDataSource] with three differences, and each one is a
 * decision rather than an accident:
 *
 * - **The request carries several turns**, not one. The history is ours and travels whole on every
 *   question, because `generateContent` holds no state (D-302).
 * - **The document reference goes in the first user turn and only there.** Repeating it adds no
 *   context — it is already in the conversation — and adds a second way for the request to be wrong.
 *   When the window drops the turn that carried it, the reference moves to the first turn left, so
 *   **the document can never be missing** (D-304).
 * - **The output ceiling is 2 000 and not 8 000.** An answer to a bulletin is short by design; if one
 *   ever touched the ceiling, the prompt is wrong and it should show.
 *
 * Everything else is kept deliberately identical, because it was all paid for once: the credential in
 * a header, the request counted when it goes **out**, [currentCoroutineContext].ensureActive as the
 * first line of the `IOException` catch, three attempts with backoff, the coordinator consulted before
 * each retry, and a log that records the *shape* of what happened and never its content.
 */
class OkHttpGeminiChatDataSource(
    client: OkHttpClient,
    private val apiKeys: GeminiApiKeyProvider,
    private val coordinator: GeminiRateLimitCoordinator,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
    private val baseUrl: String = OkHttpGeminiDocumentUploader.DEFAULT_BASE_URL,
) : GeminiChatDataSource {

    private val client = client.newBuilder().build()

    /**
     * `encodeDefaults` is not cosmetic: without it kotlinx omits any property equal to its default and
     * the thinking level — whose provider default is reasoning **on**, and billed — would never be
     * sent.
     */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun ask(
        system: String,
        history: List<ChatTurn>,
        document: UploadedDocument,
    ): GeminiChatResult = withContext(dispatchers.io) {
        require(history.isNotEmpty()) { "there is nothing to answer" }
        require(history.last().role == ChatTurn.Role.USER) {
            "the last turn must be the question being asked"
        }
        val key = apiKeys.apiKey() ?: return@withContext rejected(GeminiRefusal.NotConfigured)

        // One request at a time, across the whole application — shared with the summary, because the
        // allowance belongs to the plan and not to the feature (D-317).
        coordinator.serialised {
            when (val verdict = coordinator.verdict()) {
                QuotaVerdict.ExhaustedDay -> rejected(GeminiRefusal.QuotaDay)
                is QuotaVerdict.WaitMinute ->
                    rejected(GeminiRefusal.QuotaMinute(verdict.secondsRemaining))
                QuotaVerdict.Allowed -> attempt(key, system, history, document)
            }
        }
    }

    private suspend fun attempt(
        key: String,
        system: String,
        history: List<ChatTurn>,
        document: UploadedDocument,
    ): GeminiChatResult {
        var lastRefusal: GeminiRefusal = GeminiRefusal.Network

        repeat(MAX_ATTEMPTS) { attempt ->
            when (val outcome = execute(key, system, history, document)) {
                is GeminiChatResult.Success -> return outcome
                is GeminiChatResult.Rejected -> {
                    lastRefusal = outcome.reason
                    if (!outcome.reason.isWorthRetrying(attempt)) return outcome
                    // A retry that cannot run must not change the answer. Asking again with no room
                    // left comes back as a quota problem, and the reader is then told about a limit
                    // when what actually happened was something else.
                    if (coordinator.verdict() != QuotaVerdict.Allowed) return outcome
                    delay(coordinator.backoffMillis(attempt))
                }
            }
        }
        return rejected(lastRefusal)
    }

    private suspend fun execute(
        key: String,
        system: String,
        history: List<ChatTurn>,
        document: UploadedDocument,
    ): GeminiChatResult = try {
        val payload = GeminiGenerateRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = system))),
            contents = history.toContents(document),
            generationConfig = GeminiGenerationConfig(
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = THINKING_LEVEL),
                maxOutputTokens = MAX_OUTPUT_TOKENS,
                responseMimeType = OkHttpGeminiSummaryDataSource.MEDIA_TYPE_JSON,
                // Verbatim: its property order is payload, and `scope` being first is the defence
                // surviving a truncated generation (D-310).
                responseJsonSchema = ChatAnswerSchema.value,
            ),
        )

        val request = Request.Builder()
            .url("$baseUrl/$API_VERSION/models/${AiSummaryConstants.MODEL_ID}:generateContent")
            // In the header and never in the body or the query string, so that a body or a URL
            // captured for diagnosis cannot carry it.
            .header(OkHttpGeminiDocumentUploader.HEADER_API_KEY, key)
            .post(json.encodeToString(payload).toRequestBody(CONTENT_TYPE_JSON.toMediaType()))
            .build()

        report("asking with ${history.size} message(s)")
        // Counted when it goes out: what spends the allowance is asking.
        coordinator.recordRequest()

        client.newCall(request).execute().use { response ->
            val bodyText = response.body.string()

            when {
                response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN ->
                    rejected(GeminiRefusal.NotConfigured).also { report("HTTP ${response.code}") }

                response.code == HTTP_TOO_MANY_REQUESTS -> {
                    val seconds = retryDelaySeconds(response.headers, bodyText)
                    report("HTTP 429, retry in ${seconds}s")
                    when (coordinator.recordExhaustion(seconds)) {
                        QuotaVerdict.ExhaustedDay -> rejected(GeminiRefusal.QuotaDay)
                        is QuotaVerdict.WaitMinute, QuotaVerdict.Allowed ->
                            rejected(GeminiRefusal.QuotaMinute(seconds))
                    }
                }

                !response.isSuccessful -> {
                    // The status code never reaches the screen (FR-031), which is exactly why it has
                    // to reach the log: a 400 and a 503 are the same sentence to the reader and
                    // completely different problems to whoever has to fix it.
                    report("HTTP ${response.code}: ${reasonFrom(bodyText)}")
                    rejected(GeminiRefusal.HttpError(response.code))
                }

                else -> parse(bodyText)
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: IOException) {
        // **A blocking OkHttp call does not throw `CancellationException` when the coroutine is
        // cancelled.** `Call.execute()` blocks; cancelling tears the socket down and what comes out is
        // an `IOException`. Without this line, a cancelled request is reported as `Network` and the
        // reader is told there is no connection for a failure that never happened. Learned on a real
        // phone in feature 009; there is a regression test.
        currentCoroutineContext().ensureActive()
        report("network: ${error.javaClass.simpleName}: ${error.message}")
        rejected(GeminiRefusal.Network)
    }

    /**
     * The turns, with the document attached to the first user one.
     *
     * The `first` guard is the invariant: whichever turn ends up first after the window has trimmed
     * the history, that is where the reference goes. The document cannot be missing.
     */
    private fun List<ChatTurn>.toContents(document: UploadedDocument): List<GeminiContent> {
        var attached = false
        return map { turn ->
            val isFirstUserTurn = turn.role == ChatTurn.Role.USER && !attached
            if (isFirstUserTurn) attached = true

            GeminiContent(
                role = if (turn.role == ChatTurn.Role.USER) ROLE_USER else ROLE_MODEL,
                parts = buildList {
                    if (isFirstUserTurn) {
                        add(
                            GeminiPart(
                                fileData = GeminiFileData(
                                    fileUri = document.fileUri,
                                    mimeType = document.mimeType,
                                ),
                            ),
                        )
                    }
                    add(GeminiPart(text = turn.text))
                },
            )
        }
    }

    private fun parse(body: String): GeminiChatResult = try {
        val answer = json.decodeFromString<GeminiGenerateResponse>(body)
        val candidate = answer.candidates.firstOrNull()
        val finish = candidate?.finishReason
        if (finish != null && finish != FINISH_STOP) report("finishReason=$finish")

        // Reasoning parts are skipped **by their flag and never by position**: the reasoning step
        // arrives before the answer every single time (009 research.md D-117).
        val content = candidate?.content?.parts
            ?.filter { it.thought != true }
            ?.firstNotNullOfOrNull { it.text?.takeIf(String::isNotBlank) }

        if (content.isNullOrBlank()) {
            report(
                "no text, finishReason=$finish, " +
                    "${answer.usageMetadata?.candidatesTokenCount ?: 0} output tokens",
            )
            rejected(GeminiRefusal.Malformed)
        } else {
            val payload = json.decodeFromString<ChatAnswerPayload>(content)
            // The scope and the counts, never the words: the scope is an enum of three values and
            // cannot leak anything, and the rest is the document (FR-039).
            report("answer scope=${payload.scope}, ${payload.sources.size} source(s)")
            GeminiChatResult.Success(payload = payload, usage = answer.toUsage())
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        // The *kind* of failure and its size, never what the body held.
        report("unparseable answer of ${body.length} chars: ${error.javaClass.simpleName}")
        rejected(GeminiRefusal.Malformed)
    }

    private fun retryDelaySeconds(headers: Headers, body: String): Long =
        GeminiRateLimitCoordinator.parseRetryDelaySeconds(
            headers[GeminiRateLimitCoordinator.HEADER_RETRY_AFTER],
        )
            ?: retryDelayFromBody(body)
            ?: GeminiRateLimitCoordinator.DEFAULT_RETRY_SECONDS

    private fun retryDelayFromBody(body: String): Long? = runCatching {
        json.decodeFromString<GeminiErrorEnvelope>(body).error?.details
            ?.firstNotNullOfOrNull { detail ->
                GeminiRateLimitCoordinator.parseRetryDelaySeconds(
                    detail[FIELD_RETRY_DELAY]?.jsonPrimitive?.content,
                )
            }
    }.getOrNull()

    /** The provider's own wording about our request. Never the document and never the question. */
    private fun reasonFrom(body: String): String = runCatching {
        json.decodeFromString<GeminiErrorEnvelope>(body).error?.message.orEmpty()
    }.getOrElse { "" }.ifBlank { "sin detalle" }

    /**
     * A 401 does not become a 200 by asking again, and retrying a 429 without honouring its wait makes
     * things worse. Only the transient ones come back.
     *
     * Unlike the summary there is no `BlankSummary` case here: a blank answer is caught by the
     * validator upstairs, where the page count needed to judge it lives.
     */
    private fun GeminiRefusal.isWorthRetrying(attempt: Int): Boolean = when (this) {
        is GeminiRefusal.HttpError -> code >= HTTP_SERVER_ERROR && attempt < MAX_ATTEMPTS - 1
        GeminiRefusal.Network -> attempt < MAX_ATTEMPTS - 1
        GeminiRefusal.BlankSummary, GeminiRefusal.NotConfigured, GeminiRefusal.Malformed,
        GeminiRefusal.QuotaDay, is GeminiRefusal.QuotaMinute,
        -> false
    }

    private fun rejected(reason: GeminiRefusal) = GeminiChatResult.Rejected(reason)

    /**
     * What happened, in the log.
     *
     * The screen gets one plain sentence with no status codes in it (FR-031), which is also why this
     * has to exist: without it a 400, a 503 and an answer that would not parse are indistinguishable
     * from outside. **Never the credential, never the document, never the question, never the answer.**
     */
    private fun report(what: String) = crashReporter.log("chat: $what")

    companion object {
        const val API_VERSION = OkHttpGeminiDocumentUploader.API_VERSION
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"

        private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        private const val THINKING_LEVEL = "MINIMAL"
        private const val FINISH_STOP = "STOP"
        private const val FIELD_RETRY_DELAY = "retryDelay"

        /**
         * A quarter of the summary's, because an answer to a bulletin is short by design. Touching
         * this ceiling means the prompt is wrong, and it should show.
         */
        private const val MAX_OUTPUT_TOKENS = 2_000
        private const val MAX_ATTEMPTS = 3
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
