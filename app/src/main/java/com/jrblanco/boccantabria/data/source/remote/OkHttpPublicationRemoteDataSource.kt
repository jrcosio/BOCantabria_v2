package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.RandomProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Reads a source over HTTP.
 *
 * The timings come from the feed specification and are not arbitrary: several responses were
 * observed to take seconds, so the aggressive three-to-five-second budgets a mobile client
 * usually wants would turn a slow service into a broken one.
 *
 * Retries only where retrying can help, with growing waits and jitter. The jitter is injected
 * rather than drawn from a global generator, which is what lets the policy be tested without
 * flakiness.
 */
class OkHttpPublicationRemoteDataSource(
    private val client: OkHttpClient,
    private val parser: BocRssParser,
    private val dispatchers: DispatcherProvider,
    private val random: RandomProvider,
) : PublicationRemoteDataSource {

    override suspend fun fetchFeed(
        definition: BocFeedDefinition,
        knownBodyHash: String?,
    ): FeedFetchResult {
        if (!definition.url.startsWith(HTTPS_PREFIX)) {
            return FeedFetchResult.Failed(FeedFailure.CLIENT_ERROR)
        }

        var lastFailure = FeedFailure.NETWORK
        repeat(MAX_ATTEMPTS) { attempt ->
            when (val outcome = attempt(definition, knownBodyHash)) {
                is FeedFetchResult.Failed -> {
                    lastFailure = outcome.cause
                    if (!outcome.cause.isRetryable || attempt == MAX_ATTEMPTS - 1) {
                        return outcome
                    }
                    delay(backoffMillis(attempt))
                }

                else -> return outcome
            }
        }
        return FeedFetchResult.Failed(lastFailure)
    }

    private suspend fun attempt(
        definition: BocFeedDefinition,
        knownBodyHash: String?,
    ): FeedFetchResult = withContext(dispatchers.io) {
        try {
            // `await`, not `execute()`: cancelling the coroutine cancels the call (feature 014,
            // PERF-002; research.md D-617).
            client.newCall(request(definition)).await { response -> readResponse(response, knownBodyHash) }
        } catch (cancellation: CancellationException) {
            // Never translated into a failure: cancelling a coroutine is not a network problem.
            throw cancellation
        } catch (error: IOException) {
            // A cancelled call surfaces as an IOException on OkHttp's side too, and before feature 014
            // it was classified NETWORK — which is retryable — so cancelling could cost three attempts.
            // Only a live coroutine may report a failure. `SocketTimeoutException` is an IOException,
            // so it is told apart here rather than in a catch of its own that would skip the check.
            currentCoroutineContext().ensureActive()
            FeedFetchResult.Failed(if (error is SocketTimeoutException) FeedFailure.TIMEOUT else FeedFailure.NETWORK)
        }
    }

    private fun readResponse(response: Response, knownBodyHash: String?): FeedFetchResult {
        if (!response.isSuccessful) {
            return FeedFetchResult.Failed(response.code.toFailure())
        }

        val contentType = response.header(HEADER_CONTENT_TYPE).orEmpty().lowercase()
        if (contentType.isNotEmpty() && !contentType.contains(XML_MARKER)) {
            return FeedFetchResult.Failed(FeedFailure.UNUSABLE_BODY)
        }

        val body = response.body ?: return FeedFetchResult.Failed(FeedFailure.UNUSABLE_BODY)
        val source = body.source()
        // Reads at most one byte past the cap: enough to know it was exceeded, without pulling a
        // hostile response into memory.
        source.request(MAX_BODY_BYTES + 1)
        if (source.buffer.size > MAX_BODY_BYTES) {
            return FeedFetchResult.Failed(FeedFailure.UNUSABLE_BODY)
        }

        val text = source.buffer.readString(Charsets.UTF_8)
        val hash = sha256(text)
        if (knownBodyHash != null && knownBodyHash == hash) return FeedFetchResult.NotModified

        return try {
            FeedFetchResult.Fetched(parser.parse(text), hash)
        } catch (_: BocRssParseException) {
            FeedFetchResult.Failed(FeedFailure.MALFORMED)
        }
    }

    private fun request(definition: BocFeedDefinition): Request = Request.Builder()
        .url(definition.url)
        .header(HEADER_ACCEPT, ACCEPT_XML)
        .header(HEADER_ACCEPT_CHARSET, "utf-8")
        .header(HEADER_USER_AGENT, USER_AGENT)
        .get()
        .build()

    private fun Int.toFailure(): FeedFailure = when {
        this == HTTP_REQUEST_TIMEOUT || this == HTTP_TOO_MANY_REQUESTS -> FeedFailure.THROTTLED
        this >= HTTP_SERVER_ERROR_FLOOR -> FeedFailure.SERVER_ERROR
        else -> FeedFailure.CLIENT_ERROR
    }

    /** Growing waits plus injected jitter, so nineteen sources do not all come back at once. */
    private fun backoffMillis(attempt: Int): Long =
        BACKOFF_MILLIS[attempt] + random.nextLong(JITTER_MILLIS)

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 45L
        const val CALL_TIMEOUT_SECONDS = 60L

        const val MAX_ATTEMPTS = 3
        val BACKOFF_MILLIS = longArrayOf(2_000, 5_000, 15_000)
        const val JITTER_MILLIS = 1_000L

        /** Safety cap from the feed specification. */
        const val MAX_BODY_BYTES = 5L * 1024 * 1024

        const val USER_AGENT = "BOC-Cantabria/2.0 (+https://github.com/jrcosio/BOCantabria_v2)"
        const val ACCEPT_XML = "application/rss+xml, application/xml, text/xml"

        private const val HTTPS_PREFIX = "https://"
        private const val XML_MARKER = "xml"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_ACCEPT_CHARSET = "Accept-Charset"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR_FLOOR = 500
    }
}

/**
 * Built here and not in `core/di` for the same reason as the database: an architecture rule keeps
 * third-party SDKs out of the dependency-injection package.
 */
fun bocHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(OkHttpPublicationRemoteDataSource.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(OkHttpPublicationRemoteDataSource.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(OkHttpPublicationRemoteDataSource.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    // The policy lives in the data source, where it can be tested with injected waits. Leaving
    // OkHttp's own retry on as well would silently double the attempts.
    .retryOnConnectionFailure(false)
    .build()
