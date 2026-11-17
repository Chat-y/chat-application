package com.example.chat.data.api

import org.json.JSONObject

/** No local purge or sign-out until an explicit server completion receipt. */
class ApiAccountDeletionClient(private val transport: ApiHttpTransport) {
    fun delete(scope: ApiAccountScope, current: () -> Boolean, userId: String) {
        check(scope.userId == userId && !scope.token.isNullOrBlank()) { "Sign in to delete your own account." }
        val id = ApiPathSegments.safe(userId) ?: error("Invalid account identifier.")
        check(current()) { "Account changed. Open Settings again." }
        val headers = mapOf("Authorization" to "Bearer ${scope.token}", "x-tenant-id" to scope.tenantId)
        val base = scope.baseUrl.trimEnd('/')
        val first = transport.execute("DELETE", "$base/app-users/$id", headers, null)
        // A verified success belongs to the captured owner even if the UI switched.
        // Do not discard it before its durable cleanup receipt is recorded.
        val response = if (first.first == 404) {
            check(current()) { "Account changed before fallback. Server outcome requires verification." }
            transport.execute("DELETE", "$base/api/v1/app-users/$id", headers, null)
        } else first
        // Live backend answers 200 {"success": true, "data": null,
        // "message": "User deleted successfully."} - no user_id/deleted echo.
        // Accept the success envelope as the completion receipt (still
        // fail-closed on anything else), and keep the older echoed form.
        val confirmed = response.first == 204 || (response.first == 200 && runCatching {
            val root = JSONObject(response.second)
            if (root.optBoolean("success", false)) {
                true
            } else {
                val data = root.getJSONObject("data")
                data.optString("user_id") == id && data.optBoolean("deleted", false)
            }
        }.getOrDefault(false))
        check(confirmed) { "Server did not confirm account deletion. Your local data and sign-in were kept. Try again or contact support." }
    }
}

// Previous implementation retained inactive (unified v4):
//         check(current()) { "Account changed. Deletion result was not applied." }
//         val response = if (first.first == 404) transport.execute("DELETE", "$base/api/v1/app-users/$id", headers, null) else first
//         check(current()) { "Account changed. Deletion result was not applied." }
