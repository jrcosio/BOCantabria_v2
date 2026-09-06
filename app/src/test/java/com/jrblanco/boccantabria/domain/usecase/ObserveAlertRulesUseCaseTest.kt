package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.fake.FakeAlertRepository
import com.jrblanco.boccantabria.fake.alertRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ObserveAlertRulesUseCaseTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val tenInTheMorning = LocalDate.of(2026, 9, 6).atTime(LocalTime.of(10, 0)).atZone(madrid).toInstant().toEpochMilli()
    private val midnight = LocalDate.of(2026, 9, 6).atStartOfDay(madrid).toInstant().toEpochMilli()

    @Test
    fun `the repository is asked from the start of the local day`() = runTest {
        val repository = FakeAlertRepository()

        ObserveAlertRulesUseCase(repository, object : TimeProvider { override fun nowMillis() = tenInTheMorning }, madrid)()

        assertTrue(repository.calls.contains("observeRules($midnight)"))
    }

    @Test
    fun `a match from yesterday does not count as today`() = runTest {
        val repository = FakeAlertRepository(listOf(alertRule(id = "r1")))
        repository.seedMatch("r1", "boc:1", matchedAt = midnight - 1)
        repository.seedMatch("r1", "boc:2", matchedAt = midnight + 1)

        val overview = ObserveAlertRulesUseCase(repository, object : TimeProvider { override fun nowMillis() = tenInTheMorning }, madrid)()
            .first().single()

        assertEquals(1, overview.matchesToday)
        assertEquals(java.lang.Long.valueOf(midnight + 1), overview.lastMatchedAt)
    }
}
