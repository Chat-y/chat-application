package com.example.chat

import com.example.chat.domain.models.UniversalPreferences
import com.example.chat.ui.chats.detail.ChatyEmojiPack
import org.junit.Assert.*
import org.junit.Test

/** New requirement fixtures only; existing legacy-default assertions are retained for explicit review. */
class AndroidSystemEmojiDefaultTest {
    @Test fun onlySystemDisplayRemainsEvenForSavedCustomChoices() {
        assertEquals(ChatyEmojiPack.SYSTEM, ChatyEmojiPack.normalize(null))
        assertEquals(ChatyEmojiPack.SYSTEM, ChatyEmojiPack.normalize("Chaty Motion"))
        assertEquals(ChatyEmojiPack.SYSTEM, ChatyEmojiPack.normalize(""))
        assertEquals(ChatyEmojiPack.SYSTEM, ChatyEmojiPack.normalize("Chaty"))
        assertEquals(ChatyEmojiPack.SYSTEM, ChatyEmojiPack.normalize("iOS"))
        assertEquals(listOf(ChatyEmojiPack.SYSTEM), ChatyEmojiPack.ORDER)
        assertNull(ChatyEmojiPack.assetPack(ChatyEmojiPack.SYSTEM))
        assertNull(ChatyEmojiPack.assetPathFor(ChatyEmojiPack.SYSTEM, "😀"))
        assertEquals("Android system", UniversalPreferences().emojiStyle)
    }
    @Test fun systemRendererUsesUnicodeTextBeforeAnyCustomFallback() {
        val root = if (java.io.File("app/src/main").exists()) java.io.File("app/src/main") else java.io.File("src/main")
        val source = java.io.File(root, "kotlin/com/example/chat/ui/chats/detail/AnimatedEmoji.kt").readText()
        val system = source.substringAfter("if (pack == ChatyEmojiPack.SYSTEM)").substringBefore("else if")
        assertTrue(system.contains("Text(unicode"))
        assertTrue(system.contains("FontFamily.Default"))
        assertFalse(system.contains("BundledEmojiFallback"))
    }
}
