package com.example.chat.ui.chats.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Exact port of lib/ui/core/formatting/chat_formatters.dart. One
 *  implementation serves the chat list, archived list and presence surfaces
 *  so date rules never diverge between composables. Timestamps convert to
 *  LOCAL time only at this display boundary. */
object HomeFormatters {

    private val weekdays = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    )

    private fun two(v: Int): String = v.toString().padStart(2, '0')

    /** English keeps the exact hardcoded en-US output above; any other
     *  language uses the platform's localized formatters (R1-44). */
    private fun isEnglish(locale: Locale): Boolean = locale.language == "en"

    /** Locale-aware clock time ("9:52 AM" or "21:52"), always en-US labels
     *  today, matching the Dart convention. */
    fun formatClockTime(
        value: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        is24Hour: Boolean? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        val local = value.atZone(zone)
        // R1-44: honour the user's 12/24-hour setting when the caller passes it.
        if (is24Hour == true) return "${two(local.hour)}:${two(local.minute)}"
        if (is24Hour == false && !isEnglish(locale)) {
            // R1-44: an explicit 12-hour choice wins over a 24-hour locale default.
            return DateTimeFormatter.ofPattern("h:mm a", locale).format(local)
        }
        if (!isEnglish(locale)) {
            return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(local)
        }
        val hour12 = if (local.hour % 12 == 0) 12 else local.hour % 12
        val amPm = if (local.hour < 12) "AM" else "PM"
        return "$hour12:${two(local.minute)} $amPm"
    }

    private fun sameCalendarDay(a: LocalDate, b: LocalDate): Boolean = a == b

    /** Chat-list / metadata timestamp:
     *  today -> "9:52 AM" · yesterday -> "Yesterday"
     *  within the last 7 days -> "Wednesday" · older -> "19/08/2026". */
    fun formatConversationTimestamp(
        timestamp: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        is24Hour: Boolean? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        val local = timestamp.atZone(zone).toLocalDate()
        val ref = now.atZone(zone).toLocalDate()
        if (sameCalendarDay(local, ref)) return formatClockTime(timestamp, zone, is24Hour, locale)
        if (sameCalendarDay(local, ref.minusDays(1))) return "Yesterday"
        val diffDays = java.time.temporal.ChronoUnit.DAYS.between(local, ref)
        if (diffDays in 1..6) {
            // LocalDate.dayOfWeek: Mon=1 .. Sun=7.
            if (!isEnglish(locale)) {
                return timestamp.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            }
            return weekdays[timestamp.atZone(zone).dayOfWeek.value - 1]
        }
        val z = timestamp.atZone(zone)
        if (!isEnglish(locale)) {
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(z)
        }
        return "${two(z.dayOfMonth)}/${two(z.monthValue)}/${z.year}"
    }

    /** Presence line for a contact's last-seen instant. Null input yields an
     *  empty string so privacy rules stay in the caller's control. */
    fun formatLastSeen(
        lastSeen: Instant?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        is24Hour: Boolean? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (lastSeen == null) return ""
        val local = lastSeen.atZone(zone).toLocalDate()
        val ref = now.atZone(zone).toLocalDate()
        if (sameCalendarDay(local, ref)) {
            return "last seen today at ${formatClockTime(lastSeen, zone, is24Hour, locale)}"
        }
        if (sameCalendarDay(local, ref.minusDays(1))) {
            return "last seen yesterday at ${formatClockTime(lastSeen, zone, is24Hour, locale)}"
        }
        val z = lastSeen.atZone(zone)
        // R1-44: match formatConversationTimestamp - older dates follow the
        // locale for non-English; English keeps the exact hardcoded output.
        val dateText = if (!isEnglish(locale)) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(z)
        } else {
            "${two(z.dayOfMonth)}/${two(z.monthValue)}/${z.year}"
        }
        return "last seen $dateText at ${formatClockTime(lastSeen, zone, is24Hour, locale)}"
    }

    /** Elapsed relative time for conversation entries when the `elapsedTime`
     *  preference is enabled ("just now", "5m ago", "2h ago", ...). */
    fun formatElapsedTime(
        timestamp: Instant,
        now: Instant = Instant.now(),
    ): String {
        val diff = java.time.Duration.between(timestamp, now)
        if (diff.isNegative || diff.seconds < 45) return "just now"
        if (diff.toMinutes() < 60) return "${diff.toMinutes()}m ago"
        if (diff.toHours() < 24) return "${diff.toHours()}h ago"
        val days = diff.toDays()
        if (days < 7) return "${days}d ago"
        if (days < 30) return "${days / 7}w ago"
        if (days < 365) return "${days / 30}mo ago"
        return "${days / 365}y ago"
    }

    /** The archived screen's own simpler formatter
     *  (chats_home_screen.dart _ArchivedChatsScreenState._formatMessageTime):
     *  today -> "HH:mm" (24h padded) · yesterday -> "Yesterday" · else "d/M". */
    fun formatArchivedTime(
        value: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val local: ZonedDateTime = value.atZone(zone)
        val ref = now.atZone(zone).toLocalDate()
        if (local.toLocalDate() == ref) {
            return "${two(local.hour)}:${two(local.minute)}"
        }
        if (local.toLocalDate() == ref.minusDays(1)) return "Yesterday"
        return "${local.dayOfMonth}/${local.monthValue}"
    }
}
