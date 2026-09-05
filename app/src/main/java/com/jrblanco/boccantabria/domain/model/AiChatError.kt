package com.jrblanco.boccantabria.domain.model

/**
 * Why there is no answer.
 *
 * **Its own hierarchy and not `AiSummaryError`**, although six of the eight cases have a twin there.
 * The sentences on screen are different — «no se ha podido responder» is not «no se ha podido generar
 * el resumen» — and a shared enum would end up forcing a `when` with unreachable branches in one of
 * the two screens, which is exactly what principle V forbids. What **is** shared is `GeminiRefusal`:
 * that one is the vocabulary of the transport, not of the screen (011 research.md D-318).
 *
 * One case per message in `strings.xml`, and not one of them carries a status code, a number or the
 * provider's name (FR-031).
 */
sealed interface AiChatError {

    /** No connection. */
    data object Offline : AiChatError

    /** The per-minute allowance ran out. Worth waiting for. */
    data class QuotaMinute(val secondsRemaining: Long) : AiChatError {
        init {
            require(secondsRemaining >= 0) {
                "secondsRemaining must not be negative, was: $secondsRemaining"
            }
        }
    }

    /** The daily allowance ran out. Not worth an immediate retry. */
    data object QuotaDay : AiChatError

    /** No credential, or the service refused it. A limitation of the application, not a hiccup. */
    data object NotConfigured : AiChatError

    /** The document was accepted and the service still could not read it. */
    data object UnreadableDocument : AiChatError

    /** Password-protected. Detected on the device, so it never leaves it (FR-029). */
    data object EncryptedPdf : AiChatError

    /** Empty, malformed, or it did not survive validation. */
    data object InvalidResponse : AiChatError

    /** The document could not be fetched, or anything else the path can throw. */
    data object Unknown : AiChatError

    /** Whether to offer trying again. Offering it where it cannot help is its own kind of lie. */
    val isRetryable: Boolean
        get() = when (this) {
            Offline, is QuotaMinute, InvalidResponse, Unknown -> true
            QuotaDay, NotConfigured, UnreadableDocument, EncryptedPdf -> false
        }
}
