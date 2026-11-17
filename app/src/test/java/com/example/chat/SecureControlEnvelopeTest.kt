package com.example.chat

import com.example.chat.data.messaging.SecureControlEnvelope as Controls
import com.example.chat.domain.models.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Authored UNRUN. */
class SecureControlEnvelopeTest {
    private val target = ChatMessage("target", "room", "recipient", text = "hello", createdAt = Instant.EPOCH)
    private fun control(actor: String, revision: Long, remove: Boolean = false, verified: Boolean = true) = ChatMessage(
        "control-$actor-$revision", "room", actor, MessageType.system, "", createdAt = Instant.EPOCH,
        authenticatedMlsSender = if (verified) AuthenticatedMlsSender(actor, "device") else null,
        metadata = mapOf("_mls_sender_verified" to verified, Controls.KEY to mapOf(
            "version" to 1, "kind" to "reaction", "target_id" to "target", "revision" to revision,
            "event_id" to "00000000-0000-0000-0000-${revision.toString().padStart(12, '0')}",
            "operation" to if (remove) "remove" else "set", "emoji" to "like",
        )),
    )
    @Test fun replayIsIdempotentAndHigherRevisionRemoveWins() {
        val add = control("a", 1); val remove = control("a", 2, true)
        assertEquals(listOf("a"), Controls.project(listOf(target, add, add)).single().reactions.single().userIds)
        assertTrue(Controls.project(listOf(remove, target, add, remove)).single().reactions.isEmpty())
    }
    @Test fun oneActorCannotRemoveAnotherActorReaction() {
        val result = Controls.project(listOf(target, control("a", 1), control("b", 99, true))).single()
        assertEquals(listOf("a"), result.reactions.single().userIds)
    }
    @Test fun unauthenticatedControlsCannotAlterTarget() {
        assertTrue(Controls.project(listOf(target, control("a", 1, verified = false))).single().reactions.isEmpty())
    }
    @Test fun forgedWireMarkerWithoutTypedNativeProofCannotApply() {
        val forged = control("a", 1).copy(authenticatedMlsSender = null,
            metadata = control("a", 1).metadata + mapOf("_mls_sender_verified" to true, "decryption_failed" to true))
        assertTrue(Controls.project(listOf(target, forged)).single().reactions.isEmpty())
    }

}
