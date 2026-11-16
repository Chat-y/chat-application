package com.example.chat

import com.example.chat.domain.models.NotificationToastPreferences
import com.example.chat.ui.settings.notifications.toastCardUsesDefaults
import com.example.chat.ui.settings.notifications.toastPreviewFontScale
import com.example.chat.ui.settings.notifications.undoToastAppearance
import com.example.chat.ui.settings.notifications.toastAppearanceChanged
import com.example.chat.ui.settings.notifications.ToastCardType
import com.example.chat.ui.settings.notifications.toastCardSlice
import com.example.chat.ui.settings.notifications.withToastCardSlice
import org.junit.Assert.assertEquals
import org.junit.Test

class ToastCardSliceGapTest {
    @Test fun roundTripEveryCardPreservesWholeModel() {
        val prefs = NotificationToastPreferences(onlineToastRadius = 2.0, storyToastRadius = 8.0,
            profileToastRadius = 16.0, typingToastRadius = 24.0)
        ToastCardType.entries.forEach { type ->
            assertEquals(prefs, withToastCardSlice(type, prefs, toastCardSlice(type, prefs)))
        }
    }
    @Test fun changingOneSliceDoesNotChangeOtherTypes() {
        val before = NotificationToastPreferences()
        ToastCardType.entries.forEach { changedType ->
            val changed = toastCardSlice(changedType, before).copy(radius = 30.0, enabled = false)
            val after = withToastCardSlice(changedType, before, changed)
            assertEquals(changed, toastCardSlice(changedType, after))
            ToastCardType.entries.filter { it != changedType }.forEach { other ->
                assertEquals(toastCardSlice(other, before), toastCardSlice(other, after))
            }
        }
    }
    @Test fun labelsAreMappedWithTheSameImmutableSlice() {
        val source = NotificationToastPreferences(onlineToastRingtone = "Silent", onlineToastBgColor = 0xFFFFFFFFL)
        val card = toastCardSlice(ToastCardType.online, source)
        assertEquals("White (#FFFFFFFF)", card.backgroundLabel)
        assertEquals("Auto (readable on background)", card.foregroundLabel)
        assertEquals("Silent - no sound will play", card.ringtoneLabel)
        val edited = card.copy(background = null, ringtone = "Default")
        assertEquals("Default", edited.backgroundLabel)
        assertEquals("Default", edited.ringtoneLabel)
    }
    @Test fun defaultCaptionUsesEachTypesOwnDefaults() {
        val defaults = NotificationToastPreferences()
        ToastCardType.entries.forEach { type ->
            val card = toastCardSlice(type, defaults)
            assertEquals(true, toastCardUsesDefaults(type, card))
            assertEquals(false, toastCardUsesDefaults(type, card.copy(radius = card.radius + 1.0)))
            assertEquals(false, toastCardUsesDefaults(type, card.copy(enabled = !card.enabled)))
        }
    }
    @Test fun otherCardCustomizationDoesNotChangeDefaultCaption() {
        val changed = NotificationToastPreferences(onlineToastRadius = 30.0)
        assertEquals(false, toastCardUsesDefaults(ToastCardType.online, toastCardSlice(ToastCardType.online, changed)))
        listOf(ToastCardType.story, ToastCardType.profile, ToastCardType.typing).forEach { type ->
            assertEquals(true, toastCardUsesDefaults(type, toastCardSlice(type, changed)))
        }
    }
    @Test fun largeTextSampleIsExactlyTwoHundredPercent() {
        listOf(1f, 1.5f, 2f, 2.5f).forEach { systemScale ->
            assertEquals(2f, toastPreviewFontScale(systemScale, true), 0f)
            assertEquals(systemScale, toastPreviewFontScale(systemScale, false), 0f)
        }
    }
    @Test fun appearanceUndoRestoresOnlyItsOwnChange() {
        val before = toastCardSlice(ToastCardType.online, NotificationToastPreferences())
        val after = before.copy(background = 0xFF123456L)
        val current = after.copy(radius = 30.0, enabled = false)
        assertEquals(current.copy(background = before.background), undoToastAppearance(before, after, current))
        assertEquals(true, toastAppearanceChanged(before, after))
        assertEquals(false, toastAppearanceChanged(before, before.copy(enabled = !before.enabled)))
    }
    @Test fun appearanceUndoPreservesLaterEditToSameField() {
        val before = toastCardSlice(ToastCardType.online, NotificationToastPreferences())
        val after = before.copy(radius = 10.0)
        val current = after.copy(radius = 20.0)
        assertEquals(current, undoToastAppearance(before, after, current))
    }
    @Test fun previewThemeFactoryBooleanMeansLightNotDark() {
        assertEquals(false, com.example.chat.core.theme.ThemePresets.getSystemDefaultTheme(true).isDark)
        assertEquals(true, com.example.chat.core.theme.ThemePresets.getSystemDefaultTheme(false).isDark)
    }
}
