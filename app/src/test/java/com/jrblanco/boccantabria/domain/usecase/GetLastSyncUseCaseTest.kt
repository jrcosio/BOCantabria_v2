package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakePublicationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetLastSyncUseCaseTest {

    @Test
    fun `null until a source has answered, then the instant`() = runTest {
        val repository = FakePublicationRepository()

        assertNull(GetLastSyncUseCase(repository)())
        repository.lastSuccessAt = 4_000L
        assertEquals(java.lang.Long.valueOf(4_000L), GetLastSyncUseCase(repository)())
    }
}
