package com.example.chat.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chat.BuildConfig
import com.example.chat.R
import com.example.chat.core.audio.AudioPolicyCoordinator
import com.example.chat.core.audio.formatAudioDiagnosticExport
import com.example.chat.ui.theme.LocalAppColors

/** No automatic export or files. The user chooses a recipient in Android's sharesheet. */
@Composable
internal fun AudioDiagnosticShare(coordinator: AudioPolicyCoordinator) {
    if (!BuildConfig.DEBUG) return
    var failed by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val chooserTitle = stringResource(R.string.audio_debug_share_title)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    Column(Modifier.fillMaxWidth()) {
        TextButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
            failed = false
            try {
                val payload = formatAudioDiagnosticExport(coordinator.diagnostics)
                launcher.launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload)
                }, chooserTitle))
            } catch (_: Exception) { failed = true }
        }) { Text(chooserTitle) }
        Text(stringResource(R.string.audio_debug_share_scope), color = colors.foregroundSecondary)
        if (failed) Text(stringResource(R.string.audio_debug_share_failed), color = colors.error)
    }
}
