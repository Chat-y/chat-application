package com.example.chat.data.api

/** Privacy and immutable owner both have to match immediately before a read acknowledgement. */
object ReadReceiptDispatchPolicy {
    fun allowed(owner: ApiAccountScope, current: () -> ApiAccountScope, roomId: String,
        preference: (String, ApiAccountScope) -> Boolean): Boolean {
        if (roomId.isBlank() || owner.token.isNullOrBlank() || owner.userId.isNullOrBlank() || current() !== owner) return false
        // A failed/initializing preference source must never publish a read receipt.
        val permitted = try { preference(roomId, owner) } catch (_: Exception) { false }
        return permitted && current() === owner
    }
}
