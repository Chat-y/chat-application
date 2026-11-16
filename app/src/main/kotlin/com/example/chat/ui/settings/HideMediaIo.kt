package com.example.chat.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** All marker/repository disk operations return to the caller before changing UI state. */
internal suspend fun <T> mediaDiskIo(action: () -> T): T = withContext(Dispatchers.IO) { action() }

/** Keep entry resync and user writes ordered after moving the disk work off Main. */
internal class HideMediaDiskQueue {
//     private val mutex = kotlinx.coroutines.sync.Mutex()
    // Marker paths are app-wide, so replacement screen/account state must share ordering.
    private companion object { val mutex = kotlinx.coroutines.sync.Mutex() }
    suspend fun <T> transaction(action: () -> T, publish: (T) -> Unit): T {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        // Preserve the former non-suspending disk+preference commit boundary.
//         return withContext(kotlinx.coroutines.NonCancellable) {
//             val result = run(action)
//             publish(result)
//             result
//         }
        // Waiting is cancellable. Once IO starts, retain the existing commit boundary and
        // hold the same lock until publication, not just until the disk callback returns.
        mutex.lock()
        return try {
            withContext(kotlinx.coroutines.NonCancellable) {
                val result = mediaDiskIo(action)
                publish(result)
                result
            }
        } finally { mutex.unlock() }
    }
    suspend fun <T> run(action: () -> T): T {
        mutex.lock()
        return try { mediaDiskIo(action) } finally { mutex.unlock() }
    }
}
