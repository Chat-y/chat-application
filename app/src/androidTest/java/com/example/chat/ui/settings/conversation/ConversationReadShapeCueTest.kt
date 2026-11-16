package com.example.chat.ui.settings.conversation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.chats.detail.DeliveryStatusIcon
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Same tint in both states: rendered difference must come from shape, not color. Unrun. */
class ConversationReadShapeCueTest {
    @get:Rule val compose = createComposeRule()
    private fun verify(style: DeliveryIconStyle) {
        val state = mutableStateOf(DeliveryState.delivered)
        val color = Color(ThemePresets.paper.primaryTextColor)
        compose.setContent { ChatyTheme(ThemePresets.paper) {
            DeliveryStatusIcon(style, state.value, color, color, size = 24.dp, modifier = Modifier.testTag("receipt-cue"))
        } }
        val node = compose.onNodeWithTag("receipt-cue")
        val delivered = node.captureToImage().toPixelMap()
        compose.runOnIdle { state.value = DeliveryState.read }
        val read = node.captureToImage().toPixelMap()
        assertEquals(delivered.width, read.width); assertEquals(delivered.height, read.height)
        assertTrue("Read shape must differ at equal tint: $style", (0 until read.height).any { y ->
            (0 until read.width).any { x -> read[x, y] != delivered[x, y] }
        })
    }
    @Test fun stockHasNonColorReadCue() = verify(DeliveryIconStyle.stock)
    @Test fun stickerHasNonColorReadCue() = verify(DeliveryIconStyle.sticker)
    @Test fun iosTicksHaveNonColorReadCue() = verify(DeliveryIconStyle.ios2)
    @Test fun chevronTicksHaveNonColorReadCue() = verify(DeliveryIconStyle.rcIos11)
    @Test fun bbmTicksHaveNonColorReadCue() = verify(DeliveryIconStyle.bbmV2)
}
