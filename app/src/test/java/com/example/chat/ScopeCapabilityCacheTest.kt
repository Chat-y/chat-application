package com.example.chat

import com.example.chat.data.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ScopeCapabilityCacheTest {
    private fun owner(g: Long) = ApiAccountScope("me", "token$g", "t", "https://example.invalid", "", g)
    @Test fun repeatedMessagesUseOneProbeUntilTtl() = runBlocking {
        var now = 0L; var probes = 0; val a = owner(1); val cache = ScopeCapabilityCache({ now }, 100)
        repeat(5) { assertTrue(cache.supports(a, "send", { a }) { probes++; true }) }
        assertEquals(1, probes)
        now = 100
        assertFalse(cache.supports(a, "send", { a }) { probes++; false })
        assertEquals(2, probes)
    }
    @Test fun anotherScopeNeverInheritsResult() = runBlocking {
        val a = owner(1); val b = owner(2); var current = a; var probes = 0
        val cache = ScopeCapabilityCache({ 0 })
        assertTrue(cache.supports(a, "send", { current }) { probes++; true })
        current = b
        assertFalse(cache.supports(a, "send", { current }) { fail("stale probe"); true })
        assertFalse(cache.supports(b, "send", { current }) { probes++; false })
        assertEquals(2, probes)
    }
    @Test fun concurrentMessagesShareProbeAndStaleCompletionIsDropped() = runBlocking {
        val a = owner(1); val b = owner(2); var current = a
        val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        val cache = ScopeCapabilityCache({ 0 })
        val job = async { cache.supports(a, "send", { current }) { started.complete(Unit); gate.await(); true } }
        started.await(); current = b; gate.complete(Unit)
        assertFalse(job.await())
        assertTrue(cache.supports(b, "send", { current }) { true })
    }
}
