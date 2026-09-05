package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AiConversation
import com.jrblanco.boccantabria.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.Flow

/** What has been said about this publication. Observing generates nothing. */
class ObserveAiConversationUseCase(
    private val repository: AiChatRepository,
) {
    operator fun invoke(externalKey: String): Flow<AiConversation> =
        repository.observeConversation(externalKey)
}
