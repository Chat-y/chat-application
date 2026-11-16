package com.example.chat.ui.chats.detail

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** MR-R3-043: status updates are polite without creating another focus target. */
@Composable
internal fun MediaReviewStatusHost(state: SnackbarHostState) {
    SnackbarHost(hostState = state, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
