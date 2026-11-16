package com.example.chat.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.repeatOnLifecycle
import com.example.chat.R
import com.example.chat.data.services.CallMicrophoneCaptureProvider
import com.example.chat.data.services.CallMicrophoneCaptureState
import com.example.chat.ui.theme.LocalAppColors

internal fun microphoneCaptureLabelResource(state: CallMicrophoneCaptureState, muted: Boolean): Int = when (state) {
    CallMicrophoneCaptureState.active -> if (muted) R.string.call_microphone_in_use_muted else R.string.call_microphone_in_use
    CallMicrophoneCaptureState.starting -> R.string.call_microphone_starting
    CallMicrophoneCaptureState.stopped -> R.string.call_microphone_stopped
    CallMicrophoneCaptureState.unknown -> R.string.call_microphone_unknown
}

/** Native capture state, separate from mute/transmission and Android's own privacy indicator. */
@Composable
internal fun CallMicrophoneIndicator(provider: CallMicrophoneCaptureProvider?, muted: Boolean) {
    val unavailable = remember { kotlinx.coroutines.flow.MutableStateFlow(CallMicrophoneCaptureState.unknown) }
    val flow = provider?.microphoneCaptureState ?: unavailable
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state by produceState(flow.value, flow, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            flow.collect { value = it }
        }
    }
    val colors = LocalAppColors.current
    Text(stringResource(microphoneCaptureLabelResource(state, muted)),
        style = MaterialTheme.typography.labelSmall, color = colors.foreground,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.background(colors.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
}
