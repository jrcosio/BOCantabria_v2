package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.AiChatMessage
import com.jrblanco.boccantabria.domain.model.AiConversation
import com.jrblanco.boccantabria.fake.FakeAiChatRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveAiConversationUseCaseTest {

    private val repository = FakeAiChatRepository()
    private val useCase = ObserveAiConversationUseCase(repository)

    @Test
    fun `hands through what the repository says`() = runTest {
        useCase("boc:1").test {
            assertTrue(awaitItem().isEmpty)

            repository.emit(
                AiConversation(
                    externalKey = "boc:1",
                    messages = listOf(
                        AiChatMessage.Question(id = "q1", atEpochMillis = 0, text = "¿Y el plazo?"),
                    ),
                ),
            )

            assertEquals(1, awaitItem().messages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
