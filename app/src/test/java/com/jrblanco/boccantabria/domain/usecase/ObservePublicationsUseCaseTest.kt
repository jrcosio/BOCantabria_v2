package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservePublicationsUseCaseTest {

    @Test
    fun `emits what is stored for the selection`() = runTest {
        val repository = FakePublicationRepository(listOf(publication("boc:1"), publication("boc:2")))

        ObservePublicationsUseCase(repository)(HomeSelection.TodaysBulletin).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty bulletin is an empty list, not an error`() = runTest {
        val repository = FakePublicationRepository()

        ObservePublicationsUseCase(repository)(HomeSelection.TodaysBulletin).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it keeps emitting as sources land`() = runTest {
        val repository = FakePublicationRepository()

        ObservePublicationsUseCase(repository)(HomeSelection.TodaysBulletin).test {
            assertTrue(awaitItem().isEmpty())
            repository.emit(listOf(publication("boc:1")))
            assertEquals(1, awaitItem().size)
            repository.emit(listOf(publication("boc:1"), publication("boc:2")))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the selection reaches the repository, so the query changes with it`() = runTest {
        val repository = FakePublicationRepository()
        val selection = HomeSelection.Section("2", "2.2")

        ObservePublicationsUseCase(repository)(selection).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(selection), repository.observedSelections)
    }
}
