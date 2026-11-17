package com.example.chat.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Explicit purpose binding for legacy vector call sites. The retained stock vector records provenance only;
 * it can never select artwork or override the user's global/per-purpose theme. Callback and tint stay owned
 * by the caller. A repeated semantic purpose may share one ID, different actions need distinct purpose IDs. */
@Composable
fun ChatyPurposeIcon(
    purpose: ChatyIconId,
    @Suppress("UNUSED_PARAMETER") previousVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    ChatyIcon(purpose, modifier = modifier, color = tint, semanticLabel = contentDescription)
}
