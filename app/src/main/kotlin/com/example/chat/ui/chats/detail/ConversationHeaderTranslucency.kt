package com.example.chat.ui.chats.detail
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic

internal data class ConversationHeaderTranslucency(val percent: Int,val foreground: Long,val minimumContrast: Double)
internal fun conversationHeaderComposite(header: Long,wallpaper: Long,percent: Int): Long {
    val alpha=percent.coerceIn(40,100)/100.0
    fun channel(shift:Int):Long=((((header shr shift) and 255)*alpha + ((wallpaper shr shift) and 255)*(1-alpha)).toLong()).coerceIn(0,255)
    return (header and (255L shl 24)) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
/** Every wallpaper RGB component lies between the existing monochrome extreme tokens.
 * Use a luminance interval, not just two endpoint contrast scores (mid-interval text would be1:1).
 * Raise opacity, without rewriting the preference, until title/icons/no-pill status have a4.55 margin. */
internal fun conversationHeaderMinimumContrast(header: Long,foreground: Long,percent: Int): Double {
    val dark=conversationHeaderComposite(header,ThemePresets.monochromeLight.accentColor,percent)
    val light=conversationHeaderComposite(header,ThemePresets.monochromeDark.accentColor,percent)
    val low=ColorPickerLogic.luminance(dark);val high=ColorPickerLogic.luminance(light);val fg=ColorPickerLogic.luminance(foreground)
    if (fg in low..high) return 1.0
    return minOf(ColorPickerLogic.contrastRatio(foreground,dark),ColorPickerLogic.contrastRatio(foreground,light))
}
internal fun conversationHeaderTranslucency(header: Long,requested: Int): ConversationHeaderTranslucency {
    val candidates=listOf(ThemePresets.monochromeLight.accentColor,ThemePresets.monochromeDark.accentColor)
    for (percent in requested.coerceIn(40,100)..100) {
        val foreground=candidates.maxBy { conversationHeaderMinimumContrast(header,it,percent) }
        val minimum=conversationHeaderMinimumContrast(header,foreground,percent)
        if (minimum>=4.55) return ConversationHeaderTranslucency(percent,foreground,minimum)
    }
    val foreground=candidates.maxBy { ColorPickerLogic.contrastRatio(it,header) }
    return ConversationHeaderTranslucency(100,foreground,ColorPickerLogic.contrastRatio(foreground,header))
}
