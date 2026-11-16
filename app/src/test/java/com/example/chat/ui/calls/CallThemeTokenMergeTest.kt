package com.example.chat.ui.calls

import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.calls.bundles.CallBundleId
import com.example.chat.ui.calls.bundles.CallBundleRegistry
import com.example.chat.ui.theme.AppColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CallThemeTokenMergeTest {
    private fun colors(isDark: Boolean) = AppColors(
        ThemeConfig(
            id = "call-token-test",
            name = "Call token test",
            isDark = isDark,
            accentColor = 0xFF123456L,
            backgroundColor = 0xFF234567L,
            surfaceColor = 0xFF345678L,
            cardColor = 0xFF456789L,
            primaryTextColor = 0xFF56789AL,
            secondaryTextColor = 0xFF6789ABL,
            outgoingBubbleColor = 0xFF789ABCL,
            incomingBubbleColor = 0xFF89ABCDL,
            outgoingTextColor = 0xFF9ABCDEL,
            incomingTextColor = 0xFFABCDEFL,
            linkColor = 0xFFBCDEF0L,
            dangerColor = 0xFFCDEF01L,
            successColor = 0xFFDEF012L,
        ),
    )

    @Test fun userThemeSuppliesEveryMergedColorForEveryBundle() {
        for (isDark in listOf(false, true)) {
            val c = colors(isDark)
            for (id in CallBundleId.entries) {
                val merged = themedCallBundleTokens(id, c)
                assertEquals("$id primaryAccent", c.accent, merged.primaryAccent)
                assertEquals("$id acceptColor", c.success, merged.acceptColor)
                assertEquals("$id declineColor", c.error, merged.declineColor)
                assertEquals("$id surfaceBackground", c.background, merged.surfaceBackground)
                assertEquals("$id cardBackground", c.surface, merged.cardBackground)
                assertEquals("$id primaryText", c.foreground, merged.primaryText)
                assertEquals("$id secondaryText", c.foregroundSecondary, merged.secondaryText)
                assertEquals("$id controlBarBackground", c.surface, merged.controlBarBackground)
            }
        }
    }

    @Test fun mergingPreservesBundleIdentityGeometryAndRegistryValues() {
        val c = colors(isDark = false)
        for (id in CallBundleId.entries) {
            val original = CallBundleRegistry.getTokens(id)
            val snapshot = original.copy()
            val merged = themedCallBundleTokens(id, c)
            assertEquals(id, merged.bundleId)
            assertEquals("$id pill radius", original.pillCornerRadiusDp, merged.pillCornerRadiusDp)
            assertEquals("$id card radius", original.cardCornerRadiusDp, merged.cardCornerRadiusDp)
            assertEquals("$id icon size", original.iconButtonSizeDp, merged.iconButtonSizeDp)
            assertEquals("$id glass", original.isGlassmorphic, merged.isGlassmorphic)
            assertEquals("$id registry unchanged", snapshot, CallBundleRegistry.getTokens(id))
        }
    }
}
