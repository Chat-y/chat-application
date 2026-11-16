package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** UI-only option contracts, five source cases each. Unrun. */
class ConversationHeaderAppearanceTest {
    @Test fun weightDefaultKeepsActualProduction800() { assertEquals(800, ConversationHeaderAppearance.fromMap(emptyMap()).nameWeightValue()) }
    @Test fun weightChoicesAreExact() {
        mapOf("regular" to 400, "medium" to 500, "bold" to 700).forEach { (key,value) ->
            assertEquals(value, ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.NAME_WEIGHT to key)).nameWeightValue())
        }
    }
    @Test fun unknownWeightFallsBack() { assertEquals("default", ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.NAME_WEIGHT to "heavy")).nameWeight) }
    @Test fun wrongTypeWeightFallsBack() { assertEquals(800, ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.NAME_WEIGHT to 400)).nameWeightValue()) }
    @Test fun weightDoesNotChangeOtherFields() {
        assertEquals(ConversationHeaderAppearance(nameWeight = "medium"), ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.NAME_WEIGHT to "medium", "unrelated" to true)))
    }
    @Test fun avatarDefaultKeepsCircle38() {
        val value = ConversationHeaderAppearance.fromMap(emptyMap()); assertEquals(38, value.avatarSizeDp); assertFalse(value.roundedSquareAvatar)
    }
    @Test fun avatarRangeKeepsEveryAllowedInteger() {
        (32..44).forEach { assertEquals(it, ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.AVATAR_SIZE to it)).avatarSizeDp) }
    }
    @Test fun avatarInvalidNumbersFallBackWithoutWrapping() {
        listOf(31,45,Long.MAX_VALUE,Double.NaN,Double.POSITIVE_INFINITY,36.5).forEach {
            assertEquals(38, ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.AVATAR_SIZE to it)).avatarSizeDp)
        }
    }
    @Test fun avatarShapeRequiresExplicitBoolean() {
        assertTrue(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.AVATAR_SHAPE to true)).roundedSquareAvatar)
        assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.AVATAR_SHAPE to "true")).roundedSquareAvatar)
    }
    @Test fun avatarDirectInvalidSizeNormalizes() { assertEquals(38, ConversationHeaderAppearance(avatarSizeDp = -1).normalized().avatarSizeDp) }
    @Test fun titleDefaultStaysStartAligned() { assertFalse(ConversationHeaderAppearance.fromMap(emptyMap()).centeredTitle) }
    @Test fun titleExplicitTrueIsHonored() { assertTrue(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.CENTERED_TITLE to true)).centeredTitle) }
    @Test fun titleFalseDoesNotBecomeTrue() { assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.CENTERED_TITLE to false)).centeredTitle) }
    @Test fun titleWrongTypeDoesNotOptIn() {
        listOf("true",1,emptyList<String>()).forEach { assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.CENTERED_TITLE to it)).centeredTitle) }
    }
    @Test fun titleResetReturnsAllDefaultsWithoutUnknownKeys() {
        assertEquals(ConversationHeaderAppearance(), ConversationHeaderAppearance.fromMap(ConversationHeaderAppearance.defaultValues()))
        assertEquals(setOf(ConversationHeaderAppearance.ALPHA_PERCENT,ConversationHeaderAppearance.UNREAD_CHAT_BADGE,ConversationHeaderAppearance.AUDIO_VISIBLE,ConversationHeaderAppearance.VIDEO_VISIBLE,ConversationHeaderAppearance.SUBTITLE_MODE,ConversationHeaderAppearance.OPTIONS_ENABLED,ConversationHeaderAppearance.NAME_WEIGHT,ConversationHeaderAppearance.CENTERED_TITLE,
            ConversationHeaderAppearance.AVATAR_SHAPE,ConversationHeaderAppearance.AVATAR_SIZE, ConversationHeaderAppearance.AUTO_CONTRAST,
            ConversationHeaderAppearance.ICONS_COLOR,ConversationHeaderAppearance.DIVIDER,ConversationHeaderAppearance.STATUS_MARQUEE,
            ConversationHeaderAppearance.CALLS_IN_OVERFLOW,ConversationHeaderAppearance.COMPACT_LANDSCAPE), ConversationHeaderAppearance.defaultValues().keys)
    }
}
