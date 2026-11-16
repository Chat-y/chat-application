package com.example.chat.ui.settings.conversation

import com.example.chat.ui.icons.ChatyIconId
import org.junit.Assert.*
import org.junit.Test

/** Exact row meanings for pack-aware glyph rendering. Source-only, unrun. */
class ConversationRowIconTest {
    @Test fun round1EmojiTargetsUseEmojiMeanings() {
        assertEquals(ChatyIconId.alignTop, ConversationExtraColor.emojiHeaderColor.rowIcon())
        assertEquals(ChatyIconId.emoji, ConversationExtraColor.emojiHeaderIconsColor.rowIcon())
        assertEquals(ChatyIconId.palette, ConversationExtraColor.emojiPickerBgColor.rowIcon())
    }
    @Test fun round2LinkAndNameTargetsUseTheirMeanings() {
        assertEquals(ChatyIconId.link, ConversationExtraColor.hyperlinksColor.rowIcon())
        assertEquals(ChatyIconId.groups, ConversationExtraColor.groupParticipantNameColor.rowIcon())
    }
    @Test fun round3SystemTargetsUseTheirMeanings() {
        assertEquals(ChatyIconId.info, ConversationExtraColor.infoBalloonsTextColor.rowIcon())
        assertEquals(ChatyIconId.markUnread, ConversationExtraColor.infoBalloonsBgColor.rowIcon())
    }
    @Test fun round4VoiceTargetsUseTheirMeanings() {
        assertEquals(ChatyIconId.waveform, ConversationExtraColor.voiceNotePlayingBarColor.rowIcon())
        assertEquals(ChatyIconId.playCircle, ConversationExtraColor.voiceNotePlayButtonColor.rowIcon())
    }
    @Test fun round5AllNineTargetsHaveDistinctRoleMappings() {
        assertEquals(9, ConversationExtraColor.entries.map { it.rowIcon() }.distinct().size)
    }
}
