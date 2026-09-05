package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.Flow

/**
 * Whether asking is possible at all in this build.
 *
 * Exists so the screen can say «not available» **when it opens** rather than after spending a request
 * to find out, which is what FR-036 asks for and what the summary does not do (011 research.md D-320b).
 */
class ObserveAiAvailabilityUseCase(
    private val repository: AiChatRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeAvailability()
}
