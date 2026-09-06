package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetAlertRuleEnabledUseCaseTest {

    private val scheduler = FakeBackgroundSyncScheduler()

    @Test
    fun `pausing the last enabled rule cancels the check`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1")))

        SetAlertRuleEnabledUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))("r1", enabled = false)

        assertEquals(listOf("cancel"), scheduler.calls)
        assertTrue(repository.storedRules.none { it.isEnabled })
    }

    @Test
    fun `re-enabling schedules it and renews active since`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1", isEnabled = false, activeSince = 1L))).apply { now = 7L }

        SetAlertRuleEnabledUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))("r1", enabled = true)

        assertEquals(listOf("ensureScheduled"), scheduler.calls)
        assertEquals(7L, repository.storedRules.single().activeSince)
    }

    @Test
    fun `a failed write does not touch the scheduler`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1"))).apply { failWrites = true }

        SetAlertRuleEnabledUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))("r1", enabled = false)

        assertTrue(scheduler.calls.isEmpty())
    }
}
