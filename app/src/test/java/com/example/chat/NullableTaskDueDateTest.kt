package com.example.chat

import com.example.chat.domain.models.ChatTask
import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiTasksClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Source-only regression cases, not executed as part of this patch. */
class NullableTaskDueDateTest {
    @Test fun absentNullAndMalformedDatesDoNotBecomeOverdueNow() {
        listOf(emptyMap<String, Any?>(), mapOf("due_date" to null), mapOf("due_date" to "bad-date")).forEach {
            val task = ChatTask.fromApi(it)
            assertNull(task.dueAt)
            assertFalse(task.isOverdue)
            assertEquals("No due date", task.dueRelativeText)
        }
    }
    @Test fun actualDatesArePreserved() {
        val due = "2030-01-01T09:00:00Z"
        assertEquals(Instant.parse(due), ChatTask.fromApi(mapOf("due_date" to due)).dueAt)
    }
    @Test fun updateCanClearAnExistingDeadline() {
        var sent: JSONObject? = null
        val client = ApiTasksClient({ "https://tasks.test" }, { "tenant" }, { "token" }, ApiHttpTransport { _, _, _, body ->
            sent = JSONObject(requireNotNull(body))
            200 to """{"data":{"uid":"task-id","due_date":null}}"""
        })
        val result = client.updateTask("task-id", "Title", "", emptyList(), "medium", null)
        assertNotNull(result)
        assertTrue(requireNotNull(sent).has("due_date"))
        assertTrue(requireNotNull(sent).isNull("due_date"))
        assertNull(result?.dueAt)
    }
}
