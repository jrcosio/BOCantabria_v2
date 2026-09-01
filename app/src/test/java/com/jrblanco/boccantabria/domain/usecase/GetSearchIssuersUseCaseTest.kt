package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeSearchRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSearchIssuersUseCaseTest {

    private val repository = FakeSearchRepository()
    private val useCase = GetSearchIssuersUseCase(repository)

    @Test
    fun `it hands over what the store holds`() = runTest {
        repository.emitIssuers(listOf("Ayuntamiento de Piélagos", "Gobierno de Cantabria"))

        assertEquals(
            listOf("Ayuntamiento de Piélagos", "Gobierno de Cantabria"),
            useCase().first(),
        )
    }

    @Test
    fun `nothing stored is an empty list, not a failure`() = runTest {
        assertEquals(emptyList<String>(), useCase().first())
    }
}
