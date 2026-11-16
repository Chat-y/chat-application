package com.example.chat.ui.chats.starred

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Display-only dates. Invalid upstream timestamps do not suppress the underlying starred message. */
internal object StarredDateRules {
    fun localDayOrNull(instant: Instant, zone: ZoneId): LocalDate? =
        try { instant.atZone(zone).toLocalDate() } catch (_: java.time.DateTimeException) { null }

    fun label(
        instant: Instant,
        zone: ZoneId,
        today: LocalDate,
        unavailable: String,
        formatTime: (Date) -> String,
        formatDate: (Date) -> String,
    ): String = try {
        val day = localDayOrNull(instant, zone)
        if (day == null) unavailable else {
            val value = Date.from(instant)
            if (day == today) formatTime(value) else formatDate(value)
        }
    } catch (_: Exception) { unavailable }
}
