package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.calls.callLogRenderKey
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Render keys only; backend IDs and deletion identities are not rewritten. */
class CallLogDuplicateKeysGapTest {
    private fun record(id: String, timestamp: Instant = Instant.EPOCH) = CallRecord(
        id, "self", listOf("self", "other"), CallType.voice, CallDirection.outgoing, timestamp, 0)

    @Test fun identicalAndBlankIdsHaveDistinctRenderKeys() {
        val rows = listOf(record(""), record(""), record("same"), record("same"))
        val keys = rows.mapIndexed { index, row -> callLogRenderKey(row, index) }
        assertEquals(rows.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("row:") })
        assertEquals(listOf("", "", "same", "same"), rows.map { it.id })
    }
    @Test fun keysAreRepeatableAndSeparateHeaderNamespace() {
        val row = record("bucket-1970-01-01:#")
        assertEquals(callLogRenderKey(row, 0), callLogRenderKey(row.copy(), 0))
        assertNotEquals("bucket-1970-01-01", callLogRenderKey(row, 0))
        assertNotEquals(callLogRenderKey(row, 0), callLogRenderKey(row.copy(timestamp = Instant.ofEpochSecond(1)), 0))
    }
}
