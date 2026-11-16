package com.example.chat.ui.chats.detail

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** MR-R3-027: visual feedback only; the caller retains its save/reentry/disabling policy. */
@Composable
internal fun EditorExportButtonContent(saving: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // MR-R4-043 prior: Text(if (saving) "Exporting" else "Apply")
        Text(if (saving) stringResource(com.example.chat.R.string.media_released_exporting) else stringResource(com.example.chat.R.string.media_released_apply))
        if (saving) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}
