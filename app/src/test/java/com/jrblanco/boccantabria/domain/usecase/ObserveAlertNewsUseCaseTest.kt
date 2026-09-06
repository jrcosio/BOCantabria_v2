package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.alertRule
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveAlertNewsUseCaseTest {

    @Test
    fun `one row per publication, naming every rule`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1", name = "A"), alertRule(id = "r2", name = "B")))
        repository.publications["boc:1"] = publication("boc:1")
        repository.seedMatch("r1", "boc:1")
        repository.seedMatch("r2", "boc:1")

        val news = ObserveAlertNewsUseCase(repository)().first()

        assertEquals(1, news.size)
        assertEquals(listOf("A", "B"), news.single().ruleNames)
    }
}
