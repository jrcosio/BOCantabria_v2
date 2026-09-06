package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAlertRuleUseCaseTest {

    private val scheduler = FakeBackgroundSyncScheduler()

    @Test
    fun `deleting the only rule removes it, its matches, and cancels the check`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1")))
        repository.seedMatch("r1", "boc:1")

        DeleteAlertRuleUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))("r1")

        assertTrue(repository.storedRules.isEmpty())
        assertTrue(repository.storedMatches.isEmpty())
        assertEquals(listOf("cancel"), scheduler.calls)
    }

    @Test
    fun `deleting one of two enabled rules keeps the check scheduled`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1"), alertRule(id = "r2")))

        DeleteAlertRuleUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))("r1")

        assertEquals(listOf("ensureScheduled"), scheduler.calls)
    }
}
