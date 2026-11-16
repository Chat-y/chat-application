package com.example.chat.ui.settings.conversation

/** Exact existing nine-color support payload. No account, contact or message fields. */
internal fun moreOptionsSupportValues(state: MoreOptionsUiState): Map<String, Any?> = linkedMapOf(
    "emojiHeaderColor" to state.emojiHeaderColor,
    "emojiHeaderIconsColor" to state.emojiHeaderIconsColor,
    "emojiPickerBgColor" to state.emojiPickerBgColor,
    "hyperlinksColor" to state.hyperlinksColor,
    "infoBalloonsTextColor" to state.infoBalloonsTextColor,
    "infoBalloonsBgColor" to state.infoBalloonsBgColor,
    "groupParticipantNameColor" to state.groupParticipantNameColor,
    "voiceNotePlayingBarColor" to state.voiceNotePlayingBarColor,
    "voiceNotePlayButtonColor" to state.voiceNotePlayButtonColor,
)
