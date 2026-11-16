package com.example.chat.ui.settings.conversation

import androidx.lifecycle.ViewModel
import com.example.chat.domain.models.ConversationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class MoreOptionsUiState(
    val emojiHeaderColor: Long?,
    val emojiHeaderIconsColor: Long?,
    val emojiPickerBgColor: Long?,
    val hyperlinksColor: Long?,
    val infoBalloonsTextColor: Long?,
    val infoBalloonsBgColor: Long?,
    val groupParticipantNameColor: Long?,
    val voiceNotePlayingBarColor: Long?,
    val voiceNotePlayButtonColor: Long?,
    val leftBubbleColor: Long?,
    val rightBubbleColor: Long?,
    val bubbleStyle: String, val voicePlaybackSpeed: Double,
) {
    companion object {
        fun from(p: ConversationPreferences) = MoreOptionsUiState(
            emojiHeaderColor = p.emojiHeaderColor,
            emojiHeaderIconsColor = p.emojiHeaderIconsColor,
            emojiPickerBgColor = p.emojiPickerBgColor,
            hyperlinksColor = p.hyperlinksColor,
            infoBalloonsTextColor = p.infoBalloonsTextColor,
            infoBalloonsBgColor = p.infoBalloonsBgColor,
            groupParticipantNameColor = p.groupParticipantNameColor,
            voiceNotePlayingBarColor = p.voiceNotePlayingBarColor,
            voiceNotePlayButtonColor = p.voiceNotePlayButtonColor,
            leftBubbleColor = p.leftBubbleColor,
            rightBubbleColor = p.rightBubbleColor,
            bubbleStyle = p.bubbleStyle, voicePlaybackSpeed = p.voicePlaybackSpeed,
        )
    }
}

internal sealed interface MoreOptionsEvent {
    data class ColorChanged(val target: ConversationExtraColor, val value: Long?) : MoreOptionsEvent
    data class ColorsChanged(val values: Map<ConversationExtraColor, Long?>) : MoreOptionsEvent
    data object ResetEmojiStyle : MoreOptionsEvent
    data object ResetColors : MoreOptionsEvent
}

internal fun reduceMoreOptions(p: ConversationPreferences, event: MoreOptionsEvent): ConversationPreferences = when (event) {
    is MoreOptionsEvent.ColorChanged -> event.target.write(p, event.value)
    is MoreOptionsEvent.ColorsChanged -> event.values.entries.fold(p) { state, (target, value) -> target.write(state, value) }
    MoreOptionsEvent.ResetEmojiStyle -> p.withEmojiPopupStyle(com.example.chat.domain.models.EmojiPopupStyle())
    MoreOptionsEvent.ResetColors -> ConversationExtraColor.entries.fold(p) { state, target -> target.write(state, null) }
}

/** UI adapter only, with exact-target events; no storage/controller changes. */
internal class MoreOptionsViewModel(
    private val read: () -> ConversationPreferences,
    private val write: (ConversationPreferences) -> Unit,
    private val subscribe: (() -> Unit) -> Unit,
    private val unsubscribe: (() -> Unit) -> Unit,
    private val transaction: (((ConversationPreferences) -> ConversationPreferences) -> Unit)? = null,
) : ViewModel() {
    private fun update(transform: (ConversationPreferences) -> ConversationPreferences) {
        val commit = transaction
        if (commit != null) commit(transform) else write(transform(read()))
    }
    private val mutableState = MutableStateFlow(MoreOptionsUiState.from(read()))
    val uiState: StateFlow<MoreOptionsUiState> = mutableState.asStateFlow()
    private val listener: () -> Unit = { mutableState.value = MoreOptionsUiState.from(read()) }
    init { subscribe(listener); listener() }
    fun onEvent(event: MoreOptionsEvent) { update { reduceMoreOptions(it, event) }; listener() }
    override fun onCleared() { unsubscribe(listener); super.onCleared() }
}
