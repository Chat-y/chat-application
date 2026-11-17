package com.example.chat

import org.junit.Assert.*
import org.junit.Test

/** Source contract only. UI timing/appearance and gesture acceptance still require a device. */
class ReactionFeedbackSourceTest {
    private fun source(path: String): String {
        val root = if (java.io.File("app/src/main").exists()) java.io.File("app/src/main") else java.io.File("src/main")
        return java.io.File(root, path).readText()
    }
    @Test fun feedbackUsesActualChangesSharedTokensAndReducedMotion() {
        val source = source("kotlin/com/example/chat/ui/chats/detail/ReactionFeedback.kt")
        assertTrue(source.contains("previous != (count to selected)"))
        assertTrue(source.contains("if (changed && !reduceMotion)"))
        assertTrue(source.contains("ChatyMotion.reactionFeedbackScale"))
        assertTrue(source.contains("ChatyMotion.instantMs"))
        assertTrue(source.contains("ChatyMotion.fastMs"))
        assertTrue(source.contains("if (reduceMotion) 1f else scale.value"))
        assertFalse(source.contains("rememberInfiniteTransition"))
    }
    @Test fun badgeKeysAreStableAndBottomActionCallbacksRemain() {
        val source = source("kotlin/com/example/chat/ui/chats/detail/MessageBubble.kt").substringBefore("/* Pre-")
        assertTrue(source.contains("key(message.id, reaction.emoji)"))
        assertTrue(source.contains(".reactionFeedback(count, isSelected, reduceMotion)"))
        assertTrue(source.contains("else onReactionTap?.invoke(reaction.emoji)"))
        assertTrue(source.contains("color = borderColor"))
    }
    @Test fun reactionBadgesUseTheExistingScreenMotionObserver() {
        val bubble = source("kotlin/com/example/chat/ui/chats/detail/MessageBubble.kt")
        val badge = bubble.substringAfter("private fun ReactionBadge(").substringBefore("private fun ImageAttachmentPreview(")
            .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertFalse(badge.contains("rememberChatyReduceMotion()"))
        assertTrue(badge.contains("reduceMotion: Boolean"))
        assertTrue(bubble.contains("reduceMotion = reduceReactionMotion"))
        val screen = source("kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt")
        assertTrue(screen.contains("reduceReactionMotion = viewerAnchorReduceMotion"))
    }

}
