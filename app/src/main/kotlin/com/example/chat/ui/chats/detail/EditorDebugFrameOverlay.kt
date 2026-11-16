package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** MR-R3-049 diagnostic aid only: callback spacing is NOT composition duration or a jank verdict.
 * Sampling requests frame-clock callbacks and can itself perturb timing. Never exports/logs data.
 */
@Composable
internal fun EditorDebugFrameOverlay(modifier: Modifier = Modifier) {
    if (!com.example.chat.BuildConfig.DEBUG) return
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> foreground = lifecycle.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var label by remember { mutableStateOf("DEBUG frame callbacks: waiting") }
    LaunchedEffect(foreground) {
        if (foreground) {
            var previous: Long? = null
            var publishedAt: Long? = null
            var count = 0
            var sum = 0.0
            var maximum = 0.0
            while (true) {
                val time = withFrameNanos { it }
                val interval = editorFrameIntervalMillis(previous, time)
                if (interval != null) { count++; sum += interval; maximum = maxOf(maximum, interval) }
                previous = time
                val published = publishedAt
                if (published == null) publishedAt = time
                else if (time - published >= 500_000_000L && count > 0) {
                    label = "DEBUG callback interval avg %.1f / max %.1f ms\nNot composition or render time".format(java.util.Locale.ROOT, sum / count, maximum)
                    publishedAt = time; count = 0; sum = 0.0; maximum = 0.0
                }
            }
        } else label = "DEBUG frame callbacks: paused"
    }
    Surface(modifier = modifier.clearAndSetSemantics {}, shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
        Text(label, Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

internal fun editorFrameIntervalMillis(previousNanos: Long?, currentNanos: Long): Double? =
    if (previousNanos == null || currentNanos <= previousNanos) null
    else (currentNanos.toDouble() - previousNanos.toDouble()) / 1_000_000.0
