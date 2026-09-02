package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AiSummary
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.Publication
import kotlinx.coroutines.flow.Flow

/**
 * The AI summaries of publications.
 *
 * Shaped like [DocumentRepository] deliberately: it is the same problem — a long operation with
 * phases that the screen watches while something else pushes it forward — and the project already
 * has a working answer for it (research.md D-025).
 *
 * Contract, the same as the rest of the project:
 * - Nothing here throws. Failures travel as [AppResult.Failure] or [AiSummaryStatus.Failed].
 * - [observeSummary] never terminates with an error.
 * - `CancellationException` is always rethrown: leaving the screen is not a failure (FR-006).
 *
 * And four of its own, which the specification demands:
 * - **Observing never generates.** Opening the publication, opening the tab or syncing costs
 *   nothing. Only [generate] reaches the service (FR-002, SC-004).
 * - **A document without usable text never reaches the service.** Asking about an empty context
 *   spends quota and gets invention back (FR-012, SC-005).
 * - [generate] is **idempotent while one is in flight**: two concurrent calls for the same key
 *   share a single request rather than spending the allowance twice (FR-005).
 * - A stored summary that no longer matches its document, model, prompt or schema is **stale, not
 *   absent**: it is still emitted, marked, and never discarded here (FR-035).
 */
interface AiSummaryRepository {

    fun observeSummary(externalKey: String): Flow<AiSummaryStatus>

    /**
     * @param force ignore what is stored and ask again. What the «regenerate» action does (FR-034).
     */
    suspend fun generate(publication: Publication, force: Boolean): AppResult<AiSummary>

    /** Whether this installation has already been told the text leaves the device (FR-043). */
    fun observeNoticeAccepted(): Flow<Boolean>

    suspend fun acceptNotice()
}
