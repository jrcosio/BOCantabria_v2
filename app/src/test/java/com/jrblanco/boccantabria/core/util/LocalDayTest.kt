package com.jrblanco.boccantabria.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LocalDayTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `the start of the day is local midnight, not UTC`() {
        val now = LocalDate.of(2026, 9, 6).atTime(LocalTime.of(10, 30)).atZone(madrid).toInstant().toEpochMilli()

        val start = LocalDay.startOf(now, madrid)

        assertEquals(
            LocalDate.of(2026, 9, 6).atStartOfDay(madrid).toInstant().toEpochMilli(),
            start,
        )
    }

    /** At 00:30 in Madrid it is still yesterday in UTC; the day has to be the person's. */
    @Test
    fun `just after local midnight the day has already changed`() {
        val now = LocalDate.of(2026, 9, 6).atTime(LocalTime.of(0, 30)).atZone(madrid).toInstant().toEpochMilli()

        assertEquals(LocalDate.of(2026, 9, 6).atStartOfDay(madrid).toInstant().toEpochMilli(), LocalDay.startOf(now, madrid))
    }
}
