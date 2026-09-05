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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The one way out to the summarising service.
 *
 * Feature 010 changed **what travels**: the request used to carry the document's text as a JSON
 * string and now it carries a reference to a document already uploaded through the Files API. What
 * did **not** change is everything the layers above depend on — the seven [GeminiRefusal] cases, one
 * for one, each feeding a message on the screen and an instrumented test.
 *
 * It also moved from the Interactions API to `generateContent`, because that is the surface the Files
 * API references belong to.
 *
 * The official Kotlin library was adopted and then withdrawn: its Android artifact throws on
 * construction when given an API key. See research.md D-227.
 */
class OkHttpGeminiSummaryDataSource(
    client: OkHttpClient,
    private val apiKeys: GeminiApiKeyProvider,
    private val coordinator: GeminiRateLimitCoordinator,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
    private val baseUrl: String = OkHttpGeminiDocumentUploader.DEFAULT_BASE_URL,
) : GeminiSummaryDataSource {

    private val client = client.newBuilder().build()

    /**
     * `encodeDefaults` is not cosmetic: without it kotlinx-serialization omits any property equal to
     * its default, and the settings this feature depends on — the thinking level above all — would
     * never be sent. The provider's own default for that is reasoning **on**, and billed.
     */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun summarise(
        system: String,
        user: String,
        document: UploadedDocument,
    ): GeminiSummaryResult = withContext(dispatchers.io) {
        val key = apiKeys.apiKey() ?: return@withContext rejected(GeminiRefusal.NotConfigured)

        // One at a time, across the whole application.
        coordinator.serialised {
            when (val verdict = coordinator.verdict()) {
                QuotaVerdict.ExhaustedDay -> rejected(GeminiRefusal.QuotaDay)
                is QuotaVerdict.WaitMinute ->
                    rejected(GeminiRefusal.QuotaMinute(verdict.secondsRemaining))
                QuotaVerdict.Allowed -> attempt(key, system, user, document)
            }
        }
    }

    private suspend fun attempt(
        key: String,
        system: String,
        user: String,
        document: UploadedDocument,
    ): GeminiSummaryResult {
        var lastRefusal: GeminiRefusal = GeminiRefusal.Network

        repeat(MAX_ATTEMPTS) { attempt ->
            when (val outcome = execute(key, system, user, document)) {
                is GeminiSummaryResult.Success -> return outcome
                is GeminiSummaryResult.Rejected -> {
                    lastRefusal = outcome.reason
                    if (!outcome.reason.isWorthRetrying(attempt)) return outcome
                    // A retry that cannot run must not change the answer. Asking again when there is
                    // no room left comes back as a quota problem, and the reader is then told about a
                    // limit when what actually happened was an empty summary.
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
        user: String,
        document: UploadedDocument,
    ): GeminiSummaryResult = try {
        val payload = GeminiGenerateRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = system))),
            contents = listOf(
                GeminiContent(
                    role = ROLE_USER,
                    parts = listOf(
                        // The document, by reference. It was uploaded once and stays there for as
                        // long as the reader is in this publication.
                        GeminiPart(
                            fileData = GeminiFileData(
                                fileUri = document.fileUri,
                                mimeType = document.mimeType,
                            ),
                        ),
                        GeminiPart(text = user),
                    ),
                ),
            ),
            generationConfig = GeminiGenerationConfig(
                // The provider's default is "medium", and reasoning is billed (009 D-106).
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = THINKING_LEVEL),
                maxOutputTokens = MAX_OUTPUT_TOKENS,
                responseMimeType = MEDIA_TYPE_JSON,
                // The schema, verbatim: its property **order** is payload, and rewriting it would
                // put that bomb back on the table for nothing (D-211).
                responseJsonSchema = SummarySchema.value,
            ),
        )

        val request = Request.Builder()
            .url("$baseUrl/$API_VERSION/models/${AiSummaryConstants.MODEL_ID}:generateContent")
            // The credential travels in the header and never in the body or the query string, so
            // that a body or a URL captured for diagnosis cannot carry it.
            .header(OkHttpGeminiDocumentUploader.HEADER_API_KEY, key)
            .post(json.encodeToString(payload).toRequestBody(CONTENT_TYPE_JSON.toMediaType()))
            .build()

        // Counted when it goes out, not when it comes back: what spends the allowance is asking.
        // Three 500s spend three requests of the daily quota for zero summaries.
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
                    // The status code never reaches the screen (FR-028), and that is precisely why it
                    // has to reach the log: a 400 and a 503 are the same sentence to the reader and
                    // completely different problems to whoever has to fix it. The reason travels too;
                    // it is the provider's own wording about our request, never the document.
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
        // cancelled.** `Call.execute()` blocks; cancelling tears the socket down, and what comes out
        // is an `IOException` — `SocketException: Software caused connection abort` on a real phone.
        // Without this line, leaving the screen mid-request is reported as `Network`, and the reader
        // sees «No hay conexión» for a failure that never happened. Seen on a device on 4 September
        // 2026; there is a regression test.
        currentCoroutineContext().ensureActive()
        report("network: ${error.javaClass.simpleName}: ${error.message}")
        rejected(GeminiRefusal.Network)
    }

    private fun parse(body: String): GeminiSummaryResult = try {
        val answer = json.decodeFromString<GeminiGenerateResponse>(body)
        val candidate = answer.candidates.firstOrNull()
        val finish = candidate?.finishReason
        // `MAX_TOKENS` used to have to be deduced from JSON that would not parse, and the reader was
        // told «no se ha podido construir un resumen fiable» — our problem wearing the service's
        // clothes. Said plainly now.
        if (finish != null && finish != FINISH_STOP) report("finishReason=$finish")

        // Reasoning parts are skipped **by their flag and never by position**: the reasoning step
        // arrives before the answer every single time, so taking the first part would have been
        // wrong a hundred per cent of the time (009 research.md D-117).
        val content = candidate?.content?.parts
            ?.filter { it.thought != true }
            ?.firstNotNullOfOrNull { it.text?.takeIf(String::isNotBlank) }

        if (content.isNullOrBlank()) {
            report("no text, finishReason=$finish, ${answer.usageMetadata?.candidatesTokenCount ?: 0} output tokens")
            rejected(GeminiRefusal.Malformed)
        } else {
            val payload = json.decodeFromString<SummaryPayload>(content)
            if (payload.plainLanguageSummary.isBlank()) {
                // Which keys came back, never what they held: the field names are our own schema,
                // the values are the document (FR-036).
                report(
                    "blank summary: ${describe(content)}, finishReason=$finish, " +
                        "${answer.usageMetadata?.candidatesTokenCount ?: 0} output tokens",
                )
                rejected(GeminiRefusal.BlankSummary)
            } else {
                GeminiSummaryResult.Success(
                    payload = payload,
                    usage = answer.toUsage(),
                    // Which exact version answered. Same nullable column as before.
                    systemFingerprint = answer.modelVersion,
                )
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        // The *kind* of failure and its size, never what the body held: it is the document.
        report("unparseable answer of ${body.length} chars: ${error.javaClass.simpleName}")
        rejected(GeminiRefusal.Malformed)
    }

    /**
     * How long the service asked us to wait, from the header first and from a `RetryInfo` in the
     * error details second.
     *
     * The documentation promises neither, so both are tried and a default is used when neither
     * arrives. The number is what classifies a 429 as of the minute or of the day, so it must never
     * be missing (009 research.md D-109).
     */
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

    /** The provider's own wording about our request. Never the document. */
    private fun reasonFrom(body: String): String = runCatching {
        json.decodeFromString<GeminiErrorEnvelope>(body).error?.message.orEmpty()
    }.getOrElse { "" }.ifBlank { "sin detalle" }

    /**
     * A 401 does not become a 200 by asking again, and retrying a 429 without honouring its wait
     * makes the situation worse. Only the transient ones come back.
     */
    private fun GeminiRefusal.isWorthRetrying(attempt: Int): Boolean = when (this) {
        is GeminiRefusal.HttpError -> code >= HTTP_SERVER_ERROR && attempt < MAX_ATTEMPTS - 1
        GeminiRefusal.Network -> attempt < MAX_ATTEMPTS - 1
        // Once, and only once. An answer that came back empty is the service giving up, not a
        // document that cannot be summarised — and asking again usually works. Twice would be
        // spending a shared allowance on hope.
        GeminiRefusal.BlankSummary -> attempt < 1
        GeminiRefusal.NotConfigured, GeminiRefusal.Malformed, GeminiRefusal.QuotaDay,
        is GeminiRefusal.QuotaMinute,
        -> false
    }

    /**
     * The shape of an answer: which fields arrived and how full each one was. Never the values.
     *
     * A summary that parsed into nothing looks identical from the outside to one whose field names we
     * failed to match, and those are opposite problems.
     */
    private fun describe(content: String): String = runCatching {
        Json.parseToJsonElement(content).jsonObject.entries.joinToString(" ") { (key, value) ->
            val size = when (value) {
                is JsonArray -> value.size
                is JsonPrimitive -> value.content.length
                else -> value.toString().length
            }
            "$key=$size"
        }
    }.getOrElse { "unreadable shape" }

    private fun rejected(reason: GeminiRefusal) = GeminiSummaryResult.Rejected(reason)

    /**
     * What went wrong, in the log.
     *
     * The screen gets one plain sentence with no status codes in it, which is right (FR-028) — and it
     * is also why this line has to exist. Without it a 400, a 503 and an answer that would not parse
     * are indistinguishable from the outside, and the first time this ran on a real phone that is
     * exactly where the trail went cold. **Never the credential and never the document.**
     */
    private fun report(what: String) = crashReporter.log("gemini: $what")

    companion object {
        const val API_VERSION = OkHttpGeminiDocumentUploader.API_VERSION
        const val MEDIA_TYPE_JSON = "application/json"
        const val ROLE_USER = "user"

        private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        private const val THINKING_LEVEL = "MINIMAL"
        private const val FINISH_STOP = "STOP"
        private const val FIELD_RETRY_DELAY = "retryDelay"

        /**
         * Not 65 536, on purpose: if an answer ever reached 8 000, something is wrong with the prompt
         * and it should show. The provider bills output **used**, not reserved (009 D-103).
         */
        private const val MAX_OUTPUT_TOKENS = 8_000
        private const val MAX_ATTEMPTS = 3
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
