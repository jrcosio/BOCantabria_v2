package com.jrblanco.boccantabria.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * An instant said the way people say it: «hace 20 min», «ayer», or a date.
 *
 * Returns labels rather than strings: the words are interface copy and belong to `strings.xml`, and
 * a utility that reached for resources would need a `Context`. The zone is a parameter so the tests
 * can pin it (012 research.md D-432).
 */
object RelativeTime {

    sealed interface Label {
        data object JustNow : Label
        data class Minutes(val count: Int) : Label
        data class Hours(val count: Int) : Label
        data object Today : Label
        data object Yesterday : Label
        data class Day(val date: LocalDate) : Label
    }

    /**
     * The finest label that fits: minutes and hours within the same local day, «ayer», or the date.
     * An instant in the future —a clock that went backwards— is «ahora mismo».
     */
    fun label(instantMillis: Long, nowMillis: Long, zone: ZoneId): Label {
        val elapsed = nowMillis - instantMillis
        if (elapsed < MINUTE_MILLIS) return Label.JustNow
        if (elapsed < HOUR_MILLIS) return Label.Minutes((elapsed / MINUTE_MILLIS).toInt())
        return when (val day = dayOf(instantMillis, nowMillis, zone)) {
            Label.Today -> Label.Hours((elapsed / HOUR_MILLIS).toInt())
            else -> day
        }
    }

    /** Coarser: only «hoy», «ayer» or the date. What the day separators of a list use. */
    fun dayOf(instantMillis: Long, nowMillis: Long, zone: ZoneId): Label {
        val day = Instant.ofEpochMilli(instantMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(day, today)) {
            0L -> Label.Today
            1L -> Label.Yesterday
            else -> Label.Day(day)
        }
    }

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
}
