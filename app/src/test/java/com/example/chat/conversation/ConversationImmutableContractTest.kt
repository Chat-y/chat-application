package com.example.chat.conversation

import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.EmojiPopupStyle
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test

/** Guards the promised object shape, not a Compose compiler report or a skipping guarantee. */
class ConversationImmutableContractTest {
    private fun assertFinalScalars(type: Class<*>) {
        val allowed = setOf(String::class.java, java.lang.Long::class.java,
            java.lang.Boolean::class.java, java.lang.Double::class.java,
            java.lang.Integer::class.java, java.lang.Float::class.java)
        val fields = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        assertTrue("No instance field contract found", fields.isNotEmpty())
        fields.forEach {
            assertTrue("Mutable backing field: ${it.name}", Modifier.isFinal(it.modifiers))
            assertTrue("Review @Immutable before adding ${it.name}: ${it.type}", it.type.isPrimitive || it.type in allowed)
        }
    }
    @Test fun conversationBackedStateIsFinalAndScalar() { assertFinalScalars(ConversationPreferences::class.java) }
    @Test fun emojiPaletteBackedStateIsFinalAndScalar() { assertFinalScalars(EmojiPopupStyle::class.java) }
    @Test fun changingSerializationMapCannotMutateOriginalValue() {
        val value = ConversationPreferences(actionBarColor=11L,emojiHeaderColor=22L)
        val map = value.toMap().toMutableMap()
        map["actionBarColor"] = 99L
        map["emojiHeaderColor"] = 88L
        assertEquals(11L,value.actionBarColor)
        assertEquals(22L,value.emojiPopupStyle.header)
    }
    @Test fun changingPaletteProducesNewValueWithoutChangingSource() {
        val value = ConversationPreferences(emojiHeaderColor=11L)
        val changed = value.withEmojiPopupStyle(value.emojiPopupStyle.copy(header=22L))
        assertEquals(11L,value.emojiPopupStyle.header)
        assertEquals(22L,changed.emojiPopupStyle.header)
    }
}
