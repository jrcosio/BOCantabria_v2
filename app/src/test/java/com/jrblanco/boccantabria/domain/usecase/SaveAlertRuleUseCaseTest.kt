package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AlertRuleDraft
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveAlertRuleUseCaseTest {

    private val scheduler = FakeBackgroundSyncScheduler()
    private val draft = AlertRuleDraft(name = "Ganadería", keywords = listOf("ganadería"))

    @Test
    fun `saving the first enabled rule schedules the periodic check`() = runTest {
        val repository = FakeAlertRepository()

        val result = SaveAlertRuleUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))(draft, id = null)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("ensureScheduled"), scheduler.calls)
        assertEquals(1, repository.storedRules.size)
    }

    @Test
    fun `saving a paused rule as the only one cancels it`() = runTest {
        val repository = FakeAlertRepository()

        SaveAlertRuleUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))(draft.copy(isEnabled = false), id = null)

        assertEquals(listOf("cancel"), scheduler.calls)
    }

    @Test
    fun `editing keeps the id and renews active since`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1", createdAt = 1L))).apply { now = 9L }

        val result = SaveAlertRuleUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))(draft, id = "r1")

        assertEquals(AppResult.Success("r1"), result)
        val stored = repository.storedRules.single()
        assertEquals(1L, stored.createdAt)
        assertEquals(9L, stored.activeSince)
    }

    @Test
    fun `a failed write does not touch the scheduler`() = runTest {
        val repository = FakeAlertRepository().apply { failWrites = true }

        val result = SaveAlertRuleUseCase(repository, ReconcileBackgroundSyncUseCase(repository, scheduler))(draft, id = null)

        assertTrue(result is AppResult.Failure)
        assertTrue(scheduler.calls.isEmpty())
    }
}
