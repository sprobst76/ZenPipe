package org.schabi.newpipe.zen

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Calendar helpers: the "Zen day" changes at 04:00 local time, editions change every hour. */
object ZenDay {
    const val ROLLOVER_HOUR = 4

    /** The Zen day a moment belongs to (before 04:00 it is still the previous calendar day). */
    @JvmStatic
    fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        .minusHours(ROLLOVER_HOUR.toLong()).toLocalDate()

    /** Start (millis) of the full hour containing [millis]. */
    @JvmStatic
    fun hourStart(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        .withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

    /** Start of the next full hour after [millis]. */
    @JvmStatic
    fun nextHourStart(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long = ZonedDateTime.ofInstant(Instant.ofEpochMilli(hourStart(millis, zone)), zone)
        .plusHours(1).toInstant().toEpochMilli()

    /** Start of the next Zen day (04:00 local) after [millis]. */
    @JvmStatic
    fun nextDayStart(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long = dayOf(millis, zone).plusDays(1).atStartOfDay(zone).plusHours(ROLLOVER_HOUR.toLong())
        .toInstant().toEpochMilli()
}
