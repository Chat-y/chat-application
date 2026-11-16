package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiTasksClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Request-shape checks only. Does not establish deployment or server acceptance. */
class ApiTasksDueDateContractTest {
    private class Transport : ApiHttpTransport {
        val bodies = mutableListOf<JSONObject>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            bodies += JSONObject(requireNotNull(body))
            return 200 to """{"data":{"uid":"task-id"}}"""
        }
    }
    @Test fun forwardsCreateAndUpdateDueDate() {
        val transport = Transport()
        val client = ApiTasksClient({ "https://example.test" }, { "tenant" }, { "token" }, transport)
        val due = "2026-12-01T09:30:00Z"
        client.createTask("Title", null, listOf("user"), "medium", due)
        client.updateTask("task-id", "Title", "", listOf("user"), "medium", due)
        assertEquals(2, transport.bodies.size)
        transport.bodies.forEach { assertEquals(due, it.getString("due_date")) }
    }
    @Test fun nullCreateDueDateIsExplicit() {
        val transport = Transport()
        val client = ApiTasksClient({ "https://example.test" }, { "tenant" }, { "token" }, transport)
        client.createTask("Title", null, emptyList(), "medium", null)
        assertTrue(transport.bodies.single().has("due_date"))
        assertTrue(transport.bodies.single().isNull("due_date"))
    }
}
