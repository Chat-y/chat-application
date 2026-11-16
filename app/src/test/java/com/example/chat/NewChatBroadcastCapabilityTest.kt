package com.example.chat

import com.example.chat.ui.chats.newchat.NewChatRules
import org.junit.Assert.*
import org.junit.Test

class NewChatBroadcastCapabilityTest {
    @Test fun absentBroadcastNeverFallsBackToGroup() {
        val group = { "group" }
        assertNull(NewChatRules.creatorFor(true, group, null))
    }
    @Test fun explicitGroupStillUsesGroupCallback() {
        val group = { "group" }
        assertSame(group, NewChatRules.creatorFor(false, group, null))
    }
    @Test fun supportedBroadcastUsesOnlyItsDistinctCallback() {
        val group = { "group" }
        val broadcast = { "broadcast" }
        assertSame(broadcast, NewChatRules.creatorFor(true, group, broadcast))
        assertNotSame(group, NewChatRules.creatorFor(true, group, broadcast))
    }
    @Test fun explicitSwitchToGroupChangesTheChosenCallback() {
        val group = { "group" }
        val broadcast = { "broadcast" }
        assertSame(group, NewChatRules.creatorFor(false, group, broadcast))
    }
}
