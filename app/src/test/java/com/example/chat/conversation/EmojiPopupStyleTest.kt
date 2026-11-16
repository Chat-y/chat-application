package com.example.chat.ui.settings.conversation
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.EmojiPopupStyle
import org.junit.Assert.*
import org.junit.Test
class EmojiPopupStyleTest {
    @Test fun objectRoundTripKeepsNullableFields() {
        val original = ConversationPreferences(emojiHeaderColor = 42L,emojiPickerBgColor = 99L)
        assertEquals(original.emojiPopupStyle,ConversationPreferences.fromMap(original.toMap()).emojiPopupStyle)
    }
    @Test fun oldScalarOnlyPaletteStillReads() {
        assertEquals(EmojiPopupStyle(10L,20L,null),ConversationPreferences.fromMap(mapOf("emojiHeaderColor" to 10L,"emojiHeaderIconsColor" to 20L)).emojiPopupStyle)
    }
    @Test fun explicitObjectNullOverridesStaleScalar() {
        assertNull(ConversationPreferences.fromMap(mapOf("emojiPopupStyle" to EmojiPopupStyle().toMap(),"emojiHeaderColor" to 100L)).emojiHeaderColor)
    }
    @Test fun malformedObjectFallsBackWithoutMixingPartialObjects() {
        assertNull(EmojiPopupStyle.fromMap(mapOf("header" to Double.NaN,"icons" to null,"background" to null)))
        assertNull(EmojiPopupStyle.fromMap(mapOf("header" to 5L)))
        assertEquals(8L,ConversationPreferences.fromMap(mapOf("emojiPopupStyle" to "bad","emojiHeaderColor" to 8L)).emojiHeaderColor)
    }
    @Test fun oneResetPreservesOtherColoursAndWritesLegacyRollbackAliases() {
        val source = ConversationPreferences(emojiHeaderColor = 1L,emojiHeaderIconsColor = 2L,emojiPickerBgColor = 3L,hyperlinksColor = 9L)
        val reset = reduceMoreOptions(source,MoreOptionsEvent.ResetEmojiStyle)
        assertEquals(EmojiPopupStyle(),reset.emojiPopupStyle);assertEquals(9L,reset.hyperlinksColor)
        assertTrue(reset.toMap().containsKey("emojiHeaderColor"));assertTrue(reset.toMap().containsKey("emojiPopupStyle"))
    }
}
