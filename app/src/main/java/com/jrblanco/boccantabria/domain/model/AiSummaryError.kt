package com.jrblanco.boccantabria.domain.model

/**
 * Why there is no summary.
 *
 * A hierarchy of its own rather than new cases on [DomainError], which stays at two. Widening
 * `DomainError` would force the document preview and every other screen to handle situations that
 * mean nothing to them, and the project already has the precedent of a status carrying its own error
 * (research.md D-026).
 *
 * One case per message in FR-040. Being sealed is what makes the compiler complain if a case is
 * added later and the screen forgets it.
 */
sealed interface AiSummaryError {

    /** No connection, and nothing stored to fall back on. */
    data object Offline : AiSummaryError

    /**
     * The service could not read the document.
     *
     * This slot used to mean the opposite end of the same problem: "scanned or empty, so we could not
     * pull any text out of it, and it never reaches the service". That judgement was ours because
     * what travelled **was** the text. Now the document itself travels and a scan is a valid input,
     * so the case that cannot happen any more is gone and the one that can starts here: the upload
     * was accepted and the service still failed to process it (010 research.md D-217, FR-002).
     *
     * Not retryable, for the same reason it never was: trying again cannot change it.
     */
    data object UnreadableDocument : AiSummaryError

    data object EncryptedPdf : AiSummaryError

    /** The per-minute allowance ran out. Worth waiting for. */
    data class QuotaMinute(val secondsRemaining: Long) : AiSummaryError {
        init {
            require(secondsRemaining >= 0) {
                "secondsRemaining must not be negative, was: $secondsRemaining"
            }
        }
    }

    /** The daily allowance ran out. Not worth an immediate retry (FR-039). */
    data object QuotaDay : AiSummaryError

    /** The credential is missing or refused. A limitation of the application, not a hiccup. */
    data object NotConfigured : AiSummaryError

    /** Empty, malformed, or it did not survive validation. Nothing is shown and nothing is stored. */
    data object InvalidResponse : AiSummaryError

    data object Unknown : AiSummaryError

    /** Whether to offer trying again. Offering it where it cannot help is its own kind of lie. */
    val isRetryable: Boolean
        get() = when (this) {
            Offline, is QuotaMinute, InvalidResponse, Unknown -> true
            UnreadableDocument, EncryptedPdf, QuotaDay, NotConfigured -> false
        }
}
