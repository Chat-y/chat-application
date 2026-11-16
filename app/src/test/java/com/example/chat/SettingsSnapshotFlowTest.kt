package com.example.chat

import com.example.chat.ui.settings.state.settingsSnapshotFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class SettingsSnapshotFlowTest {
    private class Source {
        var generation = 0L
        var value = "first"
        val listeners = mutableSetOf<() -> Unit>()
        fun snapshot() = generation to value
        fun notifyChanged() { listeners.toList().forEach { it() } }
    }
    private suspend fun until(predicate: () -> Boolean) = withTimeout(3_000) {
        while (!predicate()) delay(1)
    }
    @Test fun unrelatedNotificationsDoNotEmitAndAccountGenerationDoes() = runBlocking {
        val source = Source(); val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val state = settingsSnapshotFlow(owner, source::snapshot, { source.listeners.add(it) }, { source.listeners.remove(it) })
            val values = mutableListOf<Pair<Long,String>>()
            val collector = launch { state.collect { values.add(it) } }
            until { source.listeners.size == 1 && values.size == 1 }
            repeat(5) { source.notifyChanged() }
            delay(20); assertEquals(1, values.size)
            source.value = "second"; source.notifyChanged()
            until { values.last().second == "second" }
            source.generation++; source.notifyChanged()
            until { values.last().first == 1L }
            assertEquals(3, values.size)
            collector.cancelAndJoin()
            until { source.listeners.isEmpty() }
        } finally { owner.cancel() }
    }
    @Test fun stoppedCollectionReleasesListenerAndResumeReadsLatestSnapshot() = runBlocking {
        val source = Source(); val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val state = settingsSnapshotFlow(owner, source::snapshot, { source.listeners.add(it) }, { source.listeners.remove(it) })
            var collector = launch { state.collect { } }
            until { source.listeners.size == 1 }
            collector.cancelAndJoin(); until { source.listeners.isEmpty() }
            source.generation = 2; source.value = "changed while stopped"
            collector = launch { state.collect { } }
            until { state.value == source.snapshot() && source.listeners.size == 1 }
            collector.cancelAndJoin(); until { source.listeners.isEmpty() }
        } finally { owner.cancel() }
    }
    @Test fun overlappingCollectorsShareOneListenerAndOwnerCancellationCleansUp() = runBlocking {
        val source = Source(); val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val state = settingsSnapshotFlow(owner, source::snapshot, { source.listeners.add(it) }, { source.listeners.remove(it) })
            val one = launch { state.collect { } }; val two = launch { state.collect { } }
            until { source.listeners.size == 1 }
            one.cancelAndJoin(); assertEquals(1, source.listeners.size)
            owner.cancel(); until { source.listeners.isEmpty() }
            two.cancelAndJoin()
        } finally { owner.cancel() }
    }
}
