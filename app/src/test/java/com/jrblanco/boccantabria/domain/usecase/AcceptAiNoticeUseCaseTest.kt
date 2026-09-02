package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAiSummaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptAiNoticeUseCaseTest {

    @Test
    fun `accepting is remembered`() = runTest {
        val repository = FakeAiSummaryRepository(noticeAccepted = false)

        AcceptAiNoticeUseCase(repository)()

        assertTrue(repository.observeNoticeAccepted().first())
        assertEquals(1, repository.accepted)
    }

    /** FR-045: shown once. Accepting again is harmless but must not be needed. */
    @Test
    fun `accepting twice leaves it accepted`() = runTest {
        val repository = FakeAiSummaryRepository(noticeAccepted = false)
        val accept = AcceptAiNoticeUseCase(repository)

        accept()
        accept()

        assertTrue(repository.observeNoticeAccepted().first())
    }
}
