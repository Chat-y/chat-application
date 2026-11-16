package com.example.chat.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.chat.ui.theme.LocalAppColors

/** Semantic colors for chrome drawn over the live camera preview.
 *
 *  Why a separate token set: app theme tokens can be light-on-light and are unsafe over an
 *  arbitrary viewfinder feed, so overlay content keeps fixed light-on-dark defaults. The
 *  accent/danger/success/warning tokens follow the active theme (LocalAppColors). Replacing
 *  scattered Color(0x..)/Color.White literals with these tokens keeps one theming seam
 *  without changing the current appearance.
 *
 *  Render-pipeline values (effect color matrices, lens art, segmentation canvases) are
 *  content, not chrome, and intentionally do NOT use these tokens. */
class CameraOverlayColors(
    /** Primary icons/text over the preview. */
    val content: Color,
    /** Dimmed icons/text over the preview. */
    val contentSecondary: Color,
    /** Disabled/hint icons and text. */
    val contentDisabled: Color,
    /** Text/icons on selected light chips (inverse of content). */
    val contentInverse: Color,
    /** Control/pill backgrounds over the preview. */
    val scrim: Color,
    /** Stronger scrim for badges over the preview. */
    val scrimStrong: Color,
    /** Full-screen letterbox and review backdrop. */
    val backdrop: Color,
    /** Capture flash overlay. */
    val captureFlash: Color,
    /** Active mode/zoom highlight. Follows the theme accent. */
    val accent: Color,
    /** Recording indicator. Follows the theme danger color. */
    val danger: Color,
    /** Level-ok / ready confirmations. Follows the theme success color. */
    val success: Color,
    /** Caution marks (document quad invalid, warnings). Follows the theme warning color. */
    val warning: Color,
    /** Informational guides (document scan guide frame). Follows the theme info/link color. */
    val info: Color,
)

/** Fixed light-on-dark defaults; only the four semantic tokens below follow the theme. */
val LocalCameraOverlayColors = staticCompositionLocalOf {
    CameraOverlayColors(
        content = Color.White,
        contentSecondary = Color(0x99FFFFFF),
        contentDisabled = Color(0x4DFFFFFF),
        contentInverse = Color.Black,
        scrim = Color(0x99141414),
        scrimStrong = Color(0x88000000),
        backdrop = Color.Black,
        captureFlash = Color.White,
        accent = Color(0xFFFFD54F),
        danger = Color.Red,
        success = Color(0xFF7CFAAE),
        warning = Color.Yellow,
        info = Color(0xFF60A5FA),
    )
}

/** Resolves the overlay tokens for the current theme: base overlay palette with
 *  accent/danger/success/warning taken from LocalAppColors. */
@Composable
fun rememberCameraOverlayColors(): CameraOverlayColors {
    val app = LocalAppColors.current
    val base = LocalCameraOverlayColors.current
    return remember(app, base) {
        CameraOverlayColors(
            content = base.content,
            contentSecondary = base.contentSecondary,
            contentDisabled = base.contentDisabled,
            contentInverse = base.contentInverse,
            scrim = base.scrim,
            scrimStrong = base.scrimStrong,
            backdrop = base.backdrop,
            captureFlash = base.captureFlash,
            accent = app.accent,
            danger = app.error,
            success = app.success,
            warning = app.warning,
            info = app.info,
        )
    }
}
