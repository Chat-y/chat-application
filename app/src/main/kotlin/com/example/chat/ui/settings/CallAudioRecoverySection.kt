package com.example.chat.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.core.audio.CallAudioRecovery
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Explicit retained-reservation recovery. Never retries a call, restarts recording or autoplays. */
@Composable
internal fun CallAudioRecoverySection() {
    val recovery = CallAudioRecovery.store
    val status by recovery.status.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    if (status.pending == 0) return
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Audio cleanup needs attention", color = colors.error)
        Text(if (status.nativeCleanupUncertain)
// Previous implementation retained:             "Call capture cleanup could not be confirmed. Close and reopen Chaty before making another call or recording."
            "Call capture cleanup could not be confirmed. In Android Settings, force stop Chaty, then reopen it before making another call or recording."
            else "Previous media could not be paused. Retry cleanup before starting a call or recording.",
            color = colors.foregroundSecondary)
        TextButton(enabled = !busy && !status.nativeCleanupUncertain,
            modifier = Modifier.heightIn(min = 48.dp), onClick = {
                busy = true
                scope.launch {
                    try { failed = !recovery.retry() }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Checking audio cleanup…" else "Retry audio cleanup") }
        if (failed) Text("Cleanup is not confirmed. Nothing was restarted.", color = colors.error)
    }
}
