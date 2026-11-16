package com.example.chat.ui.chats.home

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fixed clocks, zones and locales avoid depending on the test machine's defaults. */
class HomeFormattersBoundaryTest {
    private val utc = ZoneId.of("UTC")
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-11-16T12:05:00Z")

    @Test fun explicitClockChoiceControlsMidnightAndNoon() {
        val midnight = Instant.parse("2026-11-16T00:05:00Z")
        assertEquals("00:05", HomeFormatters.formatClockTime(midnight, utc, true, Locale.US))
        assertEquals("12:05 AM", HomeFormatters.formatClockTime(midnight, utc, false, Locale.US))
        assertEquals("12:05 PM", HomeFormatters.formatClockTime(now, utc, false, Locale.US))
        assertEquals("17:35", HomeFormatters.formatClockTime(now, kolkata, true, Locale.US))
    }

    @Test fun explicitClockChoiceWorksWithNonUsLocales() {
        assertEquals("1:05 PM", HomeFormatters.formatClockTime(
            Instant.parse("2026-11-16T13:05:00Z"), utc, false, Locale.UK,
        ))
        assertEquals("13:05", HomeFormatters.formatClockTime(
            Instant.parse("2026-11-16T13:05:00Z"), utc, true, Locale.FRANCE,
        ))
    }

    @Test fun conversationUsesLocalCalendarDayRatherThanElapsed24Hours() {
        val reference = Instant.parse("2026-11-15T18:45:00Z") // Nov 16 00:15 Kolkata
        val earlier = Instant.parse("2026-11-15T18:15:00Z") // Nov 15 23:45 Kolkata
        assertEquals("Yesterday", HomeFormatters.formatConversationTimestamp(
            earlier, reference, kolkata, true, Locale.US,
        ))
        assertEquals("18:15", HomeFormatters.formatConversationTimestamp(
            earlier, reference, utc, true, Locale.US,
        ))
    }

    @Test fun sixthPreviousDayUsesWeekdayAndSeventhUsesDate() {
        assertEquals("Tuesday", HomeFormatters.formatConversationTimestamp(
            Instant.parse("2026-11-10T12:05:00Z"), now, utc, false, Locale.US,
        ))
        assertEquals("09/11/2026", HomeFormatters.formatConversationTimestamp(
            Instant.parse("2026-11-09T12:05:00Z"), now, utc, false, Locale.US,
        ))
    }

    @Test fun sameDayConversationAndLastSeenPassThrough24HourChoice() {
        assertEquals("17:35", HomeFormatters.formatConversationTimestamp(
            now, now, kolkata, true, Locale.US,
        ))
        assertEquals("last seen today at 17:35", HomeFormatters.formatLastSeen(
            now, now, kolkata, true, Locale.US,
        ))
        assertEquals("last seen yesterday at 17:35", HomeFormatters.formatLastSeen(
            now.minusSeconds(86400), now, kolkata, true, Locale.US,
        ))
        assertEquals("last seen 09/11/2026 at 17:35", HomeFormatters.formatLastSeen(
            now.minusSeconds(7 * 86400L), now, kolkata, true, Locale.US,
        ))
    }

    @Test fun archiveUsesLocalDayAndUnpaddedOlderDate() {
        val reference = Instant.parse("2026-11-15T18:45:00Z")
        assertEquals("00:10", HomeFormatters.formatArchivedTime(
            Instant.parse("2026-11-15T18:40:00Z"), reference, kolkata,
        ))
        assertEquals("Yesterday", HomeFormatters.formatArchivedTime(
            Instant.parse("2026-11-15T18:15:00Z"), reference, kolkata,
        ))
        assertEquals("9/11", HomeFormatters.formatArchivedTime(
            Instant.parse("2026-11-09T12:05:00Z"), reference, kolkata,
        ))
    }
}
