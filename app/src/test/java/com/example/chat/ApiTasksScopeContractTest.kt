package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

class ApiTasksScopeContractTest {
    private fun owner(user: String) = ApiAccountScope(user, "token-$user", "tenant-$user", "https://$user.example.test", "", 1)
    @Test fun replacedOwnerNeverSendsMutation() {
        val first = owner("first")
        val next = owner("next")
        var calls = 0
        val client = ApiTasksClient({ error("Ambient route must not be read") }, { error("Ambient tenant must not be read") },
            { error("Ambient token must not be read") }, ApiHttpTransport { _, _, _, _ -> calls++; 200 to "{}" }, { next })
        assertNull(client.updateTask("task-id", "Title", "", emptyList(), "medium", "", first))
        assertFalse(client.deleteTask("task-id", first))
        assertEquals(0, calls)
    }
    @Test fun inFlightMutationKeepsCapturedWireScopeButRejectsLateSuccess() {
        val first = owner("first")
        var current = first
        val client = ApiTasksClient({ "https://wrong.test" }, { "wrong" }, { "wrong" }, ApiHttpTransport { _, url, headers, body ->
            assertEquals("https://first.example.test/tasks", url)
            assertEquals("tenant-first", headers["x-tenant-id"])
            assertEquals("Bearer token-first", headers["Authorization"])
            assertTrue(org.json.JSONObject(body!!).isNull("assigned_to_id"))
            current = owner("next")
            200 to """{"data":{"uid":"task-id"}}"""
        }, { current })
        try { client.createTask("Title", null, emptyList(), "medium", null, first); fail("Old success must not escape") }
        catch (_: ApiScopeChangedException) { }
    }
    @Test fun multipleAssigneesAreRejectedRatherThanSilentlyTruncated() {
        var calls = 0
        val client = ApiTasksClient({ "https://tasks.test" }, { "tenant" }, { "token" },
            ApiHttpTransport { _, _, _, _ -> calls++; 200 to "{}" })
        try { client.createTask("Title", null, listOf("one", "two"), "medium", null); fail("Cannot drop assignees") }
        catch (_: IllegalArgumentException) { }
        assertNull(client.updateTask("task-id", "Title", "", listOf("one", "two"), "medium", ""))
        assertEquals(0, calls)
    }
}
