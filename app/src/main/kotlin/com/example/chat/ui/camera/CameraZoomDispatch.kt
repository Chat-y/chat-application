package com.example.chat.ui.camera

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** CAM-R2-007: main-thread consumer. Producer must use Channel.CONFLATED.
 * First action is immediate; during the34ms interval only the newest pending action survives.
 * Unlike debounce, continuous dragging still applies requests and retains the trailing value. */
internal suspend fun dispatchCameraZoomRequests(
    requests: ReceiveChannel<() -> Unit>, wait: suspend (Long) -> Unit = { delay(it) },
) {
    for (action in requests) {
        coroutineContext.ensureActive()
        action()
        wait(34L)
    }
}
