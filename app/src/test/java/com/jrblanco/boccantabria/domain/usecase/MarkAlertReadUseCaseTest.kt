package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkAlertReadUseCaseTest {

    @Test
    fun `marks every match of the publication and lowers the count`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1"), alertRule(id = "r2")))
        repository.seedMatch("r1", "boc:1")
        repository.seedMatch("r2", "boc:1")

        MarkAlertReadUseCase(repository)("boc:1")

        assertEquals(0, repository.observeUnreadCount().first())
    }

    @Test
    fun `a publication without news is a success, not a failure`() = runTest {
        assertEquals(AppResult.Success(Unit), MarkAlertReadUseCase(FakeAlertRepository())("boc:9"))
    }
}
