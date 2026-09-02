package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.fake.FakeAiSummaryRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateAiSummaryUseCaseTest {

    @Test
    fun `it asks the repository and hands back the summary`() = runTest {
        val repository = FakeAiSummaryRepository()

        val result = GenerateAiSummaryUseCase(repository)(publication("boc:439765"))

        assertTrue(result is AppResult.Success)
        assertEquals(1, repository.calls)
    }

    /** Generating is the default; forcing is what «regenerate» does (FR-034). */
    @Test
    fun `it does not force unless asked to`() = runTest {
        val repository = FakeAiSummaryRepository()

        GenerateAiSummaryUseCase(repository)(publication("boc:439765"))

        assertEquals(0, repository.forcedCalls)
    }

    @Test
    fun `regenerating forces a new request`() = runTest {
        val repository = FakeAiSummaryRepository()

        GenerateAiSummaryUseCase(repository)(publication("boc:439765"), force = true)

        assertEquals(1, repository.forcedCalls)
    }
}
