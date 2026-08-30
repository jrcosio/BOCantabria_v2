package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenOfficialDocumentUseCaseTest {

    @Test
    fun `it returns the local copy`() = runTest {
        val result = OpenOfficialDocumentUseCase(FakeDocumentRepository())(publication())

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `a failure is passed through untouched`() = runTest {
        val repository = FakeDocumentRepository().apply {
            result = AppResult.Failure(DomainError.Network)
        }

        assertEquals(
            AppResult.Failure(DomainError.Network),
            OpenOfficialDocumentUseCase(repository)(publication()),
        )
    }

    @Test
    fun `asking twice is safe, because the repository shares one download`() = runTest {
        val repository = FakeDocumentRepository()
        val useCase = OpenOfficialDocumentUseCase(repository)

        useCase(publication())
        useCase(publication())

        assertEquals(2, repository.calls)
    }
}
