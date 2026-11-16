package com.example.chat.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** All marker/repository disk operations return to the caller before changing UI state. */
internal suspend fun <T> mediaDiskIo(action: () -> T): T = withContext(Dispatchers.IO) { action() }

/** Keep entry resync and user writes ordered after moving the disk work off Main. */
internal class HideMediaDiskQueue {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    suspend fun <T> transaction(action: () -> T, publish: (T) -> Unit): T {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        // Preserve the former non-suspending disk+preference commit boundary.
        return withContext(kotlinx.coroutines.NonCancellable) {
            val result = run(action)
            publish(result)
            result
        }
    }
    suspend fun <T> run(action: () -> T): T {
        mutex.lock()
        return try { mediaDiskIo(action) } finally { mutex.unlock() }
    }
}
