package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AiSummary
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository

/**
 * Makes the summary of a publication, which is the only thing that reaches the service.
 *
 * Called on «generate» and on «regenerate». Asking twice at once is safe: the repository shares a
 * single request rather than spending the allowance twice (FR-005).
 */
class GenerateAiSummaryUseCase(
    private val repository: AiSummaryRepository,
) {
    suspend operator fun invoke(
        publication: Publication,
        force: Boolean = false,
    ): AppResult<AiSummary> = repository.generate(publication, force)
}
