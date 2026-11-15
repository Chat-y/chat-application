package com.example.chat.data.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One owner at a time. Cache an authenticated result briefly, never across scope changes. */
class ScopeCapabilityCache(private val nowMillis: () -> Long, private val ttlMillis: Long = 300_000L) {
    private val lock = Mutex()
    private var owner: ApiAccountScope? = null
    private val values = mutableMapOf<String, Pair<Boolean, Long>>()

    suspend fun supports(expected: ApiAccountScope, feature: String, current: () -> ApiAccountScope,
        probe: suspend () -> Boolean): Boolean = lock.withLock {
        if (current() !== expected) return@withLock false
        if (owner !== expected) { owner = expected; values.clear() }
        val now = nowMillis()
        values[feature]?.takeIf { now - it.second in 0 until ttlMillis }?.let { return@withLock it.first }
        val supported = probe()
        if (current() !== expected) return@withLock false
        values[feature] = supported to nowMillis()
        supported
    }
}
