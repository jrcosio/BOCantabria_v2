package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AiChatRepository

/** Sends the failed question again. Whoever asked already wrote it once (FR-033). */
class RetryLastQuestionUseCase(
    private val repository: AiChatRepository,
) {
    operator fun invoke(publication: Publication) = repository.retry(publication)
}
