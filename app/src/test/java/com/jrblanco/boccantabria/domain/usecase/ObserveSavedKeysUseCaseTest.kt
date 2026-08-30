package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveSavedKeysUseCaseTest {

    @Test
    fun `emits the keys of everything saved`() = runTest {
        val repository = FakeSavedPublicationRepository(
            listOf(publication("boc:1"), publication("boc:2")),
        )

        ObserveSavedKeysUseCase(repository)().test {
            assertEquals(setOf("boc:1", "boc:2"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing saved is an empty set`() = runTest {
        ObserveSavedKeysUseCase(FakeSavedPublicationRepository())().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it keeps emitting, so a card redraws without anybody reloading anything`() = runTest {
        val repository = FakeSavedPublicationRepository()

        ObserveSavedKeysUseCase(repository)().test {
            assertTrue(awaitItem().isEmpty())
            repository.emit(listOf(publication("boc:1")))
            assertEquals(setOf("boc:1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
