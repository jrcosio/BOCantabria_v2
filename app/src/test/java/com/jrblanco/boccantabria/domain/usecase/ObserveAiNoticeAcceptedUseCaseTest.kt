package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAiSummaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveAiNoticeAcceptedUseCaseTest {

    @Test
    fun `a fresh installation has not been told yet`() = runTest {
        val repository = FakeAiSummaryRepository(noticeAccepted = false)

        assertFalse(ObserveAiNoticeAcceptedUseCase(repository)().first())
    }

    @Test
    fun `once told, it stays told`() = runTest {
        val repository = FakeAiSummaryRepository(noticeAccepted = true)

        assertTrue(ObserveAiNoticeAcceptedUseCase(repository)().first())
    }
}
