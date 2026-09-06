package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkAllAlertsReadUseCaseTest {

    @Test
    fun `everything becomes read`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1")))
        repository.seedMatch("r1", "boc:1")
        repository.seedMatch("r1", "boc:2")

        MarkAllAlertsReadUseCase(repository)()

        assertEquals(0, repository.observeUnreadCount().first())
        assertEquals(listOf("markAllRead"), repository.calls.filter { it == "markAllRead" })
    }
}
