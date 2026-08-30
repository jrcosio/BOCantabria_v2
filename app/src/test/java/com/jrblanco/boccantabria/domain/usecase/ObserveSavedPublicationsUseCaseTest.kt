package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveSavedPublicationsUseCaseTest {

    @Test
    fun `emits what is saved`() = runTest {
        val repository = FakeSavedPublicationRepository(
            listOf(publication("boc:1"), publication("boc:2")),
        )

        ObserveSavedPublicationsUseCase(repository)().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing saved is an empty list, not an error`() = runTest {
        ObserveSavedPublicationsUseCase(FakeSavedPublicationRepository())().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it keeps emitting as the person saves and unsaves`() = runTest {
        val repository = FakeSavedPublicationRepository()

        ObserveSavedPublicationsUseCase(repository)().test {
            assertTrue(awaitItem().isEmpty())
            repository.emit(listOf(publication("boc:1")))
            assertEquals(1, awaitItem().size)
            repository.emit(emptyList())
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The order comes from the store. A use case that sorted would be a second place deciding it. */
    @Test
    fun `the order arrives untouched`() = runTest {
        val repository = FakeSavedPublicationRepository(
            listOf(publication("boc:3"), publication("boc:1"), publication("boc:2")),
        )

        ObserveSavedPublicationsUseCase(repository)().test {
            assertEquals(
                listOf("boc:3", "boc:1", "boc:2"),
                awaitItem().map { it.externalKey },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
