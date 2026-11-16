package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.kit.ChatyBubbleContainer
// Previous import pointed to kit; renderer is in chats.detail.
import com.example.chat.ui.chats.detail.DeliveryStatusIcon
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Per-registry actual renderer pixel-presence cases, not a distinct-design or rights audit. Unrun. */
@RunWith(Parameterized::class)
class ConversationBubbleRegistryRenderTest(private val id: BubbleStyleId) {
    @get:Rule val compose = createComposeRule()
    private fun render(incoming: Boolean) {
        val theme = ThemePresets.paper
        val background = Color(theme.backgroundColor)
        val fill = if (Color(theme.accentColor) != background) Color(theme.accentColor) else Color(theme.primaryTextColor)
        compose.setContent { ChatyTheme(theme) {
            Box(Modifier.background(background).padding(8.dp).testTag("registry-bubble")) {
                ChatyBubbleContainer(styleId = id, isMe = !incoming, fillColor = fill, accentColor = fill) {
                    Spacer(Modifier.size(40.dp, 20.dp))
                }
            }
        } }
        val image = compose.onNodeWithTag("registry-bubble", useUnmergedTree = true).captureToImage().toPixelMap()
        assertTrue("Blank bubble renderer: $id incoming=$incoming", (0 until image.height).any { y ->
            (0 until image.width).any { x -> image[x, y] != background }
        })
    }
    @Test fun incomingRendererIsNotBlank() = render(true)
    @Test fun outgoingRendererIsNotBlank() = render(false)
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "bubble={0}")
        fun styles(): Collection<Array<Any>> = ConversationStyleRegistry.bubbles.map { arrayOf<Any>(it.id) }
    }
}

@RunWith(Parameterized::class)
class ConversationTickRegistryRenderTest(private val id: DeliveryIconStyle) {
    @get:Rule val compose = createComposeRule()
    private fun render(state: DeliveryState) {
        val theme = ThemePresets.paper
        val background = Color(theme.backgroundColor)
        val foreground = Color(theme.primaryTextColor)
        compose.setContent { ChatyTheme(theme) {
            Box(Modifier.background(background).padding(8.dp).testTag("registry-tick")) {
                DeliveryStatusIcon(style = id, state = state, unreadColor = foreground,
                    readColor = Color(theme.accentColor), size = 20.dp)
            }
        } }
        val image = compose.onNodeWithTag("registry-tick", useUnmergedTree = true).captureToImage().toPixelMap()
        assertTrue("Blank tick renderer: $id state=$state", (0 until image.height).any { y ->
            (0 until image.width).any { x -> image[x, y] != background }
        })
    }
    @Test fun sentRendererIsNotBlank() = render(DeliveryState.sent)
    @Test fun deliveredRendererIsNotBlank() = render(DeliveryState.delivered)
    @Test fun readRendererIsNotBlank() = render(DeliveryState.read)
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "tick={0}")
        fun styles(): Collection<Array<Any>> = ConversationStyleRegistry.ticks.map { arrayOf<Any>(it.id) }
    }
}
