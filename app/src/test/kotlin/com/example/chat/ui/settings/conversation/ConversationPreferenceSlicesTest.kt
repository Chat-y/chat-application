package com.example.chat.ui.settings.conversation

import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationPreferenceSlicesTest {
    private fun assertSlice(fields: Set<String>, select: (ConversationPreferences) -> ConversationPreferences) {
        val baseline = ConversationPreferences()
        val baseMap = baseline.toMap()
        for ((key, value) in baseMap) {
            val changed: Any = when (value) {
                null -> 0xFF13579BL
                is Boolean -> !value
                is Double -> if (key == "messageTextSize") 23.0 else 0.5
                is Long -> value xor 1L
                is String -> if (key == "bubbleStyle" || key == "tickStyle") "rounded" else "sample-$key"
                // Grouped palette is now serialized beside its scalar rollback aliases.
                is Map<*, *> -> value.mapValues { 0xFF13579BL }
                else -> error("Unhandled preference type for $key")
            }
            // For scalar cases, omit the authoritative object so the scalar mutation is real.
            // val next = ConversationPreferences.fromMap(baseMap + (key to changed))
            val input = if (key in setOf("emojiHeaderColor", "emojiHeaderIconsColor", "emojiPickerBgColor"))
                baseMap - "emojiPopupStyle" else baseMap
            val next = ConversationPreferences.fromMap(input + (key to changed))
            val projected = select(next)
            if (key in fields) assertEquals(key, next.toMap()[key], projected.toMap()[key])
            else assertEquals("Unrelated key $key invalidated this screen", select(baseline), projected)
        }
    }
    @Test fun selectActionBarPreferencesHasAnIsolatedSnapshot() = assertSlice(
        setOf("actionBarColor", "contactStatusBgColor", "contactStatusTextColor", "disableContactStatus", "hideCallButton", "hideContactName", "hideProfilePicture"),
        ::selectActionBarPreferences
    )

    @Test fun selectBubbleAndTicksPreferencesHasAnIsolatedSnapshot() = assertSlice(
        setOf("bubbleStyle", "convBackgroundColor", "deletedMessageIconColor", "leftBubbleColor", "leftBubbleTimeColor", "leftChatBubbleTextColor", "makeTextSelectable", "messageTextSize", "quotedBackgroundColor", "quotedDividerColor", "quotedMessageColor", "quotedNameColor", "removeReadMore", "rightBubbleColor", "rightBubbleTimeColor", "rightChatBubbleTextColor", "tickStyle", "wallpaperType"),
        ::selectBubbleAndTicksPreferences
    )

    @Test fun selectEntryStylePreferencesHasAnIsolatedSnapshot() = assertSlice(
        setOf("emojiButtonColor", "entryStyle", "micSendBgCircleColor", "sendButtonColor", "textEntryBackgroundColor", "textEntryColor", "uiButtonsColor", "uiEntryBackgroundColor"),
        ::selectEntryStylePreferences
    )

    // Previous slice keys omitted the grouped palette: bubbleStyle, emojiHeaderColor,
    // emojiHeaderIconsColor, emojiPickerBgColor, groupParticipantNameColor, hyperlinksColor,
    // infoBalloonsBgColor, infoBalloonsTextColor, leftBubbleColor, rightBubbleColor,
    // voiceNotePlayButtonColor, voiceNotePlayingBarColor, voicePlaybackSpeed.
    @Test fun selectMoreOptionsPreferencesHasAnIsolatedSnapshot() = assertSlice(
        setOf("bubbleStyle", "emojiPopupStyle", "emojiHeaderColor", "emojiHeaderIconsColor", "emojiPickerBgColor", "groupParticipantNameColor", "hyperlinksColor", "infoBalloonsBgColor", "infoBalloonsTextColor", "leftBubbleColor", "rightBubbleColor", "voiceNotePlayButtonColor", "voiceNotePlayingBarColor", "voicePlaybackSpeed"),
        ::selectMoreOptionsPreferences
    )

}
