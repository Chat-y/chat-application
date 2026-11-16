package com.example.chat.ui.settings.conversation

import org.junit.Assert.*
import org.junit.Test

/** Simple-mode source grouping contract. Existing values are never rewritten by this projection. */
class ConversationSimpleExtrasTest {
    @Test fun modeHasExactlyThreeChoices() { assertEquals(3, ConversationSimpleExtraGroup.entries.size) }
    @Test fun emojiKeepsItsThreeTargets() { assertEquals(3, ConversationSimpleExtraGroup.Emoji.targets.size) }
    @Test fun linksDoesNotSilentlyChangeGroupNameColors() { assertEquals(setOf(ConversationExtraColor.hyperlinksColor), ConversationSimpleExtraGroup.Links.targets) }
    @Test fun voiceKeepsBothExistingTargets() { assertEquals(setOf(ConversationExtraColor.voiceNotePlayingBarColor, ConversationExtraColor.voiceNotePlayButtonColor), ConversationSimpleExtraGroup.Voice.targets) }
    @Test fun advancedOnlyTargetsAreRetainedOutsideSimpleProjection() {
        val simple = ConversationSimpleExtraGroup.entries.flatMap { it.targets }.toSet()
        assertEquals(setOf(ConversationExtraColor.groupParticipantNameColor, ConversationExtraColor.infoBalloonsTextColor,
            ConversationExtraColor.infoBalloonsBgColor), ConversationExtraColor.entries.toSet() - simple)
    }
}
