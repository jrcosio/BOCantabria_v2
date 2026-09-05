package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAiChatRepository
import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryLastQuestionUseCaseTest {

    private val repository = FakeAiChatRepository()
    private val useCase = RetryLastQuestionUseCase(repository)

    @Test
    fun `retries for the publication it is given`() {
        useCase(publication(key = "boc:1"))

        assertEquals(listOf("boc:1"), repository.retries)
    }

    @Test
    fun `retrying twice asks twice, because deciding is not this class's job`() {
        useCase(publication(key = "boc:1"))
        useCase(publication(key = "boc:1"))

        assertEquals(listOf("boc:1", "boc:1"), repository.retries)
    }
}
