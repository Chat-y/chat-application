package com.example.chat.ui.updates

/** Translation bounds for a centered ContentScale.Fit image, in viewport pixels. */
internal object StatusZoomRules {
    fun bounds(viewWidth: Float, viewHeight: Float, imageWidth: Float, imageHeight: Float, scale: Float): Pair<Float, Float> {
        if (listOf(viewWidth, viewHeight, imageWidth, imageHeight, scale).any { !it.isFinite() || it <= 0f }) return 0f to 0f
        val fittedScale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val zoom = scale.coerceIn(1f, 5f)
        val x = (imageWidth * fittedScale * zoom - viewWidth).coerceAtLeast(0f) / 2f
        val y = (imageHeight * fittedScale * zoom - viewHeight).coerceAtLeast(0f) / 2f
        return if (x.isFinite() && y.isFinite()) x to y else 0f to 0f
    }
}
