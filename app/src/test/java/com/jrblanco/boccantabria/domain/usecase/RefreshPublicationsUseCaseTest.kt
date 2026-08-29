package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.SyncSummary
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the difference between "the application opened" and "the person asked" lives.
 */
class RefreshPublicationsUseCaseTest {

    @Test
    fun `opening the screen with a fresh cache does not touch the network`() = runTest {
        val repository = FakePublicationRepository().apply { stale = false }

        val result = RefreshPublicationsUseCase(repository)(force = false)

        assertEquals(AppResult.Success(SyncSummary.SKIPPED), result)
        assertEquals(0, repository.refreshCount)
    }

    @Test
    fun `opening the screen with a stale cache synchronises`() = runTest {
        val repository = FakePublicationRepository().apply { stale = true }

        RefreshPublicationsUseCase(repository)(force = false)

        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `the refresh gesture always reaches the network, however fresh the cache is`() = runTest {
        val repository = FakePublicationRepository().apply { stale = false }

        RefreshPublicationsUseCase(repository)(force = true)

        assertEquals(1, repository.refreshCount)
        assertEquals(0, repository.staleChecks)
    }

    @Test
    fun `the summary of the synchronisation is passed through`() = runTest {
        val summary = SyncSummary(succeededFeeds = 17, failedFeeds = 2, insertedItems = 40)
        val repository = FakePublicationRepository().apply {
            refreshResult = AppResult.Success(summary)
        }

        assertEquals(AppResult.Success(summary), RefreshPublicationsUseCase(repository)(force = true))
    }

    @Test
    fun `a failure is passed through untouched`() = runTest {
        val repository = FakePublicationRepository().apply {
            refreshResult = AppResult.Failure(DomainError.Network)
        }

        assertEquals(
            AppResult.Failure(DomainError.Network),
            RefreshPublicationsUseCase(repository)(force = true),
        )
    }
}
