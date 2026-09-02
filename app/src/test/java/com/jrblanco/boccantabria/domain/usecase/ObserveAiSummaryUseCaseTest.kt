package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.fake.FakeAiSummaryRepository
import com.jrblanco.boccantabria.fake.aiSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveAiSummaryUseCaseTest {

    @Test
    fun `it hands through what the repository says`() = runTest {
        val repository = FakeAiSummaryRepository(
            AiSummaryStatus.Ready(aiSummary(), generatedAtEpochMillis = 1_000L, isStale = false),
        )

        val status = ObserveAiSummaryUseCase(repository)("boc:439765").first()

        assertEquals(
            "Se aprueba definitivamente la modificacion de la ordenanza.",
            (status as AiSummaryStatus.Ready).summary.plainLanguageSummary,
        )
    }

    /** FR-002 and SC-004: watching the tab must not cost a request. */
    @Test
    fun `observing never asks the service`() = runTest {
        val repository = FakeAiSummaryRepository()

        ObserveAiSummaryUseCase(repository)("boc:439765").first()

        assertEquals(0, repository.calls)
    }
}
