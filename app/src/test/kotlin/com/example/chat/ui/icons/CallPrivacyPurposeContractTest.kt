package com.example.chat.ui.icons

import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures, not executed. */
class CallPrivacyPurposeContractTest {
    @Test fun privacyPurposeIsAvailableInEveryFamilyAndNotCallAudio() {
        for (theme in ChatyIconThemeId.entries) {
            assertNotNull(ChatySupplementalIcons.drawable(theme, ChatyIconId.callPrivacy))
            assertNotEquals(ChatySupplementalIcons.drawable(theme, ChatyIconId.callPrivacy),
                ChatyAssetIcons.drawable(theme, ChatyIconId.callAudio))
        }
        assertEquals(24f, ChatyGlyphVectors.of(ChatyIconId.callPrivacy).viewportWidth)
        assertTrue(ChatyBaseOutlines.shapes.containsKey(ChatyIconId.callPrivacy))
    }
}
