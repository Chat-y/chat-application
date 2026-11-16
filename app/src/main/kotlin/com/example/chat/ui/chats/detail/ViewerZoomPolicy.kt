package com.example.chat.ui.chats.detail

/** MV-R5-038: viewport-constrained zoom math, extracted without changing gesture policy.
 * Inputs are the reachable gesture state: oldScale >= 1, viewport extents >= 0.
 * Rotation is intentionally not an input. No Android or Compose runtime dependency. */
internal data class ViewerZoomTransform(val scale: Float, val x: Float, val y: Float)

internal fun viewerZoomTransform(
    oldScale: Float, offsetX: Float, offsetY: Float,
    centroidX: Float, centroidY: Float, panX: Float, panY: Float,
    zoom: Float, viewportW: Float, viewportH: Float,
): ViewerZoomTransform {
    val newScale = (oldScale * zoom).coerceAtLeast(1f).coerceIn(0.7f, 5f)
    val factor = newScale / oldScale
    val zoomedX = centroidX - (centroidX - offsetX) * factor + panX
    val zoomedY = centroidY - (centroidY - offsetY) * factor + panY
    val minX = minOf(0f, (1f - newScale) * viewportW)
    val maxX = maxOf(0f, (1f - newScale) * viewportW)
    val minY = minOf(0f, (1f - newScale) * viewportH)
    val maxY = maxOf(0f, (1f - newScale) * viewportH)
    return ViewerZoomTransform(newScale, zoomedX.coerceIn(minX, maxX), zoomedY.coerceIn(minY, maxY))
}

/** MV-R1-011: double tap toggles fit/2.5x around the tap, retaining viewport clamps. */
internal fun viewerDoubleTapTransform(
    scale: Float, x: Float, y: Float, tapX: Float, tapY: Float,
    viewportW: Float, viewportH: Float,
): ViewerZoomTransform {
    val targetScale = if (scale > 1f) 1f else 2.5f
    return viewerZoomTransform(scale, x, y, tapX, tapY, 0f, 0f, targetScale / scale, viewportW, viewportH)
}

/** MV-R1-042: whole-percent accessibility label, not a playback or image-quality claim. */
internal fun viewerZoomPercent(scale: Float): Int = kotlin.math.round(scale * 100f).toInt()

/** MV-R2-025: two display pixels per viewport pixel for bounded decode zoom headroom.
 * MV-R5-017 budget note: a width W x height H viewport requests at most 2W x 2H pixels
 * per dimension (Int saturation is overflow protection, NOT a practical memory cap).
 * One software ARGB_8888 buffer at that bound costs 16*W*H bytes before row padding:
 * 1080x2400 -> 41,472,000 bytes (~39.6 MiB); 1440x3200 -> 73,728,000 (~70.3 MiB).
 * Coil sampling/aspect fit/source dimensions may reduce the actual decode, but its decoder,
 * temporary/original/transition buffers, graphics upload, and UI allocations add overhead.
 * Hardware bitmap accounting differs. There is no fixed 8-16 MB screen-memory guarantee.
 * Memory/disk request caching is disabled at this call site, not a global loader budget.
 * This display-relative request bound is not a tile/region decoder or measured RSS ceiling.
 * Peak heap/native/GPU allocation and low-memory behavior need device profiling separately.
 */
internal fun viewerDecodeExtent(viewportPixels: Int): Int =
    (viewportPixels.coerceAtLeast(1).toLong() * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
