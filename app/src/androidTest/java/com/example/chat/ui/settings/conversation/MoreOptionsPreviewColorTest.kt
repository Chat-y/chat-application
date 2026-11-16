package com.example.chat.ui.settings.conversation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
// assertExists is a SemanticsNodeInteraction member; no top-level import.
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** One rendered-sample pixel-change source case per owned color. Unrun, not screenshot evidence. */
class MoreOptionsPreviewColorTest {
    @get:Rule val compose = createComposeRule()
    private val theme = ThemePresets.paper
    private fun sample(): MoreOptionsPreviewState {
        val surface = Color(theme.surfaceColor); val text = Color(theme.primaryTextColor)
        val accent = Color(theme.accentColor)
        return MoreOptionsPreviewState(bubbleStyle = BubbleStyleId.stock,
            incomingFill = Color(theme.incomingBubbleColor), emojiHeader = surface,
            emojiIcons = text, emojiBackground = surface, primaryText = text,
            secondaryText = Color(theme.secondaryTextColor), participantColor = accent,
            linkColor = Color(theme.linkColor), voiceButton = accent, voiceProgress = accent,
            infoBackground = surface, infoText = text)
    }
    private fun changedColor(previous: Color): Color =
        if (previous == Color(theme.accentColor)) Color(theme.primaryTextColor) else Color(theme.accentColor)
    private fun assertSampleChanges(tag: String, change: (MoreOptionsPreviewState) -> MoreOptionsPreviewState) {
        val state = mutableStateOf(sample())
        compose.setContent { ChatyTheme(theme) { MoreOptionsPreview(state.value) } }
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
        val before = node.captureToImage().toPixelMap()
        compose.runOnIdle { state.value = change(state.value) }
        val after = node.captureToImage().toPixelMap()
        assertTrue("Sample geometry changed unexpectedly", before.width == after.width && before.height == after.height)
        val changed = (0 until before.height).any { y -> (0 until before.width).any { x -> before[x, y] != after[x, y] } }
        assertTrue("Color event did not change rendered sample pixels: $tag", changed)
    }
    @Test fun emojiHeaderRowChangesItsSample() = assertSampleChanges("conversation-extra-emoji-preview") { it.copy(emojiHeader = changedColor(it.emojiHeader)) }
    @Test fun emojiIconsRowChangesItsSample() = assertSampleChanges("conversation-extra-emoji-preview") { it.copy(emojiIcons = changedColor(it.emojiIcons)) }
    @Test fun emojiBackgroundRowChangesItsSample() = assertSampleChanges("conversation-extra-emoji-preview") { it.copy(emojiBackground = changedColor(it.emojiBackground)) }
    @Test fun linksRowChangesItsSample() = assertSampleChanges("conversation-extra-link-preview") { it.copy(linkColor = changedColor(it.linkColor)) }
    @Test fun groupNameRowChangesItsSample() = assertSampleChanges("conversation-extra-name-preview") { it.copy(participantColor = changedColor(it.participantColor)) }
    @Test fun systemTextRowChangesItsSample() = assertSampleChanges("conversation-extra-date-preview") { it.copy(infoText = changedColor(it.infoText)) }
    @Test fun systemBackgroundRowChangesItsSample() = assertSampleChanges("conversation-extra-date-preview") { it.copy(infoBackground = changedColor(it.infoBackground)) }
    @Test fun voiceProgressRowChangesItsSample() = assertSampleChanges("conversation-extra-voice-preview") { it.copy(voiceProgress = changedColor(it.voiceProgress)) }
    @Test fun voiceButtonRowChangesItsSample() = assertSampleChanges("conversation-extra-voice-preview") { it.copy(voiceButton = changedColor(it.voiceButton)) }
    @Test fun sampleUsesFixedResourceTextWithoutAMessageInput() {
        compose.setContent { ChatyTheme(theme) { MoreOptionsPreview(sample()) } }
        compose.onNodeWithText("Alex", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("https://example.org", useUnmergedTree = true).assertExists()
    }
}
