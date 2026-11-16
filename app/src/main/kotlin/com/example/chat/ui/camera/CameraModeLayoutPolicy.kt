package com.example.chat.ui.camera

/** CAM-R1-046: keep fixed, centerable slots while allowing accessibility text growth. */
internal fun cameraModeSlotWidthDp(tight: Boolean, fontScale: Float): Float =
    (if (tight) 76f else 88f) * fontScale.coerceAtLeast(1f)

/** CAM-R1-058: same camera breakpoint for bottom mode controls and zoom chrome. */
internal fun cameraControlsTight(widthDp: Int, heightDp: Int): Boolean =
    minOf(widthDp, heightDp) < 340 || heightDp < 680

/** CAM-R1-037: announce the target lens, not an ambiguous switch command. */
internal fun cameraFlipActionLabel(frontCamera: Boolean): String =
    if (frontCamera) "Switch to back camera" else "Switch to front camera"
