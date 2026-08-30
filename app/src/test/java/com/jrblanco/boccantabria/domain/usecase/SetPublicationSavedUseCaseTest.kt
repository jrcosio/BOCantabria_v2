package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.FakeSavedPublicationRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetPublicationSavedUseCaseTest {

    @Test
    fun `saving reaches the repository with the key and the value`() = runTest {
        val repository = FakeSavedPublicationRepository()

        val result = SetPublicationSavedUseCase(repository)("boc:1", saved = true)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("boc:1" to true), repository.writes)
    }

    @Test
    fun `unsaving reaches the repository too`() = runTest {
        val repository = FakeSavedPublicationRepository(listOf(publication("boc:1")))

        SetPublicationSavedUseCase(repository)("boc:1", saved = false)

        assertEquals(listOf("boc:1" to false), repository.writes)
    }

    @Test
    fun `a failure travels through untouched`() = runTest {
        val repository = FakeSavedPublicationRepository().apply { failWrites = true }

        val result = SetPublicationSavedUseCase(repository)("boc:1", saved = true)

        assertEquals(AppResult.Failure(DomainError.Unknown), result)
    }
}
