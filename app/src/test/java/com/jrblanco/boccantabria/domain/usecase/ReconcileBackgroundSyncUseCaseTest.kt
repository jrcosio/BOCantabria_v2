package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.FakeBackgroundSyncScheduler
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcileBackgroundSyncUseCaseTest {

    private val scheduler = FakeBackgroundSyncScheduler()

    @Test
    fun `with an enabled rule the check is scheduled`() = runTest {
        ReconcileBackgroundSyncUseCase(FakeAlertRepository(listOf(alertRule())), scheduler)()

        assertEquals(listOf("ensureScheduled"), scheduler.calls)
    }

    @Test
    fun `with only paused rules the check is cancelled`() = runTest {
        ReconcileBackgroundSyncUseCase(FakeAlertRepository(listOf(alertRule(isEnabled = false))), scheduler)()

        assertEquals(listOf("cancel"), scheduler.calls)
    }

    @Test
    fun `with no rules the check is cancelled`() = runTest {
        ReconcileBackgroundSyncUseCase(FakeAlertRepository(), scheduler)()

        assertEquals(listOf("cancel"), scheduler.calls)
    }
}
