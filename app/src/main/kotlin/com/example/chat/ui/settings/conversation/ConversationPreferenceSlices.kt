package com.example.chat.ui.settings.conversation

import com.example.chat.domain.models.ConversationPreferences

/** UI-only value slices. Never write a slice back as a full preference object. */
internal fun selectActionBarPreferences(source: ConversationPreferences): ConversationPreferences =
    ConversationPreferences().copy(
        actionBarColor = source.actionBarColor,
        contactStatusBgColor = source.contactStatusBgColor,
        contactStatusTextColor = source.contactStatusTextColor,
        disableContactStatus = source.disableContactStatus,
        hideCallButton = source.hideCallButton,
        hideContactName = source.hideContactName,
        hideProfilePicture = source.hideProfilePicture,
    )

internal fun selectBubbleAndTicksPreferences(source: ConversationPreferences): ConversationPreferences =
    ConversationPreferences().copy(
        bubbleStyle = source.bubbleStyle,
        convBackgroundColor = source.convBackgroundColor,
        deletedMessageIconColor = source.deletedMessageIconColor,
        leftBubbleColor = source.leftBubbleColor,
        leftBubbleTimeColor = source.leftBubbleTimeColor,
        leftChatBubbleTextColor = source.leftChatBubbleTextColor,
        makeTextSelectable = source.makeTextSelectable,
        messageTextSize = source.messageTextSize,
        quotedBackgroundColor = source.quotedBackgroundColor,
        quotedDividerColor = source.quotedDividerColor,
        quotedMessageColor = source.quotedMessageColor,
        quotedNameColor = source.quotedNameColor,
        removeReadMore = source.removeReadMore,
        rightBubbleColor = source.rightBubbleColor,
        rightBubbleTimeColor = source.rightBubbleTimeColor,
        rightChatBubbleTextColor = source.rightChatBubbleTextColor,
        tickStyle = source.tickStyle,
        wallpaperType = source.wallpaperType,
    )

internal fun selectEntryStylePreferences(source: ConversationPreferences): ConversationPreferences =
    ConversationPreferences().copy(
        emojiButtonColor = source.emojiButtonColor,
        entryStyle = source.entryStyle,
        micSendBgCircleColor = source.micSendBgCircleColor,
        sendButtonColor = source.sendButtonColor,
        textEntryBackgroundColor = source.textEntryBackgroundColor,
        textEntryColor = source.textEntryColor,
        uiButtonsColor = source.uiButtonsColor,
        uiEntryBackgroundColor = source.uiEntryBackgroundColor,
    )

internal fun selectMoreOptionsPreferences(source: ConversationPreferences): ConversationPreferences =
    ConversationPreferences().copy(
        bubbleStyle = source.bubbleStyle,
        emojiHeaderColor = source.emojiHeaderColor,
        emojiHeaderIconsColor = source.emojiHeaderIconsColor,
        emojiPickerBgColor = source.emojiPickerBgColor,
        groupParticipantNameColor = source.groupParticipantNameColor,
        hyperlinksColor = source.hyperlinksColor,
        infoBalloonsBgColor = source.infoBalloonsBgColor,
        infoBalloonsTextColor = source.infoBalloonsTextColor,
        leftBubbleColor = source.leftBubbleColor,
        rightBubbleColor = source.rightBubbleColor,
        voiceNotePlayButtonColor = source.voiceNotePlayButtonColor,
        voiceNotePlayingBarColor = source.voiceNotePlayingBarColor,
        voicePlaybackSpeed = source.voicePlaybackSpeed,
    )
