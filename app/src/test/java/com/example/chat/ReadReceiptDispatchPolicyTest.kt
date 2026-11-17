package com.example.chat

import com.example.chat.data.api.*
import com.example.chat.ui.core.system.ReadReceiptPolicy
import org.junit.Assert.*
import org.junit.Test

class ReadReceiptDispatchPolicyTest {
    private fun owner() = ApiAccountScope("me", "synthetic-token", "test", "https://example.invalid", "", 1)
    @Test fun disabledAndGhostModesNeverSendButEnabledDoes() {
        val owner = owner()
        for (enabled in listOf(false, true)) for (ghost in listOf(false, true)) for (gbGhost in listOf(false, true)) {
            val result = ReadReceiptDispatchPolicy.allowed(owner, { owner }, "room") { _, _ ->
                ReadReceiptPolicy.shouldSend(ghost, gbGhost, enabled, false, "me", listOf("peer"))
            }
            assertEquals(enabled && !ghost && !gbGhost, result)
        }
    }
    @Test fun afterReplyGateUsesExistingPolicyWithoutFakingReadState() {
        val owner = owner()
        fun allowed(senders: List<String>) = ReadReceiptDispatchPolicy.allowed(owner, { owner }, "room") { _, _ ->
            ReadReceiptPolicy.shouldSend(false, false, true, true, "me", senders)
        }
        assertFalse(allowed(listOf("peer")))
        assertTrue(allowed(listOf("peer", "me")))
    }
    @Test fun staleAndSameUserReplacementSessionsCannotSend() {
        val original = owner(); val replacement = owner()
        assertFalse(ReadReceiptDispatchPolicy.allowed(original, { replacement }, "room") { _, _ -> true })
        var active = original
        assertFalse(ReadReceiptDispatchPolicy.allowed(original, { active }, "room") { _, _ -> active = replacement; true })
    }
    @Test fun missingOrFailedPrivacyAndChangedQueuedPreferenceFailClosed() {
        val owner = owner(); var permitted = true
        fun allowed() = ReadReceiptDispatchPolicy.allowed(owner, { owner }, "room") { _, _ -> permitted }
        assertTrue(allowed()); permitted = false; assertFalse(allowed())
        assertFalse(ReadReceiptDispatchPolicy.allowed(owner, { owner }, "room") { _, _ -> error("Not ready") })
        assertFalse(ReadReceiptDispatchPolicy.allowed(owner, { owner }, " ") { _, _ -> true })
    }

    @Test fun restReadPaginationRechecksPrivacyBeforeNextPage() {
        var permitted = true; var calls = 0
        val client = ApiChatClient({ "https://example.invalid" }, { "test" }, { "synthetic" },
            ApiHttpTransport { _, _, _, _ ->
                calls++; permitted = false
                200 to """{"data":{"updated":500,"has_more":true,"message_ids":["message-one"]}}"""
            })
        assertNull(client.markRoomRead("room-one") { permitted })
        assertEquals(1, calls)
    }

    @Test fun regularReadAndAwaitingLoadsKeepTheirCapturedOwnerInSource() {
        val root = if (java.io.File("app/src/main").exists()) java.io.File("app/src/main") else java.io.File("src/main")
        val source = java.io.File(root, "kotlin/com/example/chat/data/api/ApiBackendService.kt").readText()
            .substringBefore("/* Previous regular-chat read dispatch")
            .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertTrue(source.contains("markConversationRead(normalized.conversationId, expected)"))
        assertTrue(source.contains("hydrateRoomMessagesFromCacheIfNeeded(chatId, expected)"))
        assertTrue(source.contains("loadMessages(chatId, expected)"))
        assertTrue(source.contains("ordinaryChatTransport.execute(method, url, headers, body)"))
        assertTrue(source.contains("scopedReader.listMessages(chatId)"))
        assertTrue(source.contains("loadMessagesIfNeeded(conversationId, expected = readOwner)"))
        assertTrue(source.contains("loadMessagesIfNeeded(roomId, expected = readOwner)"))
    }
}
