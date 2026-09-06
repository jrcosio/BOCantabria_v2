package com.jrblanco.boccantabria.core.util

import java.time.Instant
import java.time.ZoneId

/**
 * Where "today" begins for the person holding the phone.
 *
 * «1 coincidencia hoy» has to be counted in the phone's day, not in UTC and not in the server's zone.
 * The zone is a parameter so a test can pin it (012 research.md D-432).
 */
object LocalDay {

    /** The first millisecond of the local day that contains [nowMillis]. */
    fun startOf(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
