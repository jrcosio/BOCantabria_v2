package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AiChatRepository

/**
 * Throws the conversation away when the publication is left.
 *
 * Not suspending for the same reason as `ReleaseAiDocumentSessionUseCase`, and called from the same
 * place: by the time the detail screen's `onCleared()` runs, its `viewModelScope` is already cancelled
 * and anything launched there would never run. The repository keeps a scope of its own
 * (011 research.md D-314).
 */
class DiscardAiConversationUseCase(
    private val repository: AiChatRepository,
) {
    operator fun invoke(externalKey: String) = repository.discard(externalKey)
}
