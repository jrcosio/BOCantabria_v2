package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
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
 * Retrofit would have been a new dependency for a single POST (research.md D-013).
 *
 * **There is no body-level logging interceptor here, and there must never be one.** It would put the
 * credential and the whole document into the system log, which is precisely what FR-047 and SC-009
 * forbid.
 */
class OkHttpGroqSummaryDataSource(
    client: OkHttpClient,
    private val apiKeys: GroqApiKeyProvider,
    private val coordinator: GroqRateLimitCoordinator,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : GroqSummaryDataSource {

    private val client = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * `encodeDefaults` is not cosmetic. Without it kotlinx-serialization omits any property equal to
     * its default, so `stream` and `reasoning_effort` would never be sent — and the provider's own
     * default for `reasoning_effort` on this model is reasoning **on**. Those tokens come out of the
     * same 8.000-a-minute allowance and are never shown to anyone.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun summarise(
        system: String,
        user: String,
        estimatedTokens: Int,
    ): GroqSummaryResult = withContext(dispatchers.io) {
        val key = apiKeys.apiKey() ?: return@withContext rejected(GroqRefusal.NotConfigured)

        // One at a time, across the whole application: one summary costs most of a minute's
        // allowance, so two at once would guarantee the second a 429.
        coordinator.serialised {
            when (val verdict = coordinator.verdict(estimatedTokens)) {
                QuotaVerdict.ExhaustedDay -> rejected(GroqRefusal.QuotaDay)
                is QuotaVerdict.WaitMinute ->
                    rejected(GroqRefusal.QuotaMinute(verdict.secondsRemaining))
                QuotaVerdict.Allowed -> attempt(key, system, user, estimatedTokens)
            }
        }
    }

    private suspend fun attempt(
        key: String,
        system: String,
        user: String,
        estimatedTokens: Int,
    ): GroqSummaryResult {
        var lastRefusal: GroqRefusal = GroqRefusal.Network

        repeat(MAX_ATTEMPTS) { attempt ->
            when (val outcome = execute(key, system, user)) {
                is GroqSummaryResult.Success -> return outcome
                is GroqSummaryResult.Rejected -> {
                    lastRefusal = outcome.reason
                    if (!outcome.reason.isWorthRetrying(attempt)) return outcome
                    // A retry that cannot run must not change the answer. The allowance is charged on
                    // `input + max_completion_tokens` when the request is *made*, so a second attempt
                    // in the same minute almost always comes back as a quota problem — and the reader
                    // is then told about a limit when what actually happened was an empty summary.
                    if (coordinator.verdict(estimatedTokens) != QuotaVerdict.Allowed) return outcome
                    delay(coordinator.backoffMillis(attempt))
                }
            }
        }
        return rejected(lastRefusal)
    }

    private fun execute(key: String, system: String, user: String): GroqSummaryResult = try {
        val payload = GroqChatRequest(
            model = AiSummaryConstants.MODEL_ID,
            messages = listOf(GroqMessage(ROLE_SYSTEM, system), GroqMessage(ROLE_USER, user)),
            temperature = TEMPERATURE,
            maxCompletionTokens = MAX_COMPLETION_TOKENS,
            // Obligatory: structured outputs and streaming are not compatible (research.md D-011).
            stream = false,
            // A factual summary does not need reasoning exposed, and those tokens come out of the
            // same allowance while never being shown.
            reasoningEffort = REASONING_EFFORT,
            responseFormat = GroqSummarySchema.value,
        )

        val request = Request.Builder()
            .url(baseUrl)
            // The credential travels in the header and never in the body, so that a body captured
            // for diagnosis cannot carry it.
            .header("Authorization", "Bearer $key")
            .header("Content-Type", MEDIA_TYPE)
            .post(json.encodeToString(payload).toRequestBody(MEDIA_TYPE.toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val headers = response.headers.toSimpleMap()
            coordinator.record(headers)

            when {
                response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN ->
                    rejected(GroqRefusal.NotConfigured).also { report("HTTP ${response.code}") }

                response.code == HTTP_TOO_MANY_REQUESTS -> {
                    val seconds = coordinator.recordRetryAfter(headers) ?: DEFAULT_RETRY_SECONDS
                    rejected(GroqRefusal.QuotaMinute(seconds))
                }

                !response.isSuccessful -> {
                    // The status code never reaches the screen (FR-040), and that is precisely why it
                    // has to reach the log: a 400 and a 503 are the same sentence to the reader and
                    // completely different problems to whoever has to fix it.
                    //
                    // And the code alone is not enough. This service explains its refusals in the
                    // body — «Limit 8000, Used 7346, Requested 6475» told us more than the 429 ever
                    // could — so the reason travels too. It is the provider's own wording about our
                    // request, never the document: what we sent is described, not quoted.
                    report("HTTP ${response.code}: ${reasonFrom(response.body.string())}")
                    rejected(GroqRefusal.HttpError(response.code))
                }

                else -> parse(response.body.string())
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: IOException) {
        report("network: ${error.javaClass.simpleName}: ${error.message}")
        rejected(GroqRefusal.Network)
    }

    private fun parse(body: String): GroqSummaryResult = try {
        val response = json.decodeFromString<GroqChatResponse>(body)
        val choice = response.choices.firstOrNull()
        val finish = choice?.finishReason
        // `length` means our own ceiling cut the answer, not that the service misbehaved. Said
        // plainly, because the two look identical once the JSON fails to parse.
        if (finish != null && finish != FINISH_STOP) report("finish_reason=$finish")
        val content = choice?.message?.content
        if (content.isNullOrBlank()) {
            report("empty content, ${response.choices.size} choice(s), finish_reason=$finish")
            rejected(GroqRefusal.Malformed)
        } else {
            val payload = json.decodeFromString<GroqSummaryPayload>(content)
            if (payload.plainLanguageSummary.isBlank()) {
                // Which keys came back, never what they held: the field names are our own schema,
                // the values are the document (FR-047). Without this there is no way to tell an
                // answer the service gave up on from one whose shape we failed to read.
                report(
                    "blank summary: ${describe(content)}, finish_reason=$finish, " +
                        "${response.usage?.completionTokens ?: 0} output tokens",
                )
                rejected(GroqRefusal.BlankSummary)
            } else {
                GroqSummaryResult.Success(
                    payload = payload,
                    usage = response.usage ?: GroqUsage(),
                    systemFingerprint = response.systemFingerprint,
                )
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        // The *kind* of failure and its size, never what the body held: it is the document.
        report("unparseable answer of ${body.length} chars: ${error.javaClass.simpleName}")
        rejected(GroqRefusal.Malformed)
    }

    /**
     * A 401 does not become a 200 by asking again, and retrying a 429 without honouring its wait
     * makes the situation worse. Only the transient ones come back (research.md D-016).
     */
    private fun GroqRefusal.isWorthRetrying(attempt: Int): Boolean = when (this) {
        is GroqRefusal.HttpError -> code >= HTTP_SERVER_ERROR && attempt < MAX_ATTEMPTS - 1
        GroqRefusal.Network -> attempt < MAX_ATTEMPTS - 1
        // Once, and only once. An answer that came back empty in a second and a half is the service
        // giving up, not a document that cannot be summarised — and asking again usually works.
        // Twice would be spending a shared allowance on hope.
        GroqRefusal.BlankSummary -> attempt < 1
        GroqRefusal.NotConfigured, GroqRefusal.Malformed, GroqRefusal.QuotaDay,
        is GroqRefusal.QuotaMinute,
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

    private fun Headers.toSimpleMap(): Map<String, String> =
        names().associateWith { name -> this[name].orEmpty() }

    private fun rejected(reason: GroqRefusal) = GroqSummaryResult.Rejected(reason)

    /**
     * What went wrong, in the log.
     *
     * The screen gets one plain sentence with no status codes in it, which is right (FR-040) — and it
     * is also why this line has to exist. Without it a 400, a 503 and an answer that would not parse
     * are indistinguishable from the outside, and the first time this ran on a real phone that is
     * exactly where the trail went cold. **Never the credential and never the document.**
     */
    private fun report(what: String) = crashReporter.log("groq: $what")

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
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"

        private const val ROLE_SYSTEM = "system"
        private const val ROLE_USER = "user"
        private const val MEDIA_TYPE = "application/json"

        private const val TEMPERATURE = 0.2
        /**
         * Raised from 1.200, and it was not arbitrary. A real summary of a selection process came
         * back at **1.185 tokens**: fifteen from the ceiling. Past it the answer arrives cut, the JSON
         * does not parse, and the reader is told the service produced nothing reliable — when what
         * really happened is that we asked for less room than the document needed.
         *
         * The document budget came down by the same amount, because the provider charges the
         * per-minute allowance for `input + max_completion_tokens` whether the answer uses it or not.
         */
        private const val MAX_COMPLETION_TOKENS = 1_800
        private const val REASONING_EFFORT = "none"

        private const val MAX_ATTEMPTS = 3
        private const val DEFAULT_RETRY_SECONDS = 60L

        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 90L
        private const val WRITE_TIMEOUT_SECONDS = 30L

        private const val FINISH_STOP = "stop"
        private const val MAX_REASON_LENGTH = 400

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
