package com.jrblanco.boccantabria.domain.usecase

import app.cash.turbine.test
import com.jrblanco.boccantabria.domain.model.DocumentStatus
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.officialDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveOfficialDocumentUseCaseTest {

    @Test
    fun `an unfetched document is absent`() = runTest {
        ObserveOfficialDocumentUseCase(FakeDocumentRepository())("boc:1").test {
            assertEquals(DocumentStatus.Absent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it follows the document from downloading to available`() = runTest {
        val repository = FakeDocumentRepository()

        ObserveOfficialDocumentUseCase(repository)("boc:1").test {
            assertEquals(DocumentStatus.Absent, awaitItem())
            repository.emit(DocumentStatus.Downloading(bytesRead = 10, totalBytes = 100))
            assertTrue(awaitItem() is DocumentStatus.Downloading)
            repository.emit(DocumentStatus.Available(officialDocument()))
            assertTrue(awaitItem() is DocumentStatus.Available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure arrives as a state, not as a broken flow`() = runTest {
        val repository = FakeDocumentRepository()

        ObserveOfficialDocumentUseCase(repository)("boc:1").test {
            awaitItem()
            repository.emit(DocumentStatus.Failed(DomainError.Network))
            assertEquals(DocumentStatus.Failed(DomainError.Network), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
