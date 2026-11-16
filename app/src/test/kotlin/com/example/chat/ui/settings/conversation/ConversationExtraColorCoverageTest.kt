package com.example.chat.ui.settings.conversation

import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the same typed writers used by actual MoreOptions row descriptors. Source only, unrun. */
class ConversationExtraColorCoverageTest {
    private val base = ConversationPreferences(entryStyle = "stock", hideCallButton = true)
    @Test fun round1ExactlyNineUniqueTargetsAreRegistered() {
        assertEquals(9, ConversationExtraColor.entries.size)
        assertEquals(9, ConversationExtraColor.entries.map { it.key }.distinct().size)
    }
    @Test fun round2EveryWriterChangesOnlyItsOwnStoredKey() {
        ConversationExtraColor.entries.forEach { target ->
            val changed = target.write(base, 123L).toMap()
            assertEquals(123L, changed[target.key])
            // Scalar emoji edits also update their authoritative grouped representation.
            // base.toMap().forEach { (key, value) -> if (key != target.key) assertEquals(key, value, changed[key]) }
            val groupedField = when (target) {
                ConversationExtraColor.emojiHeaderColor -> "header"
                ConversationExtraColor.emojiHeaderIconsColor -> "icons"
                ConversationExtraColor.emojiPickerBgColor -> "background"
                else -> null
            }
            base.toMap().forEach { (key, value) ->
                if (key == "emojiPopupStyle" && groupedField != null) {
                    val expected = base.emojiPopupStyle.toMap() + (groupedField to 123L)
                    assertEquals(key, expected, changed[key])
                } else if (key != target.key) assertEquals(key, value, changed[key])
            }
        }
    }
    @Test fun round3EveryReaderMatchesItsWriter() {
        ConversationExtraColor.entries.forEach { assertEquals(456L, it.read(it.write(base, 456L))) }
    }
    @Test fun round4ResetRestoresOnlyTheSelectedDefault() {
        ConversationExtraColor.entries.forEach { assertEquals(base, it.write(it.write(base, 789L), null)) }
    }
    @Test fun round5AllOtherConversationColourKeysHaveAnExplicitExemption() {
        val otherScreens = setOf("quickContactBgColor", "quickContactTextColor", "actionBarColor", "contactStatusBgColor", "contactStatusTextColor", "convBackgroundColor", "rightBubbleColor", "rightChatBubbleTextColor", "rightBubbleTimeColor", "leftBubbleColor", "leftChatBubbleTextColor", "leftBubbleTimeColor", "deletedMessageIconColor", "quotedDividerColor", "quotedNameColor", "quotedMessageColor", "quotedBackgroundColor", "uiEntryBackgroundColor", "uiButtonsColor", "emojiButtonColor", "sendButtonColor", "micSendBgCircleColor", "textEntryBackgroundColor", "textEntryColor")
        val actualColours = base.toMap().keys.filter { it.endsWith("Color") }.toSet()
        val classified = otherScreens + ConversationExtraColor.entries.map { it.key }
        assertEquals(actualColours, classified)
    }
}
