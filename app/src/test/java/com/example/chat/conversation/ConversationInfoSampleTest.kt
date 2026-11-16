package com.example.chat.ui.settings.conversation

import org.junit.Assert.*
import org.junit.Test

/** Display-only sample contract; no security state or events. Source-only, unrun. */
class ConversationInfoSampleTest {
    @Test fun addsExactlyTheTwoMissingKinds() { assertEquals(2,ConversationInfoSample.entries.size) }
    @Test fun encryptionHasDedicatedResource() { assertEquals(com.example.chat.R.string.conv_proposal_sample_encryption,ConversationInfoSample.Encryption.stringId) }
    @Test fun securityCodeHasDedicatedResource() { assertEquals(com.example.chat.R.string.conv_proposal_sample_security_code,ConversationInfoSample.SecurityCodeChange.stringId) }
    @Test fun tagsAreDistinctFromDateAndMissedCall() {
        assertEquals(2,ConversationInfoSample.entries.map { it.testTag }.toSet().size)
        assertTrue(ConversationInfoSample.entries.none { it.testTag in setOf("conversation-extra-date-preview","conversation-extra-missed-call-preview") })
    }
    @Test fun resourcesAreDifferentForTheTwoSampleDisclaimers() { assertNotEquals(ConversationInfoSample.Encryption.stringId,ConversationInfoSample.SecurityCodeChange.stringId) }
}
