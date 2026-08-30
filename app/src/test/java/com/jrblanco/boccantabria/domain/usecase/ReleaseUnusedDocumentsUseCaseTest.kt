package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseUnusedDocumentsUseCaseTest {

    private val documents = FakeDocumentRepository()

    @Test
    fun `it asks the repository to release what is no longer used`() = runTest {
        ReleaseUnusedDocumentsUseCase(documents)()

        assertEquals(1, documents.released)
    }
}
