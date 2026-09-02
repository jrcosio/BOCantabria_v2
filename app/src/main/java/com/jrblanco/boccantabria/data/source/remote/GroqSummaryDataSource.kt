package com.jrblanco.boccantabria.data.source.remote

/**
 * The one way out to the summarising service.
 *
 * An interface because the model behind it is listed as **preview, not production**, and preview
 * models get retired. Keeping the call behind this seam turns that into a new implementation rather
 * than a refactor (research.md D-010).
 */
interface GroqSummaryDataSource {

    /**
     * @param estimatedTokens what the request is expected to cost, so the allowance can be checked
     *   **before** going out rather than discovered as a 429.
     */
    suspend fun summarise(
        system: String,
        user: String,
        estimatedTokens: Int,
    ): GroqSummaryResult
}

sealed interface GroqSummaryResult {

    data class Success(
        val payload: GroqSummaryPayload,
        val usage: GroqUsage,
        val systemFingerprint: String?,
    ) : GroqSummaryResult

    data class Rejected(val reason: GroqRefusal) : GroqSummaryResult
}

/**
 * Why the request did not produce a summary.
 *
 * Each of these maps to one message in FR-040, and none of them ever reaches the screen as itself:
 * no status codes, no traces, no wording from the provider.
 */
sealed interface GroqRefusal {

    /** No credential, or the service refused it. Configuration, not a hiccup. */
    data object NotConfigured : GroqRefusal

    data object Network : GroqRefusal

    /** The body did not parse, or came back without a choice in it. */
    data object Malformed : GroqRefusal

    /**
     * The answer parsed but says nothing: a summary with no summary in it.
     *
     * Seen on a real phone, and it is worth telling apart from [Malformed]: the shape was fine and it
     * came back in a second and a half, which is the service giving up rather than misbehaving. Worth
     * one retry before troubling the reader (research.md D-032).
     */
    data object BlankSummary : GroqRefusal

    data class QuotaMinute(val secondsRemaining: Long) : GroqRefusal

    data object QuotaDay : GroqRefusal

    data class HttpError(val code: Int) : GroqRefusal
}
