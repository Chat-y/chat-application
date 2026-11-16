package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.calls.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Typed UI-boundary fuzz fixtures, not JSON/provider integration coverage. */
class CallLogMalformedDisplayGapTest {
    private fun malformed(time: Instant) = CallRecord("", "", emptyList(), CallType.voice,
        CallDirection.missed, time, Long.MIN_VALUE, remoteDisplayName = " ", remoteUsername = "")
    @Test fun extremeInstantsProduceUnknownDatesWithoutInventingCurrentTime() {
        listOf(Instant.MIN, Instant.MAX).forEach { instant ->
            assertNull(callLogLocalDate(instant, ZoneId.of("UTC")))
            assertNull(callLogDateForDisplay(instant))
            assertEquals("Unknown", callLogDisplayName(null, malformed(instant), "Unknown"))
            assertEquals(instant, callLogDayGroups(listOf(malformed(instant)), ZoneId.of("UTC")).single().second.single().timestamp)
        }
    }
    @Test fun normalEpochDateSurvivesAndRandomBlankLabelsFallBack() {
        assertEquals(0L, callLogDateForDisplay(Instant.EPOCH)?.time)
        listOf("", " ", "\t", "\n").forEach { blank ->
            assertEquals("Unknown", callLogDisplayName(null,
                malformed(Instant.EPOCH).copy(remoteDisplayName = blank, remoteUsername = blank), "Unknown"))
        }
    }
}
