package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAiSummaryRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A pass-through, and the test says so: what matters is that the key arrives untouched and that the
 * call does not need a coroutine, because the only caller is `onCleared()`.
 */
class ReleaseAiDocumentSessionUseCaseTest {

    private val repository = FakeAiSummaryRepository()
    private val useCase = ReleaseAiDocumentSessionUseCase(repository)

    @Test
    fun `releases the session of the key it is given`() {
        useCase("boc:439765")

        assertEquals(listOf("boc:439765"), repository.releasedKeys)
    }

    @Test
    fun `releasing twice asks twice, because deciding is not this class's job`() {
        useCase("boc:1")
        useCase("boc:1")

        assertEquals(listOf("boc:1", "boc:1"), repository.releasedKeys)
    }
}
