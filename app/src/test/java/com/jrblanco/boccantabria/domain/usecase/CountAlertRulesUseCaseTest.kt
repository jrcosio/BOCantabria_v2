package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CountAlertRulesUseCaseTest {

    @Test
    fun `counts every rule, paused ones included`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1"), alertRule(id = "r2", isEnabled = false)))

        assertEquals(2, CountAlertRulesUseCase(repository)())
        assertEquals(0, CountAlertRulesUseCase(FakeAlertRepository())())
    }
}
