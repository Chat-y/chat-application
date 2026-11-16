package com.example.chat.ui.settings.conversation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle

/** Source-only cases for the extracted renderer. Not executed in this pass. */
class BubblePreviewScopeTest {
    @get:Rule val compose = createComposeRule()
    private fun sample(style: BubbleStyleId) = BubblePreviewState(
        bubbleStyle = style, tickStyle = DeliveryIconStyle.stock,
        convBg = Color(ThemePresets.paper.backgroundColor),
        rightBubbleBg = Color(ThemePresets.paper.backgroundColor),
        rightTextCol = Color(ThemePresets.paper.primaryTextColor),
        rightTimeCol = Color(ThemePresets.paper.secondaryTextColor),
        leftBubbleBg = Color(ThemePresets.paper.backgroundColor),
        leftTextCol = Color(ThemePresets.paper.primaryTextColor),
        leftTimeCol = Color(ThemePresets.paper.secondaryTextColor),
        quotedBg = Color(ThemePresets.paper.backgroundColor),
        quotedDivider = Color(ThemePresets.paper.accentColor),
        quotedName = Color(ThemePresets.paper.primaryTextColor),
        quotedMsg = Color(ThemePresets.paper.primaryTextColor),
        deletedIconColor = Color(ThemePresets.paper.secondaryTextColor),
        textSize = 14f, makeTextSelectable = false, removeReadMore = false,
    )
    private fun assertBoth(style: BubbleStyleId) {
        val matcher = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, style.name)
        compose.onNodeWithTag("bubble-preview-incoming", useUnmergedTree = true).assert(matcher)
        compose.onNodeWithTag("bubble-preview-outgoing", useUnmergedTree = true).assert(matcher)
    }
    @Test fun round1BothSidesUseStock() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { BubblePreview(sample(BubbleStyleId.stock)) } }
        assertBoth(BubbleStyleId.stock)
    }
    @Test fun round2StyleChangeUpdatesBothSides() {
        val state = mutableStateOf(sample(BubbleStyleId.stock))
        compose.setContent { ChatyTheme(ThemePresets.paper) { BubblePreview(state.value) } }
        BubbleStyleId.values().forEach { style ->
            compose.runOnIdle { state.value = state.value.copy(bubbleStyle = style) }
            assertBoth(style)
        }
    }
    @Test fun round3SelectableSampleRetainsBothRenderers() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { BubblePreview(sample(BubbleStyleId.stock).copy(makeTextSelectable = true)) } }
        assertBoth(BubbleStyleId.stock)
    }
    @Test fun round4ExpandedSampleRetainsBothRenderers() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { BubblePreview(sample(BubbleStyleId.stock).copy(removeReadMore = true)) } }
        assertBoth(BubbleStyleId.stock)
    }
    @Test fun round5DarkThemeRetainsBothRenderers() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { BubblePreview(sample(BubbleStyleId.stock)) } }
        assertBoth(BubbleStyleId.stock)
    }
}
