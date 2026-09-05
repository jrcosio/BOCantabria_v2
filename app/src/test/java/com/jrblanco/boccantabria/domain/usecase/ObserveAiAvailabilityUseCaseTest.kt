package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.fake.FakeAiChatRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seam that lets the screen say «not available» **when it opens** instead of spending a request to
 * find out, which is what the summary has to do (011 research.md D-320b).
 */
class ObserveAiAvailabilityUseCaseTest {

    private val repository = FakeAiChatRepository(available = false)
    private val useCase = ObserveAiAvailabilityUseCase(repository)

    @Test
    fun `says no when there is no credential, and yes when there is`() = runTest {
        useCase().test {
            assertFalse(awaitItem())

            repository.setAvailable(true)

            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
