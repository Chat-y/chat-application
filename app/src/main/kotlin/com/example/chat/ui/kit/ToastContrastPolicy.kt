package com.example.chat.ui.kit

/** Presentation-only policy. Never rewrites the user's saved color. Theme tokens are supplied by caller. */
object ToastContrastPolicy {
    const val minimumTextRatio = 4.5
    fun readable(background: Long, requested: Long?, themeForegrounds: List<Long>): Long {
        require(themeForegrounds.isNotEmpty())
        if (requested != null && ColorPickerLogic.contrastRatio(background, requested) >= minimumTextRatio) return requested
        return themeForegrounds.maxBy { ColorPickerLogic.contrastRatio(background, it) }
    }
    fun needsFallback(background: Long, requested: Long?): Boolean = requested != null &&
        ColorPickerLogic.contrastRatio(background, requested) < minimumTextRatio
}
