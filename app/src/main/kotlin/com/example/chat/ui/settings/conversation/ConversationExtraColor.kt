package com.example.chat.ui.settings.conversation

import com.example.chat.domain.models.ConversationPreferences

/** Exact nine extra-colour targets. Copying changes only the explicitly selected field. */
internal enum class ConversationExtraColor(val key: String) {
    emojiHeaderColor("emojiHeaderColor"),
    emojiHeaderIconsColor("emojiHeaderIconsColor"),
    emojiPickerBgColor("emojiPickerBgColor"),
    hyperlinksColor("hyperlinksColor"),
    infoBalloonsTextColor("infoBalloonsTextColor"),
    infoBalloonsBgColor("infoBalloonsBgColor"),
    groupParticipantNameColor("groupParticipantNameColor"),
    voiceNotePlayingBarColor("voiceNotePlayingBarColor"),
    voiceNotePlayButtonColor("voiceNotePlayButtonColor");
    fun read(state: ConversationPreferences): Long? = when (this) {
        emojiHeaderColor -> state.emojiHeaderColor
        emojiHeaderIconsColor -> state.emojiHeaderIconsColor
        emojiPickerBgColor -> state.emojiPickerBgColor
        hyperlinksColor -> state.hyperlinksColor
        infoBalloonsTextColor -> state.infoBalloonsTextColor
        infoBalloonsBgColor -> state.infoBalloonsBgColor
        groupParticipantNameColor -> state.groupParticipantNameColor
        voiceNotePlayingBarColor -> state.voiceNotePlayingBarColor
        voiceNotePlayButtonColor -> state.voiceNotePlayButtonColor
    }
    fun write(state: ConversationPreferences, value: Long?): ConversationPreferences = when (this) {
        emojiHeaderColor -> state.copy(emojiHeaderColor = value)
        emojiHeaderIconsColor -> state.copy(emojiHeaderIconsColor = value)
        emojiPickerBgColor -> state.copy(emojiPickerBgColor = value)
        hyperlinksColor -> state.copy(hyperlinksColor = value)
        infoBalloonsTextColor -> state.copy(infoBalloonsTextColor = value)
        infoBalloonsBgColor -> state.copy(infoBalloonsBgColor = value)
        groupParticipantNameColor -> state.copy(groupParticipantNameColor = value)
        voiceNotePlayingBarColor -> state.copy(voiceNotePlayingBarColor = value)
        voiceNotePlayButtonColor -> state.copy(voiceNotePlayButtonColor = value)
    }
}

/** Pack-aware row meanings. Kept beside exact preference targets, not inferred from vector names. */
internal fun ConversationExtraColor.rowIcon(): com.example.chat.ui.icons.ChatyIconId = when (this) {
    ConversationExtraColor.emojiHeaderColor -> com.example.chat.ui.icons.ChatyIconId.alignTop
    ConversationExtraColor.emojiHeaderIconsColor -> com.example.chat.ui.icons.ChatyIconId.emoji
    ConversationExtraColor.emojiPickerBgColor -> com.example.chat.ui.icons.ChatyIconId.palette
    ConversationExtraColor.hyperlinksColor -> com.example.chat.ui.icons.ChatyIconId.link
    ConversationExtraColor.groupParticipantNameColor -> com.example.chat.ui.icons.ChatyIconId.groups
    ConversationExtraColor.infoBalloonsTextColor -> com.example.chat.ui.icons.ChatyIconId.info
    ConversationExtraColor.infoBalloonsBgColor -> com.example.chat.ui.icons.ChatyIconId.markUnread
    ConversationExtraColor.voiceNotePlayingBarColor -> com.example.chat.ui.icons.ChatyIconId.waveform
    ConversationExtraColor.voiceNotePlayButtonColor -> com.example.chat.ui.icons.ChatyIconId.playCircle
}
