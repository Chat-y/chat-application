package com.example.chat.ui.settings.conversation

import androidx.lifecycle.ViewModel
import com.example.chat.core.theme.ConversationEntryStyles
import com.example.chat.domain.models.ConversationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class EntryStyleUiState(
    val entryStyle: String,
    val uiEntryBackgroundColor: Long?,
    val uiButtonsColor: Long?,
    val emojiButtonColor: Long?,
    val sendButtonColor: Long?,
    val micSendBgCircleColor: Long?,
    val textEntryBackgroundColor: Long?,
    val textEntryColor: Long?,
 ) {
    companion object {
        fun from(p: ConversationPreferences) = EntryStyleUiState(p.entryStyle,
            p.uiEntryBackgroundColor,
            p.uiButtonsColor,
            p.emojiButtonColor,
            p.sendButtonColor,
            p.micSendBgCircleColor,
            p.textEntryBackgroundColor,
            p.textEntryColor,
        )
    }
}
internal enum class EntryColorTarget {
    uiEntryBackgroundColor,
    uiButtonsColor,
    emojiButtonColor,
    sendButtonColor,
    micSendBgCircleColor,
    textEntryBackgroundColor,
    textEntryColor;
    fun write(p: ConversationPreferences, value: Long?): ConversationPreferences = when (this) {
        uiEntryBackgroundColor -> p.copy(uiEntryBackgroundColor = value)
        uiButtonsColor -> p.copy(uiButtonsColor = value)
        emojiButtonColor -> p.copy(emojiButtonColor = value)
        sendButtonColor -> p.copy(sendButtonColor = value)
        micSendBgCircleColor -> p.copy(micSendBgCircleColor = value)
        textEntryBackgroundColor -> p.copy(textEntryBackgroundColor = value)
        textEntryColor -> p.copy(textEntryColor = value)
    }
}
internal sealed interface EntryStyleEvent {
    data class SelectStyle(val id: String) : EntryStyleEvent
    data class PickColor(val target: EntryColorTarget, val value: Long?) : EntryStyleEvent
    data object Reset : EntryStyleEvent
    data class Undo(val noticeId: Long) : EntryStyleEvent
}
internal data class EntryStyleUiEvent(val id: Long, val appliedId: String, val label: String, val previous: String)

/** Pending notice is acknowledged after display, not replayed after ordinary recomposition.
 * An unacknowledged notice survives recreation in this ViewModel, as does its guarded undo. */
internal class EntryStyleViewModel(
    private val read: () -> ConversationPreferences,
    private val write: (ConversationPreferences) -> Unit,
    private val subscribe: (() -> Unit) -> Unit,
    private val unsubscribe: (() -> Unit) -> Unit,
    private val transaction: (((ConversationPreferences) -> ConversationPreferences) -> Unit)? = null,
    private val useStableIds: () -> Boolean = { true },
) : ViewModel() {
    private fun update(transform: (ConversationPreferences) -> ConversationPreferences) {
        val commit = transaction
        if (commit != null) commit(transform) else write(transform(read()))
    }
    private val mutableState = MutableStateFlow(EntryStyleUiState.from(read()))
    val uiState: StateFlow<EntryStyleUiState> = mutableState.asStateFlow()
    private val pending = MutableStateFlow<EntryStyleUiEvent?>(null)
    val uiEvent: StateFlow<EntryStyleUiEvent?> = pending.asStateFlow()
    private var nextId = 0L
    private val listener: () -> Unit = { mutableState.value = EntryStyleUiState.from(read()) }
    init { subscribe(listener); listener() }
    fun onEvent(event: EntryStyleEvent) {
        when (event) {
            is EntryStyleEvent.SelectStyle -> {
                val style = ConversationEntryStyles.options.firstOrNull { it.id == event.id } ?: return
                var previous = read().entryStyle
                val savedValue = conversationEntrySavedValue(style.id,useStableIds())
                update { latest -> previous = latest.entryStyle; latest.copy(entryStyle = savedValue) }
                pending.value = EntryStyleUiEvent(++nextId, savedValue, style.label, previous)
            }
            is EntryStyleEvent.PickColor -> update { latest -> event.target.write(latest, event.value) }
            EntryStyleEvent.Reset -> update { latest -> EntryColorTarget.entries.fold(latest) { state, target -> target.write(state, null) } }
            is EntryStyleEvent.Undo -> {
                val notice = pending.value?.takeIf { it.id == event.noticeId } ?: return
                update { latest -> if (latest.entryStyle == notice.appliedId) latest.copy(entryStyle = notice.previous) else latest }
                acknowledge(notice.id)
            }
        }
        listener()
    }
    fun acknowledge(id: Long) { if (pending.value?.id == id) pending.value = null }
    override fun onCleared() { unsubscribe(listener); super.onCleared() }
}
