package com.example.chat

import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.domain.models.CallType
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.calls.callLogDisplayName
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Pure presentation fixtures; does not verify real-time repository freshness. */
class CallLogDisplayNameGapTest {
    private val record = CallRecord("call", "self", listOf("self", "remote"), CallType.voice,
        CallDirection.outgoing, Instant.EPOCH, 0, remoteDisplayName = "Historical label")
    private fun person(name: String, username: String = "current-user") = UserProfile(
        "remote", name, username, "R", "", "", lastSeenAt = Instant.EPOCH)
    @Test fun updatedContactNameWinsOverRecordedName() {
        assertEquals("Renamed", callLogDisplayName(person(" Renamed "), record, "Unknown"))
    }
    @Test fun currentUsernameWinsOverHistoricalDisplayWhenCurrentNameIsEmpty() {
        assertEquals("current-user", callLogDisplayName(person("  "), record, "Unknown"))
    }
    @Test fun missingContactFallsBackToHistoryAndBlankHistoryToUnknown() {
        assertEquals("Historical label", callLogDisplayName(null, record, "Unknown"))
        assertEquals("Unknown", callLogDisplayName(null, record.copy(remoteDisplayName = " ", remoteUsername = ""), "Unknown"))
    }
}
