package com.jrblanco.boccantabria.core.util

import com.jrblanco.boccantabria.core.util.RelativeTime.Label
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RelativeTimeTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = at(LocalDate.of(2026, 9, 6), LocalTime.of(10, 0))

    private fun at(date: LocalDate, time: LocalTime) = date.atTime(time).atZone(madrid).toInstant().toEpochMilli()

    @Test
    fun `under a minute is just now`() {
        assertEquals(Label.JustNow, RelativeTime.label(now - 30_000, now, madrid))
    }

    @Test
    fun `under an hour is minutes`() {
        assertEquals(Label.Minutes(20), RelativeTime.label(now - 20 * 60_000, now, madrid))
    }

    @Test
    fun `earlier the same day is hours`() {
        assertEquals(Label.Hours(2), RelativeTime.label(at(LocalDate.of(2026, 9, 6), LocalTime.of(8, 0)), now, madrid))
    }

    @Test
    fun `the day before is yesterday, even if fewer than 24 hours ago`() {
        assertEquals(Label.Yesterday, RelativeTime.label(at(LocalDate.of(2026, 9, 5), LocalTime.of(23, 0)), now, madrid))
    }

    @Test
    fun `further back is the date`() {
        assertEquals(
            Label.Day(LocalDate.of(2026, 9, 1)),
            RelativeTime.label(at(LocalDate.of(2026, 9, 1), LocalTime.of(9, 0)), now, madrid),
        )
    }

    /** A clock that went backwards must not produce «hace -3 min». */
    @Test
    fun `an instant in the future is just now`() {
        assertEquals(Label.JustNow, RelativeTime.label(now + 60_000, now, madrid))
    }

    @Test
    fun `the day label is today, yesterday or the date`() {
        assertEquals(Label.Today, RelativeTime.dayOf(now - 60_000, now, madrid))
        assertEquals(Label.Yesterday, RelativeTime.dayOf(at(LocalDate.of(2026, 9, 5), LocalTime.of(1, 0)), now, madrid))
        assertEquals(Label.Day(LocalDate.of(2026, 8, 30)), RelativeTime.dayOf(at(LocalDate.of(2026, 8, 30), LocalTime.NOON), now, madrid))
    }
}
