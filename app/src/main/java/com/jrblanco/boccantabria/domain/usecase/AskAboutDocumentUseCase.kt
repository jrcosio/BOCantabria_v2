package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AiChatRepository

/**
 * Asks something about the official document of a publication.
 *
 * Not suspending, and it does not return the answer: the work runs on a scope of the repository's own
 * and the answer arrives through the conversation flow. Leaving the screen therefore does not cancel
 * what has already been paid for (011 research.md D-313).
 */
class AskAboutDocumentUseCase(
    private val repository: AiChatRepository,
) {
    operator fun invoke(publication: Publication, question: String) =
        repository.ask(publication, question)
}
