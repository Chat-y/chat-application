package com.example.chat.data.api

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Authored only. No network or real receipt; execution remains gated. */
class ApiRow51ContractTest {
    private val operation = "11111111-1111-1111-1111-111111111111"
    @Test fun ownLookupPostsAllThreeFieldsAndAmbiguityNeverPicksARow() = runBlocking {
        val api = ApiMlsRpcClient({ "https://test.invalid" }, { "tenant" }, { "token" }, ApiHttpTransport { method, url, headers, body ->
            assertEquals("POST", method); assertTrue(url.endsWith("/e2ee/messages/lookup"))
            assertEquals("tenant", headers["x-tenant-id"])
            val json = JSONObject(body ?: "")
            assertEquals("room", json.getString("conversation_id")); assertEquals("client", json.getString("client_message_id"))
            assertEquals("original-device", json.getString("sender_device_id"))
            409 to "{\"detail\":{\"code\":\"IDEMPOTENCY_LOOKUP_AMBIGUOUS\"}}"
        })
        var blocked = false
        try { api.rpc("lookup_own_mls_message", mapOf("p_conversation_id" to "room", "p_client_message_id" to "client", "p_sender_device_id" to "original-device")) }
        catch (_: IllegalStateException) { blocked = true }
        assertTrue(blocked)
    }
    @Test fun lookup404IsUnknownNotAnAcceptedCanonicalId() = runBlocking {
        val api = ApiMlsRpcClient({ "https://test.invalid" }, { "tenant" }, { "token" }, ApiHttpTransport { _, _, _, _ ->
            404 to "{\"detail\":{\"code\":\"MESSAGE_NOT_FOUND\"}}"
        })
        assertNull(api.rpc("lookup_own_mls_message", mapOf("p_conversation_id" to "room", "p_client_message_id" to "client", "p_sender_device_id" to "original-device")))
    }
    @Test fun rejectedExpiredPendingConfirmedAnd404StayDistinct() {
        for (status in listOf("rejected", "expired", "pending", "confirmed", "missing")) {
            val client = ApiDeletionReceiptClient(ApiHttpTransport { _, _, headers, _ ->
                assertEquals("x".repeat(43), headers["X-Deletion-Receipt"])
                if (status == "missing") 404 to "{}" else 200 to JSONObject().put("success", true).put("data", JSONObject()
                    .put("operation_id", operation).put("status", status).put("expires_at", "2026-11-20T00:00:00Z")).toString()
            })
            val result = client.recover("https://test.invalid", ApiDeletionReceiptClient.Prepared(operation, "x".repeat(43), "unused"))
            when (status) {
                "rejected", "expired" -> assertEquals(status, (result as ApiDeletionReceiptClient.Recovery.NotDeleted).status)
                "pending" -> assertTrue(result is ApiDeletionReceiptClient.Recovery.Pending)
                "confirmed" -> assertTrue(result is ApiDeletionReceiptClient.Recovery.Confirmed)
                else -> assertEquals(ApiDeletionReceiptClient.Recovery.Indeterminate, result)
            }
        }
    }
}
