package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAiChatRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Like `ReleaseAiDocumentSessionUseCase`, and called from the same place: the detail screen's
 * `onCleared()`, where the caller's scope is already dead. Hence no coroutine here.
 */
class DiscardAiConversationUseCaseTest {

    private val repository = FakeAiChatRepository()
    private val useCase = DiscardAiConversationUseCase(repository)

    @Test
    fun `discards the conversation of the key it is given`() {
        useCase("boc:439765")

        assertEquals(listOf("boc:439765"), repository.discarded)
    }
}
