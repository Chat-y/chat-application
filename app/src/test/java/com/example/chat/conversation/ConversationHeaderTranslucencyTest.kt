package com.example.chat.ui.chats.detail
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.*
import org.junit.Test
class ConversationHeaderTranslucencyTest {
    @Test fun defaultAndRollbackAreOpaque() { assertEquals(100,ConversationHeaderAppearance().alphaPercent);assertEquals(100,ConversationHeaderAppearance(alphaPercent=40).effective().alphaPercent) }
    @Test fun malformedOpacityNeverEnablesTransparency() { listOf<Any>(39,101,Double.NaN,40.5,"40").forEach { assertEquals(100,ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.ALPHA_PERCENT to it)).alphaPercent) } }
    @Test fun everyThemeAndRequestedStepGetsAtLeastAAWorstCaseContrast() { ThemePresets.all.forEach { t -> (40..100 step 10).forEach { requested -> val p=conversationHeaderTranslucency(t.surfaceColor,requested);assertTrue(p.percent in requested..100);assertTrue(p.minimumContrast>=4.5) } } }
    @Test fun monochromeEndpointsActuallySpanAllChannels() { val dark=ThemePresets.monochromeLight.accentColor;val light=ThemePresets.monochromeDark.accentColor;listOf(0,8,16).forEach { shift->assertEquals(0L,(dark shr shift) and 255);assertEquals(255L,(light shr shift) and 255) } }
    @Test fun representativeGrayWallpapersMeetTheResolvedForegroundCheck() { val header=ThemePresets.paper.surfaceColor;val p=conversationHeaderTranslucency(header,40);(0..255 step 15).forEach { channel->val wallpaper=(header and (255L shl 24)) or (channel.toLong() shl 16) or (channel.toLong() shl 8) or channel.toLong();assertTrue(ColorPickerLogic.contrastRatio(p.foreground,conversationHeaderComposite(header,wallpaper,p.percent))>=4.5) } }
}
