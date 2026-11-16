package com.example.chat.ui.calls

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class CallAvatarColorParsingTest {
    @Test fun documentedHexFormatsResolveToTheSameColor() {
        val expected = Color(0xFF6366F1L)
        for (raw in listOf("0xFF6366F1", "#6366F1", "FF6366F1")) {
            assertEquals(raw, expected, parseCallAvatarColor(raw))
        }
    }

    @Test fun surroundingWhitespaceAndCaseDoNotChangeTheColor() {
        val expected = Color(0xFF6366F1L)
        for (raw in listOf("  #6366f1  ", "\t0Xff6366f1\n", " ff6366f1 ")) {
            assertEquals(raw, expected, parseCallAvatarColor(raw))
        }
    }

    @Test fun rgbValuesReceiveAnOpaqueAlphaIncludingBlackAndWhite() {
        for (rgb in listOf(0x000000L, 0x123456L, 0xFFFFFFL)) {
            val raw = rgb.toString(16).padStart(6, '0')
            assertEquals(raw, Color(0xFF000000L or rgb), parseCallAvatarColor(raw))
        }
    }

    @Test fun explicitNonzeroAlphaIsPreserved() {
        val expected = Color(0x806366F1L)
        for (raw in listOf("0x806366F1", "#806366F1", "806366F1")) {
            assertEquals(raw, expected, parseCallAvatarColor(raw))
        }
    }

    @Test fun absentBlankAndMalformedHexUseTheDocumentedIndigoFallback() {
        val expected = Color(0xFF6366F1L)
        for (raw in listOf(null, "", " \t\n", "not-a-color", "#GGHHII", "0x")) {
            assertEquals(raw, expected, parseCallAvatarColor(raw))
        }
    }
}
