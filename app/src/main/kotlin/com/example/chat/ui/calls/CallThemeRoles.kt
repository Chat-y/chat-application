package com.example.chat.ui.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.calls.bundles.CallBundleRegistry
import com.example.chat.ui.calls.bundles.CallBundleId

/** User theme owns color; bundle owns geometry and gesture identity. */
@Composable
fun themedCallBundle(id: CallBundleId) = themedCallBundleTokens(id, LocalAppColors.current)

/** Non-composable form so call sites can remember() the merge (CALL-R1-28). */
fun themedCallBundleTokens(id: CallBundleId, c: com.example.chat.ui.theme.AppColors) =
    CallBundleRegistry.getTokens(id).let { geometry ->
        geometry.copy(primaryAccent = c.accent, acceptColor = c.success, declineColor = c.error,
            surfaceBackground = c.background, cardBackground = c.surface, primaryText = c.foreground,
            secondaryText = c.foregroundSecondary, controlBarBackground = c.surface)
    }

/** Semantic overlays deliberately use theme surfaces instead of fixed black/white. */
// CALL-R1-06: non-static so an audio->video upgrade mid-render propagates.
internal val LocalIncomingVideoBackdrop = androidx.compose.runtime.compositionLocalOf { false }
@Composable fun callBackdrop(): Color = if (LocalIncomingVideoBackdrop.current) Color.Transparent else LocalAppColors.current.background
@Composable fun callSurface(): Color = LocalAppColors.current.surface
@Composable fun callForeground(): Color = LocalAppColors.current.foreground
@Composable fun callSecondary(): Color = LocalAppColors.current.foregroundSecondary
@Composable fun callAccent(): Color = LocalAppColors.current.accent
