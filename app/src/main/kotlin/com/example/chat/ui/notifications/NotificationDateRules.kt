package com.example.chat.ui.notifications

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Calendar boundaries use the device zone, not elapsed 24-hour durations (DST can shorten a day). */
internal object NotificationDateRules {
    enum class RelativeDay { TODAY, YESTERDAY, ABSOLUTE }

    fun localDay(timestamp: Instant, zone: ZoneId): LocalDate = timestamp.atZone(zone).toLocalDate()

    /** A corrupt timestamp must not stop unrelated activity from rendering. */
    fun localDayOrNull(timestamp: Instant, zone: ZoneId): LocalDate? =
        try { localDay(timestamp, zone) } catch (_: java.time.DateTimeException) { null }

    fun relativeDay(day: LocalDate, clock: Clock): RelativeDay {
        val today = LocalDate.now(clock)
        return when (day) {
            today -> RelativeDay.TODAY
            today.minusDays(1) -> RelativeDay.YESTERDAY
            else -> RelativeDay.ABSOLUTE
        }
    }
}
