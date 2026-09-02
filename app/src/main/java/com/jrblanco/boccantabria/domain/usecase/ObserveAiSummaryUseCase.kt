package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository
import kotlinx.coroutines.flow.Flow

/**
 * How far along the summary of a publication is, including one already stored.
 *
 * Observing costs nothing: it never reaches the service (FR-002).
 */
class ObserveAiSummaryUseCase(
    private val repository: AiSummaryRepository,
) {
    operator fun invoke(externalKey: String): Flow<AiSummaryStatus> =
        repository.observeSummary(externalKey)
}
