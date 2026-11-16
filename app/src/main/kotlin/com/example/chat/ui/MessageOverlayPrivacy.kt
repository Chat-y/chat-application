package com.example.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Screen-owned veto for app-drawn message toast/island content, independent of App Lock.
 * A scoped count handles overlapping route transitions without one route clearing another's veto.
 * Does not change Android's separate system notification/listener policy. */
object MessageOverlayPrivacy {
    private var owners by mutableIntStateOf(0)
    val suppressed: Boolean get() = owners > 0
    internal fun acquire() { owners++ }
    internal fun release() { owners = (owners - 1).coerceAtLeast(0) }
}

/** Put this in the Starred route while its content is composed. Removed routes release the veto. */
@Composable
fun SuppressMessageOverlaysWhileVisible() {
    DisposableEffect(Unit) {
        MessageOverlayPrivacy.acquire()
        onDispose { MessageOverlayPrivacy.release() }
    }
}
