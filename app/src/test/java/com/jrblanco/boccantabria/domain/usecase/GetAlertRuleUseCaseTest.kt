package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetAlertRuleUseCaseTest {

    @Test
    fun `returns the rule, or null when it is gone`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1")))

        assertEquals("r1", GetAlertRuleUseCase(repository)("r1")?.id)
        assertNull(GetAlertRuleUseCase(repository)("missing"))
    }
}
