package com.jrblanco.boccantabria.data.source.remote

/**
 * Reads one source of the bulletin.
 *
 * **The one interface in the project that reports failure as a value instead of throwing.** It is
 * deliberate: nineteen sources are read concurrently and one falling over must not disturb the
 * other eighteen. Returning the failure keeps the orchestrator a plain fold instead of a ladder
 * of nested catches — the shape in which a `CancellationException` is easiest to swallow by
 * accident.
 */
interface PublicationRemoteDataSource {

    /**
     * @param knownBodyHash digest of the last body processed for this source. When the service
     *   returns the same bytes, the result is [FeedFetchResult.NotModified] and nothing is parsed.
     */
    suspend fun fetchFeed(
        definition: BocFeedDefinition,
        knownBodyHash: String?,
    ): FeedFetchResult
}

sealed interface FeedFetchResult {

    data class Fetched(val channel: RssChannelDto, val bodyHash: String) : FeedFetchResult

    /** The service returned exactly what it returned last time. */
    data object NotModified : FeedFetchResult

    data class Failed(val cause: FeedFailure) : FeedFetchResult
}

/**
 * Why a source could not be read.
 *
 * Split by whether retrying could help, because that is the only decision the policy makes with
 * it: [isRetryable] separates a service having a bad minute from a request that will fail the
 * same way for ever.
 */
enum class FeedFailure {
    /** No route, DNS, TLS: the kind of thing that fixes itself. */
    NETWORK,
    TIMEOUT,

    /** 408 and 429: the service asking for patience. */
    THROTTLED,

    /** 5xx. */
    SERVER_ERROR,

    /** 4xx other than 408 and 429. Retrying would only repeat it. */
    CLIENT_ERROR,

    /** The body was larger than the safety cap, or it was not XML at all. */
    UNUSABLE_BODY,

    /** Well-formed transport, unreadable content. */
    MALFORMED,
    ;

    val isRetryable: Boolean
        get() = this == NETWORK || this == TIMEOUT || this == THROTTLED || this == SERVER_ERROR
}
