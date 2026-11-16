package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.chat.core.theme.ConversationEntryStyles
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.ui.chats.detail.ChatEntryBar
import com.example.chat.ui.chats.detail.ComposerColors
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual gallery -> adapter -> production composer renderer. Source-only, unrun. */
class ConversationEntryStyleSelectionTest {
    @get:Rule val compose = createComposeRule()
    private fun selectAndAssert(index: Int) {
        val theme = ThemePresets.paper
        val initial = ConversationEntryStyles.options.first()
        val target = ConversationEntryStyles.options[index]
        val preferences = mutableStateOf(ConversationPreferences(entryStyle = initial.id))
        val model = EntryStyleViewModel({ preferences.value }, { preferences.value = it }, {}, {})
        compose.setContent { ChatyTheme(theme) { Column {
            val colors = ComposerColors.resolve(theme, null, null, null, null, null, null)
            Box(Modifier.testTag("selected-entry-preview")) {
                ChatEntryBar(entryStyle = ConversationEntryStyles.layoutName(preferences.value.entryStyle), colors = colors,
                    template = com.example.chat.core.templates.ComposerTemplate(com.example.chat.core.templates.ComposerActionPlacement.integrated),
                    theme = theme, text = "Sample message", onTextChange = {}, onCamera = {}, onSend = {})
            }
            ConversationStyleGallery(title = "Message bar", values = ConversationEntryStyles.options,
                selected = ConversationEntryStyles.resolve(preferences.value.entryStyle), labelOf = { it.label },
                keyOf = { it.id }, familyOf = { it.family },
                onSelected = { model.onEvent(EntryStyleEvent.SelectStyle(it.id)) }) { }
        } } }
        val before = compose.onNodeWithTag("selected-entry-preview", useUnmergedTree = true).captureToImage().toPixelMap()
        compose.onNodeWithText("Message bar: ${initial.label}").performClick()
        compose.onNodeWithText("Search styles").performTextInput(target.label)
        compose.onNodeWithText(target.label).performClick()
        compose.runOnIdle { assertEquals(initial.id, preferences.value.entryStyle) }
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals(target.id, preferences.value.entryStyle) }
        val after = compose.onNodeWithTag("selected-entry-preview", useUnmergedTree = true).captureToImage().toPixelMap()
        val changed = before.width != after.width || before.height != after.height ||
            (0 until before.height).any { y -> (0 until before.width).any { x -> before[x, y] != after[x, y] } }
        assertTrue("Applied entry style did not change the rendered bar: ${target.id}", changed)
    }
    @Test fun round1ApplySplitActionsUpdatesTheBar() = selectAndAssert(1)
    @Test fun round2ApplyEmojiAttachmentUpdatesTheBar() = selectAndAssert(2)
    @Test fun round3ApplyTwoRowsUpdatesTheBar() = selectAndAssert(3)
    @Test fun round4ApplyBottomActionsUpdatesTheBar() = selectAndAssert(4)
    @Test fun round5ApplyMinimalUpdatesTheBar() = selectAndAssert(5)
}
