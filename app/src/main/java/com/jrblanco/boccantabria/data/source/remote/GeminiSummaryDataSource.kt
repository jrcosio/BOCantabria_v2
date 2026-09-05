package com.jrblanco.boccantabria.data.source.remote

/**
 * The one way out to the summarising service.
 *
 * An interface because the service behind it has already been swapped once: feature 009 replaced the
 * previous provider without the domain, the screen or any of its twenty-one instrumented tests
 * noticing. That is what this seam is for (009 research.md D-101).
 */
interface GeminiSummaryDataSource {

    /**
     * @param document a document already uploaded to the service. It used to be the `user` parameter
     *   that carried the text; since feature 010 `user` carries only the publication's metadata and
     *   the document travels by reference (010 contracts §1.4).
     */
    suspend fun summarise(
        system: String,
        user: String,
        document: UploadedDocument,
    ): GeminiSummaryResult
}

sealed interface GeminiSummaryResult {

    data class Success(
        val payload: SummaryPayload,
        val usage: SummaryUsage,
        /**
         * Which exact version of the model answered. The previous provider sent a fingerprint of its
         * serving configuration and the one after it had no equivalent at all; the library exposes
         * `modelVersion`, which is the same kind of fact. Still nullable, same column.
         */
        val systemFingerprint: String?,
    ) : GeminiSummaryResult

    data class Rejected(val reason: GeminiRefusal) : GeminiSummaryResult
}

/**
 * Why the request did not produce a summary.
 *
 * **The seven cases are kept one for one across a change of provider, and that is an obligation of
 * this contract rather than a coincidence.** They are what keeps the eight messages in
 * `strings.xml`, the mapping in `AiSummaryTab.messageRes()` and twenty-one instrumented tests
 * untouched. Whoever adds or removes a case is changing the application's interface, not just its
 * data layer.
 *
 * None of them ever reaches the screen as itself: no status codes, no traces, no wording from the
 * provider (FR-027, FR-028).
 */
sealed interface GeminiRefusal {

    /** No credential, or the service refused it. Configuration, not a hiccup. */
    data object NotConfigured : GeminiRefusal

    data object Network : GeminiRefusal

    /**
     * The body did not parse, or came back without a `model_output` step in it.
     *
     * Also covers a `status` of `incomplete` or `budget_exceeded` with nothing usable in it — which
     * on screen is the same sentence, and in the log must not be (009 research.md D-117).
     */
    data object Malformed : GeminiRefusal

    /**
     * The answer parsed but says nothing: a summary with no summary in it.
     *
     * Seen on a real phone with the previous provider, and worth telling apart from [Malformed]: the
     * shape was fine, which is the service giving up rather than misbehaving. Worth one retry before
     * troubling the reader.
     */
    data object BlankSummary : GeminiRefusal

    data class QuotaMinute(val secondsRemaining: Long) : GeminiRefusal

    data object QuotaDay : GeminiRefusal

    data class HttpError(val code: Int) : GeminiRefusal
}

/**
 * What a generation cost, in tokens.
 *
 * It lived in `GeminiDtos.kt` and moved here when that file went, because it was never a wire type:
 * the names are ours, nothing serialises it, and it is part of the signature of
 * [GeminiSummaryResult.Success], which survives. It arrives built from the library's `usageMetadata`
 * rather than deserialised from a body, so it carries no serialization annotations any more
 * (010 tasks T041a).
 */
data class SummaryUsage(
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalTokens: Int = 0,
    /** Should be low or zero. If it grows, the thinking level is not being applied. */
    val totalThoughtTokens: Int = 0,
)
