package com.example.chat.ui.settings.conversation
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test
class ConversationHeaderCallbacksTest {
    @Test fun toggleHandlersRouteExactEvents() { val events=mutableListOf<ActionBarEvent>();val c=ConversationHeaderCallbacks(events::add,{});c.toggleAvatar(true);c.toggleName(false);c.toggleCall(true);c.toggleStatus(false);assertEquals(listOf(ActionBarEvent.ToggleAvatar(true),ActionBarEvent.ToggleName(false),ActionBarEvent.ToggleCall(true),ActionBarEvent.ToggleStatus(false)),events) }
    @Test fun pickerHandlersRouteExactTargets() { val targets=mutableListOf<ConversationHeaderColorTarget>();val c=ConversationHeaderCallbacks({},targets::add);c.pickBar();c.pickStatusBackground();c.pickStatusText();assertEquals(ConversationHeaderColorTarget.entries.toSet(),targets.toSet()) }
    @Test fun colorResetsNeverClearOtherFields() { var value=ConversationPreferences(actionBarColor=1L,contactStatusBgColor=2L,contactStatusTextColor=3L,hyperlinksColor=4L);val c=ConversationHeaderCallbacks({ value=reduceActionBar(value,it) },{});c.resetBar();assertNull(value.actionBarColor);assertEquals(2L,value.contactStatusBgColor);assertEquals(4L,value.hyperlinksColor) }
    @Test fun callbacksUseTheFreshStateAtInvocationNotConstruction() { var value=ConversationPreferences();val c=ConversationHeaderCallbacks({ value=reduceActionBar(value,it) },{});value=value.copy(hyperlinksColor=99L,entryStyle="Reference 2");c.toggleName(true);assertTrue(value.hideContactName);assertEquals(99L,value.hyperlinksColor);assertEquals("Reference 2",value.entryStyle) }
    @Test fun handlerReferencesStayTheSameAndResetRunsOrderedFollowup() { val events=mutableListOf<String>();val c=ConversationHeaderCallbacks({events.add("event")},{},{events.add("after")});assertSame(c.toggleName,c.toggleName);assertSame(c.pickBar,c.pickBar);c.resetAll();assertEquals(listOf("event","after"),events) }
}
