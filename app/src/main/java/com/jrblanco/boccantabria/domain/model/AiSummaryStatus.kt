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
        /**
         * Reading the text on the device used to be the second phase. Since feature 010 the document
         * is uploaded whole and the service reads it, so what happens between fetching and generating
         * is a transfer, not a read.
         */
        enum class Phase { FETCHING_DOCUMENT, UPLOADING_DOCUMENT }
    }

    /**
     * The request is in flight. No fraction to show, for the reason above.
     *
     * It used to carry `analysedPages` alongside the total, and warn before spending the allowance
     * that only the first N pages would be read. That warning is gone with feature 010, and not
     * because it was noisy: **the whole document is sent now**, so "the pages that fit" is not a
     * choice any more and announcing one would be announcing something false. Keeping the field with
     * `analysedPages == totalPages` would have left a branch of the screen and its test alive with no
     * way of ever running, which is exactly what principle V forbids.
     *
     * What survives is the coverage declared **afterwards**, in [AiSummary.SummaryCoverage]: sending
     * the whole document does not guarantee the model read all of it, and the validator still
     * recomputes which pages the summary actually cites (010 data-model.md §5.2).
     */
    data class Generating(val totalPages: Int) : AiSummaryStatus {
        init {
            require(totalPages >= 1) { "a document has at least one page, was: $totalPages" }
        }
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
