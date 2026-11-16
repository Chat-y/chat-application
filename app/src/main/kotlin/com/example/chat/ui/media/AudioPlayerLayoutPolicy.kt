package com.example.chat.ui.media

/** UI-only MV-R3-033 breakpoint; existing short-window budget still wins. */
internal fun audioDiscSizeDp(widthDp: Int, heightDp: Int): Int = when {
    heightDp < 600 -> 144
    widthDp < 340 -> 180
    else -> 240
}

/** MV-R3-023: visual rotation only. Playback rate and audio state are never changed here. */
internal fun audioDiscRotationMillis(speed: Double): Int {
    val safeSpeed = if (speed.isFinite() && speed > 0.0) speed else 1.0
    return (14_000.0 / safeSpeed).coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
}

/** MV-R3-029: configuration intent plus measured pane sanity for split windows. */
internal fun audioUseLandscapePanes(landscape: Boolean, widthDp: Float, heightDp: Float): Boolean =
    landscape && widthDp.isFinite() && heightDp.isFinite() && widthDp > heightDp && heightDp > 0f

/** Art is centered within its already40%-weighted pane, with16dp breathing room each side. */
internal fun audioLandscapeDiscSizeDp(paneWidthDp: Float, paneHeightDp: Float, preferredDp: Float): Float {
    if (!paneWidthDp.isFinite() || !paneHeightDp.isFinite() || !preferredDp.isFinite()) return 0f
    return minOf(preferredDp.coerceAtLeast(0f), (paneWidthDp - 32f).coerceAtLeast(0f), (paneHeightDp - 32f).coerceAtLeast(0f))
}
