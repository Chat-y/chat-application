package com.example.chat

import com.example.chat.ui.chats.newchat.NewChatPresentationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NewChatPresentationRulesTest {
    @org.junit.Test fun titleClampDoesNotSplitSurrogatePairs() {
        org.junit.Assert.assertEquals("a", NewChatPresentationRules.clampTitle("a😀b", 2))
        org.junit.Assert.assertEquals("a😀", NewChatPresentationRules.clampTitle("a😀b", 3))
        org.junit.Assert.assertEquals("", NewChatPresentationRules.clampTitle("😀", 1))
    }
    @org.junit.Test fun titleClampKeepsSingleLineAndPositiveLimit() {
        org.junit.Assert.assertEquals("a  b", NewChatPresentationRules.clampTitle("a\r\nb", 10))
        org.junit.Assert.assertEquals("a", NewChatPresentationRules.clampTitle("abc", 0))
    }

    @Test fun emptyConfigurationRestoresAllRequiredSections() {
        assertEquals(listOf("search", "selected", "contacts"), NewChatPresentationRules.sections(emptyList()))
    }
    @Test fun preservesKnownOrderWithoutDuplicatesAndIgnoresUnknownKeys() {
        assertEquals(listOf("contacts", "search", "selected"),
            NewChatPresentationRules.sections(listOf("contacts", "unknown", "contacts", "search")))
    }
    @Test fun publicErrorsNeverContainDiagnosticPayloads() {
        val sensitive = "fixture bearer/token someone@example.invalid response body"
        for (error in listOf(Exception(sensitive), java.io.IOException(sensitive), SecurityException(sensitive))) {
            assertFalse(NewChatPresentationRules.publicError(error).contains(sensitive))
        }
    }
    @Test fun distinguishesConnectionAndPermissionFailures() {
        assertEquals("Unable to connect. Check your connection and try again.",
            NewChatPresentationRules.publicError(java.io.IOException("fixture")))
        assertEquals("This action is not permitted. Check your account access.",
            NewChatPresentationRules.publicError(SecurityException("fixture")))
    }
    @Test fun identityDisplayMakesDirectionOverridesAndControlsVisible() {
        assertEquals("safe�exe", NewChatPresentationRules.identityText("safe\u202Eexe"))
        assertEquals("a�b�c", NewChatPresentationRules.identityText("a\u0000b\nc"))
        assertEquals("�name�", NewChatPresentationRules.identityText("\u2066name\u2069"))
    }
    @Test fun identityDisplayPreservesNaturalScriptsAndEmoji() {
        for (text in listOf("మాయ", "العربية", "किताब", "résumé", "👨‍👩‍👧‍👦")) {
            assertEquals(text, NewChatPresentationRules.identityText(text))
        }
    }

}
