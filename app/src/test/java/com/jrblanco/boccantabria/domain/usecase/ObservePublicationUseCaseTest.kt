package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.fake.FakePublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservePublicationUseCaseTest {

    @Test
    fun `emits the publication that matches the key`() = runTest {
        val repository = FakePublicationRepository(listOf(publication("boc:1"), publication("boc:2")))

        ObservePublicationUseCase(repository)("boc:2").test {
            assertEquals("boc:2", awaitItem()?.externalKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a publication that is no longer stored emits null, not a failure`() = runTest {
        val repository = FakePublicationRepository(listOf(publication("boc:1")))

        ObservePublicationUseCase(repository)("boc:retirada").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a later correction reaches an open screen`() = runTest {
        val repository = FakePublicationRepository(listOf(publication("boc:1", title = "Original")))

        ObservePublicationUseCase(repository)("boc:1").test {
            assertEquals("Original", awaitItem()?.title)
            repository.emit(listOf(publication("boc:1", title = "Corregido")))
            assertEquals("Corregido", awaitItem()?.title)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
