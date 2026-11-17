package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test

/** Authored UNRUN. Uses no real receipt or account. */
class ApiDeletionReceiptClientTest {
    @Test fun prepareUnavailableNeverDeletesAndRecovery404IsIndeterminate() {
        var calls = 0
        val client = ApiDeletionReceiptClient(object : ApiHttpTransport {
            override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
                calls++
                assertFalse(url.contains("receipt-secret"))
                return 404 to "{}"
            }
        })
        val owner = ApiAccountScope("u", "t", "tenant", "https://example.invalid", "", 1)
        var failed = false
        try { client.prepare(owner) { true } } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(1, calls)
        val receipt = ApiDeletionReceiptClient.Prepared("00000000-0000-0000-0000-000000000001", "receipt-secret", "2026-11-20T00:00:00")
        assertEquals(ApiDeletionReceiptClient.Recovery.Indeterminate, client.recover(owner.baseUrl, receipt))
    }
}
