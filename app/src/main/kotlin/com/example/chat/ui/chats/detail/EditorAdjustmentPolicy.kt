package com.example.chat.ui.chats.detail

/** Preview/export share this local pixel math. Alpha is never adjusted. */
internal fun buildEditorColorValues(
    brightness: Float, contrast: Float,
    saturation: Float = 1f, warmth: Float = 0f, tint: Float = 0f, fade: Float = 0f,
): FloatArray {
    fun safe(value: Float, fallback: Float, range: ClosedFloatingPointRange<Float>) =
        if (value.isFinite()) value.coerceIn(range) else fallback
    val b = safe(brightness, 0f, -0.4f..0.4f)
    val c = safe(contrast, 1f, 0.5f..1.8f)
    val s = safe(saturation, 1f, 0f..2f)
    val w = safe(warmth, 0f, -0.5f..0.5f)
    val t = safe(tint, 0f, -0.5f..0.5f)
    val f = safe(fade, 0f, 0f..0.5f)
    val shift = b * 255f + 128f * (1f - c)
    var result = floatArrayOf(c,0f,0f,0f,shift, 0f,c,0f,0f,shift, 0f,0f,c,0f,shift, 0f,0f,0f,1f,0f)
    val inverseSaturation = 1f - s
    val red = 0.213f * inverseSaturation
    val green = 0.715f * inverseSaturation
    val blue = 0.072f * inverseSaturation
    result = composeEditorMatrices(floatArrayOf(
        red+s,green,blue,0f,0f, red,green+s,blue,0f,0f, red,green,blue+s,0f,0f, 0f,0f,0f,1f,0f), result)
    result = composeEditorMatrices(floatArrayOf(
        1f+w*0.25f,0f,0f,0f,0f, 0f,1f+t*0.25f,0f,0f,0f, 0f,0f,1f-w*0.25f,0f,0f, 0f,0f,0f,1f,0f), result)
    if (f > 0f) result = composeEditorMatrices(floatArrayOf(
        1f-f,0f,0f,0f,255f*f, 0f,1f-f,0f,0f,255f*f, 0f,0f,1f-f,0f,255f*f, 0f,0f,0f,1f,0f), result)
    return result
}

/** A(B(pixel)), with a 4x5 affine RGBA matrix and byte-range offsets. */
internal fun composeEditorMatrices(after: FloatArray, before: FloatArray): FloatArray {
    require(after.size == 20 && before.size == 20)
    val output = FloatArray(20)
    for (row in 0..3) {
        for (column in 0..3) {
            for (k in 0..3) output[row * 5 + column] += after[row * 5 + k] * before[k * 5 + column]
        }
        output[row * 5 + 4] = after[row * 5 + 4]
        for (k in 0..3) output[row * 5 + 4] += after[row * 5 + k] * before[k * 5 + 4]
    }
    return output
}
