package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
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
import java.util.concurrent.TimeUnit

/**
 * Talks to the summarising service over plain OkHttp.
 *
 * The client is **derived** from the shared one with `newBuilder()`, exactly as the document
 * downloader does and for the same reason: it shares the connection pool instead of duplicating it.
 * Retrofit would have been a new dependency for a single POST, and feature 009 added no dependency
 * at all (009 research.md D-102).
 *
 * The endpoint is the **Interactions API**, generally available since June 2026 and what the provider
 * recommends for new code; `generateContent` is labelled legacy. It also gives two things the other
 * does not: `store: false` for zero retention, and an interaction `status` that diagnoses better than
 * a `finish_reason` (009 research.md D-103).
 *
 * **There is no body-level logging interceptor here, and there must never be one.** It would put the
 * credential and the whole document into the system log, which is precisely what FR-032 and SC-010
 * forbid.
 */
class OkHttpGeminiSummaryDataSource(
    client: OkHttpClient,
    private val apiKeys: GeminiApiKeyProvider,
    private val coordinator: GeminiRateLimitCoordinator,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : GeminiSummaryDataSource {

    private val client = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * `encodeDefaults` is not cosmetic, and it is the same trap under a new name.
     *
     * Without it kotlinx-serialization omits any property equal to its default, so `store` and
     * `thinking_level` would never be sent — and the service's own defaults for both are the
     * opposite of what this feature wants: retention **on** and reasoning at `medium`, billed.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun summarise(
        system: String,
        user: String,
    ): GeminiSummaryResult = withContext(dispatchers.io) {
        val key = apiKeys.apiKey() ?: return@withContext rejected(GeminiRefusal.NotConfigured)

        // One at a time, across the whole application.
        coordinator.serialised {
            when (val verdict = coordinator.verdict()) {
                QuotaVerdict.ExhaustedDay -> rejected(GeminiRefusal.QuotaDay)
                is QuotaVerdict.WaitMinute ->
                    rejected(GeminiRefusal.QuotaMinute(verdict.secondsRemaining))
                QuotaVerdict.Allowed -> attempt(key, system, user)
            }
        }
    }

    private suspend fun attempt(key: String, system: String, user: String): GeminiSummaryResult {
        var lastRefusal: GeminiRefusal = GeminiRefusal.Network

        repeat(MAX_ATTEMPTS) { attempt ->
            when (val outcome = execute(key, system, user)) {
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

    private suspend fun execute(key: String, system: String, user: String): GeminiSummaryResult = try {
        val payload = GeminiInteractionRequest(
            model = AiSummaryConstants.MODEL_ID,
            systemInstruction = system,
            input = listOf(GeminiInputContent(type = INPUT_TYPE_TEXT, text = user)),
            // Zero retention. The default is `true` (009 research.md D-107, FR-030).
            store = false,
            generationConfig = GeminiGenerationConfig(
                // The default is "medium", and thinking is billed (D-106).
                thinkingLevel = THINKING_LEVEL,
                maxOutputTokens = MAX_OUTPUT_TOKENS,
            ),
            responseFormat = GeminiResponseFormat(schema = SummarySchema.value),
        )

        val request = Request.Builder()
            .url(baseUrl)
            // The credential travels in the header and never in the body or the query string, so
            // that a body or a URL captured for diagnosis cannot carry it.
            .header(HEADER_API_KEY, key)
            .header("Content-Type", MEDIA_TYPE)
            .post(json.encodeToString(payload).toRequestBody(MEDIA_TYPE.toMediaType()))
            .build()

        // Counted when it goes out, not when it comes back: what spends the allowance is asking.
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
                        is QuotaVerdict.WaitMinute ->
                            rejected(GeminiRefusal.QuotaMinute(seconds))
                        QuotaVerdict.Allowed -> rejected(GeminiRefusal.QuotaMinute(seconds))
                    }
                }

                !response.isSuccessful -> {
                    // The status code never reaches the screen (FR-027), and that is precisely why it
                    // has to reach the log: a 400 and a 503 are the same sentence to the reader and
                    // completely different problems to whoever has to fix it.
                    //
                    // And the code alone is not enough. The service explains its refusals in the
                    // body, so the reason travels too. It is the provider's own wording about our
                    // request, never the document: what we sent is described, not quoted.
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
        // The catch above therefore never fires, and without this line leaving the screen mid-request
        // is reported as `Network`, which the reader sees as «No hay conexión» for a failure that
        // never happened. Worse, `fail()` publishes it, and in `observeSummary` the in-flight state
        // wins over the stored summary, so the wrong error is still there on the way back.
        //
        // `generate()` already knew what to do with a cancellation — it clears the state and reports
        // nothing, with FR-006 quoted in the comment. What was missing was letting the cancellation
        // reach it. Seen on a device on 4 September 2026; there is a regression test.
        currentCoroutineContext().ensureActive()
        report("network: ${error.javaClass.simpleName}: ${error.message}")
        rejected(GeminiRefusal.Network)
    }

    private fun parse(body: String): GeminiSummaryResult = try {
        val interaction = json.decodeFromString<GeminiInteraction>(body)
        val status = interaction.status
        // `incomplete` and `budget_exceeded` mean our own ceiling or the service's cut the answer,
        // not that it misbehaved. Said plainly, because the two look identical once the JSON fails to
        // parse — and with the previous provider this had to be instrumented after the fact.
        if (status != null && status != STATUS_COMPLETED) report("status=$status")

        // Found by its type and never by position: the answer may carry reasoning steps in front,
        // and depending on the order would be depending on something the provider does not promise.
        val content = interaction.steps
            .firstOrNull { it.type == STEP_MODEL_OUTPUT }
            ?.content
            ?.firstOrNull { !it.text.isNullOrBlank() }
            ?.text

        if (content.isNullOrBlank()) {
            report("no model_output, ${interaction.steps.size} step(s), status=$status")
            rejected(GeminiRefusal.Malformed)
        } else {
            val payload = json.decodeFromString<SummaryPayload>(content)
            if (payload.plainLanguageSummary.isBlank()) {
                // Which keys came back, never what they held: the field names are our own schema,
                // the values are the document (FR-032). Without this there is no way to tell an
                // answer the service gave up on from one whose shape we failed to read.
                report(
                    "blank summary: ${describe(content)}, status=$status, " +
                        "${interaction.usage?.totalOutputTokens ?: 0} output tokens",
                )
                rejected(GeminiRefusal.BlankSummary)
            } else {
                GeminiSummaryResult.Success(
                    payload = payload,
                    usage = interaction.usage ?: GeminiUsage(),
                    // No equivalent in this service. The column is already nullable.
                    systemFingerprint = null,
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
     * How long the service asked us to wait, read from the header first and from a `RetryInfo` in the
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
     * The screen gets one plain sentence with no status codes in it, which is right (FR-027) — and it
     * is also why this line has to exist. Without it a 400, a 503 and an answer that would not parse
     * are indistinguishable from the outside, and the first time the previous provider ran on a real
     * phone that is exactly where the trail went cold. **Never the credential and never the
     * document.**
     */
    private fun report(what: String) = crashReporter.log("gemini: $what")

    /**
     * The provider's explanation for a refusal, and nothing else.
     *
     * Only `error.message` is taken, and only the first [MAX_REASON_LENGTH] characters of it: it is
     * the service talking about our request, not about the document. If the body is not the shape we
     * expect, its **length** is reported rather than its content.
     */
    private fun reasonFrom(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
            ?.get("message")?.jsonPrimitive?.content
            ?.take(MAX_REASON_LENGTH)
            ?: "no message, ${body.length} chars"
    }.getOrElse { "unreadable body, ${body.length} chars" }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"

        const val HEADER_API_KEY = "x-goog-api-key"

        private const val MEDIA_TYPE = "application/json"
        private const val INPUT_TYPE_TEXT = "text"
        private const val STEP_MODEL_OUTPUT = "model_output"
        private const val STATUS_COMPLETED = "completed"
        private const val FIELD_RETRY_DELAY = "retryDelay"

        private const val THINKING_LEVEL = "minimal"

        /**
         * Generous on purpose, and it closes a family of failures for good.
         *
         * The previous provider charged the per-minute allowance for
         * `input + max_completion_tokens` **when the request was made**, spent or not — its own 429
         * spelled it out: «Limit 8000, Used 7346, Requested 6475» — so the answer's ceiling had to
         * stay at 1 800 and a real summary once came back at 1 625, fifteen from an earlier ceiling
         * of 1 200. Past a ceiling the JSON arrives cut, does not parse, and the reader is told the
         * service produced nothing reliable — a problem of ours dressed up as one of theirs.
         *
         * **This service charges the output actually used.** So a high ceiling costs nothing. Not the
         * model's full 65 536 though: eight thousand is nearly five times the largest real answer,
         * and leaving room to the maximum would waste a useful signal — an answer that reaches this
         * ceiling means something is wrong in the prompt, and it should show
         * (009 research.md D-110).
         */
        private const val MAX_OUTPUT_TOKENS = 8_000

        private const val MAX_ATTEMPTS = 3

        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 90L
        private const val WRITE_TIMEOUT_SECONDS = 30L

        private const val MAX_REASON_LENGTH = 400

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
