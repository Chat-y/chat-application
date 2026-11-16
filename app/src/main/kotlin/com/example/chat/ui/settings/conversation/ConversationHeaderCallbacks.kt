package com.example.chat.ui.settings.conversation

/** Value-only event routing. Screen remembers one instance by controller/model; callbacks never
 * capture a preference snapshot. The model still performs its fresh-snapshot transaction. */
internal class ConversationHeaderCallbacks(
    onEvent: (ActionBarEvent)->Unit,
    onPickerTarget: (ConversationHeaderColorTarget)->Unit,
    afterReset: ()->Unit = {},
) {
    val toggleAvatar:(Boolean)->Unit = { onEvent(ActionBarEvent.ToggleAvatar(it)) }
    val toggleName:(Boolean)->Unit = { onEvent(ActionBarEvent.ToggleName(it)) }
    val toggleCall:(Boolean)->Unit = { onEvent(ActionBarEvent.ToggleCall(it)) }
    val toggleStatus:(Boolean)->Unit = { onEvent(ActionBarEvent.ToggleStatus(it)) }
    val pickBar:()->Unit = { onPickerTarget(ConversationHeaderColorTarget.Bar) }
    val pickStatusBackground:()->Unit = { onPickerTarget(ConversationHeaderColorTarget.StatusBackground) }
    val pickStatusText:()->Unit = { onPickerTarget(ConversationHeaderColorTarget.StatusText) }
    val resetBar:()->Unit = { onEvent(ActionBarEvent.PickColor(ConversationHeaderColorTarget.Bar,null)) }
    val resetStatusBackground:()->Unit = { onEvent(ActionBarEvent.PickColor(ConversationHeaderColorTarget.StatusBackground,null)) }
    val resetStatusText:()->Unit = { onEvent(ActionBarEvent.PickColor(ConversationHeaderColorTarget.StatusText,null)) }
    val resetAll:()->Unit = { onEvent(ActionBarEvent.Reset);afterReset() }
}
