package com.jrblanco.boccantabria.domain.model

/**
 * How far along the current question is. What the conversation screen observes.
 *
 * **There is deliberately no `WaitingForQuota`.** The summary has one because it is an operation asked
 * for once that can finish on its own; a question resumed a minute later, when whoever asked may well
 * have left, is an answer out of time (011 research.md D-319).
 */
sealed interface AiChatStatus {

    /** Nothing in flight. Observing the conversation never starts work. */
    data object Idle : AiChatStatus

    /**
     * The document is being made ready, before anything is asked.
     *
     * The two phases are **the same two** the summary shows, and on purpose: it is the same
     * preparation, extracted into one class (D-315). They are declared here rather than reused from
     * `AiSummaryStatus` because importing the summary's state into the chat's would tie two screens
     * that have no reason to move together.
     */
    data class Preparing(val phase: Phase) : AiChatStatus {
        enum class Phase { FETCHING_DOCUMENT, UPLOADING_DOCUMENT }
    }

    /** The request is in flight. No fraction to show: the answer cannot stream (D-306). */
    data object Thinking : AiChatStatus

    /**
     * @param retryableQuestionId which question «Retry» would resend, or `null` when trying again
     *   cannot help. It is what lets the failure be recovered **without making anyone retype**
     *   (FR-033, D-320).
     */
    data class Failed(
        val error: AiChatError,
        val retryableQuestionId: String?,
    ) : AiChatStatus
}
