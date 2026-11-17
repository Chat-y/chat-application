package com.example.chat.data.api

import org.json.JSONObject

/** Exact receipt protocol adapter. Explicit recovery only, never background polling.
 * This is not wired to the deletion UI yet. No token appears in paths or errors. */
class ApiDeletionReceiptClient(private val transport: ApiHttpTransport) {
    class Prepared internal constructor(val operationId: String, internal val receiptToken: String, val expiresAt: String) {
        override fun toString() = "PreparedDeletionReceipt(redacted)"
    }
    sealed interface Recovery {
        data class Pending(val expiresAt: String) : Recovery
        data class Confirmed(val expiresAt: String) : Recovery
        data class NotDeleted(val status: String, val expiresAt: String) : Recovery
        data object Indeterminate : Recovery
        data object RateLimited : Recovery
    }
    fun prepare(scope: ApiAccountScope, isCurrent: () -> Boolean): Prepared {
        check(isCurrent() && !scope.token.isNullOrBlank()) { "Sign in to prepare account deletion." }
        val result = transport.execute("POST", scope.baseUrl.trimEnd('/') + "/auth/delete-account/prepare",
            headers(scope), "{}")
        check(isCurrent()) { "Account changed while preparing deletion." }
        check(result.first == 200) { "Deletion recovery is unavailable. Nothing was deleted." }
        val data = envelope(result.second)
        val id = uuid(data.getString("operation_id"))
        val token = data.getString("receipt_token")
        check(token.matches(Regex("[A-Za-z0-9_-]{43}"))) { "Deletion recovery receipt was invalid." }
        return Prepared(id, token, data.getString("expires_at"))
    }
    /** Caller MUST durably save prepared in its encrypted owner journal before calling. */
    fun delete(scope: ApiAccountScope, prepared: Prepared, password: String, isCurrent: () -> Boolean) {
        check(isCurrent() && !scope.token.isNullOrBlank()) { "Account changed before deletion." }
        val body = JSONObject().put("password", password).put("operation_id", prepared.operationId).toString()
        val result = transport.execute("POST", scope.baseUrl.trimEnd('/') + "/auth/delete-account", headers(scope), body)
        when (result.first) {
            403 -> error("Password verification failed. Nothing was deleted.")
            404 -> error("Deletion operation expired or was not found. Prepare a new operation before retrying.")
        }
        check(result.first == 200) { "Deletion outcome is unknown. Use explicit recovery before clearing local data." }
        val data = envelope(result.second)
        check(data.optString("deletion_status") == "confirmed" && data.optString("operation_id") == prepared.operationId) {
            "Deletion was not confirmed for this operation. Use explicit recovery."
        }
        // Confirmation belongs to the captured owner even if UI auth changed during I/O.
    }
    private val recoveryAllowedAfterMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun recover(baseUrl: String, prepared: Prepared): Recovery {
        val cooldownKey = baseUrl + ":" + prepared.operationId
        val now = System.nanoTime() / 1_000_000L
        if (now < (recoveryAllowedAfterMs[cooldownKey] ?: 0L)) return Recovery.RateLimited
        val url = baseUrl.trimEnd('/') + "/auth/delete-account/status/" + uuid(prepared.operationId)
        val requestHeaders = mapOf("X-Deletion-Receipt" to prepared.receiptToken)
        val response = if (transport is ApiHeaderHttpTransport) transport.executeWithHeaders("GET", url, requestHeaders, null)
            else transport.execute("GET", url, requestHeaders, null).let { ApiHeaderResponse(it.first, it.second, null) }
        val result = response.code to response.body
        if (result.first == 429) {
            val seconds = response.retryAfter?.toLongOrNull()?.coerceIn(60L, 86_400L) ?: 60L
            recoveryAllowedAfterMs[cooldownKey] = System.nanoTime() / 1_000_000L + seconds * 1000L
            return Recovery.RateLimited
        }
        if (result.first != 200) return Recovery.Indeterminate
        return runCatching {
            val data = envelope(result.second)
            check(data.getString("operation_id") == prepared.operationId)
            val expires = data.getString("expires_at")
            when (data.getString("status")) {
                "confirmed" -> Recovery.Confirmed(expires)
                "pending" -> Recovery.Pending(expires)
                "rejected", "expired" -> Recovery.NotDeleted(data.getString("status"), expires)
                else -> Recovery.Indeterminate
            }
        }.getOrDefault(Recovery.Indeterminate)
    }
    private fun envelope(body: String): JSONObject {
        val root = JSONObject(body)
        check(root.optBoolean("success", false)) { "Deletion recovery response was not verified." }
        return root.getJSONObject("data")
    }
    private fun headers(scope: ApiAccountScope) = mapOf("Authorization" to "Bearer ${scope.token}", "x-tenant-id" to scope.tenantId)
    private fun uuid(value: String): String {
        check(value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) { "Deletion operation ID was invalid." }
        return value
    }
}
