package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveUnreadAlertCountUseCaseTest {

    /** FR-003: publications, not matches. */
    @Test
    fun `a publication matching two rules counts once`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1"), alertRule(id = "r2")))
        repository.seedMatch("r1", "boc:1")
        repository.seedMatch("r2", "boc:1")
        repository.seedMatch("r1", "boc:2", read = true)

        assertEquals(1, ObserveUnreadAlertCountUseCase(repository)().first())
    }
}
