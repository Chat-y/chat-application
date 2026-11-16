package com.example.chat

import com.example.chat.ui.updates.StatusIdentityLabelRules
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusIdentityLabelRulesTest {
    @Test fun ordinaryScriptsAndEmojiRemainUnchanged() {
        for (label in listOf("మాయ", "माया", "مريم", "Á", "👩‍💻", "@alice", "a/b")) {
            assertEquals(label, StatusIdentityLabelRules.display(label))
        }
    }
    @Test fun explicitDirectionControlsAndBrokenUtf16BecomeVisible() {
        assertEquals("A�B�C�D�", StatusIdentityLabelRules.display("A\u202EB\u2066C\nD\uD800"))
        assertEquals("�", StatusIdentityLabelRules.display("\uDC00"))
    }
}
