package com.example.chat.ui.settings.conversation

import androidx.lifecycle.ViewModel
import com.example.chat.domain.models.ConversationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Header-owned values only. Shared by rows, summary and production-header preview. */
internal data class ActionBarUiState(
    val actionBarColor: Long?, val contactStatusBgColor: Long?, val contactStatusTextColor: Long?,
    val hideProfilePicture: Boolean, val hideContactName: Boolean,
    val hideCallButton: Boolean, val disableContactStatus: Boolean,
) {
    companion object {
        fun from(p: ConversationPreferences) = ActionBarUiState(p.actionBarColor, p.contactStatusBgColor,
            p.contactStatusTextColor, p.hideProfilePicture, p.hideContactName, p.hideCallButton, p.disableContactStatus)
    }
}

internal sealed interface ActionBarEvent {
    data class ToggleAvatar(val hidden: Boolean) : ActionBarEvent
    data class ToggleName(val hidden: Boolean) : ActionBarEvent
    data class ToggleCall(val hidden: Boolean) : ActionBarEvent
    /** Local viewer display only; never changes account presence publication. */
    data class ToggleStatus(val hidden: Boolean) : ActionBarEvent
    data class PickColor(val target: ConversationHeaderColorTarget, val value: Long?) : ActionBarEvent
    data object Reset : ActionBarEvent
}

/** Pure fresh-snapshot reducer. Never writes a header slice over unrelated preferences. */
internal fun reduceActionBar(p: ConversationPreferences, event: ActionBarEvent): ConversationPreferences = when (event) {
    is ActionBarEvent.ToggleAvatar -> p.copy(hideProfilePicture = event.hidden)
    is ActionBarEvent.ToggleName -> p.copy(hideContactName = event.hidden)
    is ActionBarEvent.ToggleCall -> p.copy(hideCallButton = event.hidden)
    is ActionBarEvent.ToggleStatus -> p.copy(disableContactStatus = event.hidden)
    is ActionBarEvent.PickColor -> event.target.write(p, event.value)
    ActionBarEvent.Reset -> p.copy(actionBarColor = null, hideProfilePicture = false,
        hideContactName = false, hideCallButton = false, disableContactStatus = false,
        contactStatusBgColor = null, contactStatusTextColor = null)
}

/** UI adapter only. Persistence and controller subscription APIs are unchanged. */
internal class ActionBarViewModel(
    private val read: () -> ConversationPreferences,
    private val write: (ConversationPreferences) -> Unit,
    private val subscribe: (() -> Unit) -> Unit,
    private val unsubscribe: (() -> Unit) -> Unit,
    private val transaction: (((ConversationPreferences) -> ConversationPreferences) -> Unit)? = null,
    private val enableDebugUsage: Boolean = false,
) : ViewModel() {
    private fun update(transform: (ConversationPreferences) -> ConversationPreferences) {
        val commit = transaction
        if (commit != null) commit(transform) else write(transform(read()))
    }
    private val usage = MutableStateFlow<Map<String, Int>>(emptyMap())
    val debugUsage: StateFlow<Map<String, Int>> = usage.asStateFlow()
    private val mutableState = MutableStateFlow(ActionBarUiState.from(read()))
    val uiState: StateFlow<ActionBarUiState> = mutableState.asStateFlow()
    private val listener: () -> Unit = { mutableState.value = ActionBarUiState.from(read()) }
    init { subscribe(listener); listener() }
    fun onEvent(event: ActionBarEvent) {
        update { reduceActionBar(it, event) }
        if (enableDebugUsage) usage.value = recordHeaderDebugUsage(usage.value, event)
        listener()
    }
    override fun onCleared() { unsubscribe(listener); super.onCleared() }
}
