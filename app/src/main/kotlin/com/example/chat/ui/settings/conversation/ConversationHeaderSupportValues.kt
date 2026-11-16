package com.example.chat.ui.settings.conversation

/** Exact existing seven-value support payload, never contact or message data. */
internal fun actionBarSupportValues(state: ActionBarUiState): Map<String, Any?> = linkedMapOf(
    "actionBarColor" to state.actionBarColor,
    "hideProfilePicture" to state.hideProfilePicture,
    "hideContactName" to state.hideContactName,
    "hideCallButton" to state.hideCallButton,
    "disableContactStatus" to state.disableContactStatus,
    "contactStatusBgColor" to state.contactStatusBgColor,
    "contactStatusTextColor" to state.contactStatusTextColor,
)
