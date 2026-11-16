package com.example.chat.ui.settings.conversation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic sentinel values are test-only. Source cases, not run. */
class ConversationHeaderColorsTest {
    @Test fun round1NullValuesFollowTheCurrentTheme() = assertEquals(
        ConversationHeaderColors(1L, 2L, 3L), resolveConversationHeaderColors(null, null, null, 1L, 2L, 3L))
    @Test fun round2CustomValuesAreOpaqueOnUiRead() = assertEquals(
        ConversationHeaderColors(0xFF000004L, 0xFF000005L, 0xFF000006L),
        resolveConversationHeaderColors(4L, 5L, 6L, 1L, 2L, 3L))
    @Test fun round3ThemeChangeUpdatesOnlyNullValues() = assertEquals(
        ConversationHeaderColors(0xFF000004L, 8L, 9L), resolveConversationHeaderColors(4L, null, null, 7L, 8L, 9L))
    @Test fun round4TransparentOverrideIsOpaqueInUiOnly() = assertEquals(
        0xFF000000L, resolveConversationHeaderColors(0L, null, null, 1L, 2L, 3L).bar)
    @Test fun round5ResetRestoresThemeFollowing() {
        val custom = resolveConversationHeaderColors(4L, null, null, 1L, 2L, 3L)
        val reset = resolveConversationHeaderColors(null, null, null, 1L, 2L, 3L)
        assertEquals(0xFF000004L, custom.bar)
        assertEquals(1L, reset.bar)
    }
}

/* Previous pre-opaque-read source expectations retained for review.
/** Synthetic sentinel values are not production theme colours. Source-only cases, not run. */
class ConversationHeaderColorsTest {
    @Test fun round1NullValuesFollowTheCurrentTheme() = assertEquals(
        ConversationHeaderColors(1L, 2L, 3L), resolveConversationHeaderColors(null, null, null, 1L, 2L, 3L))
    @Test fun round2CustomValuesTakePriority() = assertEquals(
        ConversationHeaderColors(4L, 5L, 6L), resolveConversationHeaderColors(4L, 5L, 6L, 1L, 2L, 3L))
    @Test fun round3ThemeChangeUpdatesOnlyNullValues() = assertEquals(
        ConversationHeaderColors(4L, 8L, 9L), resolveConversationHeaderColors(4L, null, null, 7L, 8L, 9L))
    @Test fun round4TransparentOverrideIsNotSilentlyRewritten() = assertEquals(
        0L, resolveConversationHeaderColors(0L, null, null, 1L, 2L, 3L).bar)
    @Test fun round5ResetRestoresThemeFollowing() {
        val custom = resolveConversationHeaderColors(4L, null, null, 1L, 2L, 3L)
        val reset = resolveConversationHeaderColors(null, null, null, 1L, 2L, 3L)
        assertEquals(4L, custom.bar)
        assertEquals(1L, reset.bar)
    }
}

*/
