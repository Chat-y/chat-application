package com.example.chat

import com.example.chat.ui.calls.CallPipSessionPolicy
import com.example.chat.data.services.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CallPipSessionPolicyTest {
    private fun session(state: CallSessionState = CallSessionState.connected, video: Boolean = false) =
        ChatyCallSession("call", "peer", "Peer", isVideo = video, isOutgoing = false, state = state, startedAt = Instant.EPOCH)
    @Test fun voiceAndVideoUseOnlyTheCapturedCall() {
        val owner = Any()
        assertTrue(CallPipSessionPolicy.allows(owner, owner, "call", session()))
        assertTrue(CallPipSessionPolicy.allows(owner, owner, "call", session(video = true)))
        assertFalse(CallPipSessionPolicy.allows(owner, owner, "old-call", session()))
    }
    @Test fun accountChangeNullOwnerAndIncomingNeverEnterPip() {
        val owner = Any()
        assertFalse(CallPipSessionPolicy.allows(owner, Any(), "call", session()))
        assertFalse(CallPipSessionPolicy.allows(null, null, "call", session()))
        assertFalse(CallPipSessionPolicy.allows(owner, owner, "call", session(CallSessionState.incoming)))
        assertFalse(CallPipSessionPolicy.allows(owner, owner, "call", session(CallSessionState.initiating)))
    }
    @Test fun terminatedOrMissingSessionCannotBeResurrected() {
        val owner = Any()
        for (state in CallSessionState.entries.filter { it.isTerminal })
            assertFalse(state.name, CallPipSessionPolicy.allows(owner, owner, "call", session(state)))
        assertFalse(CallPipSessionPolicy.allows(owner, owner, "call", null))
    }
}
