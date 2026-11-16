package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.*
import org.junit.Test

/** Global header color choices must not bypass existing per-chat theme color ownership. Unrun. */
class ConversationHeaderScopedThemeTest {
    private val stored = ConversationHeaderAppearance(optionsEnabled = true,iconsColor = ThemePresets.paper.accentColor,
        autoContrast = true,centeredTitle = true,nameWeight = "regular",avatarSizeDp = 44)
    @Test fun globalThemeKeepsExplicitGlobalColorChoice() { assertEquals(stored,stored.forScopedTheme(false)) }
    @Test fun scopedThemeClearsGlobalIconTintOnlyInResolvedValue() { assertNull(stored.forScopedTheme(true).iconsColor);assertNotNull(stored.iconsColor) }
    @Test fun scopedThemeDisablesGlobalAutoContrast() { assertFalse(stored.forScopedTheme(true).autoContrast);assertTrue(stored.autoContrast) }
    @Test fun scopedThemeKeepsNonColorLayoutOptions() {
        val resolved = stored.forScopedTheme(true)
        assertTrue(resolved.centeredTitle);assertEquals("regular",resolved.nameWeight);assertEquals(44,resolved.avatarSizeDp)
    }
    @Test fun scopedThemeDoesNotEnableDisabledMasterGate() { assertFalse(ConversationHeaderAppearance().forScopedTheme(true).effective().optionsEnabled) }
}
