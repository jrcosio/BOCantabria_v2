package com.jrblanco.boccantabria.domain.model

/**
 * How far along the summary of a publication is. What the detail screen observes.
 *
 * Shaped like [DocumentStatus] on purpose: it is the same problem — a long operation with phases,
 * watched by the screen while something else pushes it forward — and the project already has a
 * working answer for it (research.md D-025).
 */
sealed interface AiSummaryStatus {

    /** Nothing stored and nothing happening. Observing never starts work (FR-002). */
    data object Idle : AiSummaryStatus

    /**
     * Local work, before anything leaves the device.
     *
     * The phase is shown rather than hidden behind a spinner because the answer cannot stream: the
     * strict schema and streaming are not compatible, so the wait has to say something (D-011).
     */
    data class Preparing(val phase: Phase) : AiSummaryStatus {
        enum class Phase { FETCHING_DOCUMENT, EXTRACTING_TEXT }
    }

    /**
     * The request is in flight. No fraction to show, for the reason above.
     *
     * It carries how much of the document is going out, because that is the earliest honest moment
     * to say it: the pages that fit are only known once the text has been extracted, and extracting
     * requires the document, which this application does not fetch until somebody asks for it. So
     * the warning lands here — after the text is read, **before** the allowance is spent (FR-028).
     */
    data class Generating(val analysedPages: Int, val totalPages: Int) : AiSummaryStatus {
        init {
            require(analysedPages in 1..totalPages) {
                "cannot send $analysedPages of $totalPages pages"
            }
        }

        val isPartial: Boolean get() = analysedPages < totalPages
    }

    /** Waiting for quota to come back. Continues on its own (FR-038). */
    data class WaitingForQuota(val secondsRemaining: Long) : AiSummaryStatus {
        init {
            require(secondsRemaining >= 0) {
                "secondsRemaining must not be negative, was: $secondsRemaining"
            }
        }
    }

    /**
     * @param isStale the stored summary no longer matches the current document, model, prompt or
     *   schema. It is still shown, marked, with the option to make it again — **never discarded on
     *   the application's own initiative** (FR-035).
     */
    data class Ready(
        val summary: AiSummary,
        val generatedAtEpochMillis: Long,
        val isStale: Boolean,
    ) : AiSummaryStatus

    data class Failed(val error: AiSummaryError) : AiSummaryStatus
}
