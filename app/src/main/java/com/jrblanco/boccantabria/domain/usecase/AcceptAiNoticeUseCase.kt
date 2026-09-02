package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository

/**
 * Remembers that the notice was accepted, so it is shown once and never again (FR-045).
 *
 * Cancelling is not an operation: nothing is remembered and nothing is sent (FR-044).
 */
class AcceptAiNoticeUseCase(
    private val repository: AiSummaryRepository,
) {
    suspend operator fun invoke() = repository.acceptNotice()
}
