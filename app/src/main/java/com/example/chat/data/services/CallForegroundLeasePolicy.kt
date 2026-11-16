package com.example.chat.data.services

/** Each new service command replaces the preceding provisional token. Null transfers to lifecycle. */
internal class CallForegroundLeasePolicy {
    private var current: String? = null
    @Synchronized fun begin(token: String?) { current = token }
    @Synchronized fun releaseIfCurrent(token: String): Boolean {
        if (current != token) return false
        current = null
        return true
    }
}
