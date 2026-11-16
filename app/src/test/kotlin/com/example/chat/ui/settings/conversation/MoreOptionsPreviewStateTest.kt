package com.example.chat.ui.settings.conversation

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.BubbleStyleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** State-copy regression cases; source only, not executed. Sentinel colours are test data. */
class MoreOptionsPreviewStateTest {
    private fun state(): MoreOptionsPreviewState = MoreOptionsPreviewState(
        BubbleStyleId.stock, Color(1L), Color(2L), Color(3L), Color(4L), Color(5L), Color(6L),
        Color(7L), Color(8L), Color(9L), Color(10L), Color(11L), Color(12L))
    @Test fun round1EqualValuesHaveStructuralEquality() = assertEquals(state(), state())
    @Test fun round2BubbleStyleIsPartOfPreviewState() = assertNotEquals(state(), state().copy(bubbleStyle = BubbleStyleId.rounded))
    @Test fun round3CustomNameColourDoesNotOverwriteLinkColour() {
        val before = state()
        assertEquals(before.linkColor, before.copy(participantColor = Color(13L)).linkColor)
    }
    @Test fun round4CustomTrackDoesNotOverwriteButtonFill() {
        val before = state()
        assertEquals(before.voiceButton, before.copy(voiceProgress = Color(14L)).voiceButton)
    }
    @Test fun round5CustomInfoBackgroundDoesNotOverwriteText() {
        val before = state()
        assertEquals(before.infoText, before.copy(infoBackground = Color(15L)).infoText)
    }
}
