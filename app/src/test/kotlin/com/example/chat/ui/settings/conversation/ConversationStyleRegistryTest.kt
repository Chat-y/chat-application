package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.gbBubbleVectorInterpretations
import com.example.chat.core.theme.gbTickVectorInterpretations
import com.example.chat.domain.models.DeliveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** New source-only registry cases, not renderer/device proof and not executed. */
class ConversationStyleRegistryTest {
    @Test fun round1EveryTickEnumAppearsExactlyOnce() = assertEquals(
        com.example.chat.core.theme.uniqueDeliveryStyles.toSet(), ConversationStyleRegistry.ticks.map { it.id }.toSet().also {
            assertEquals(it.size, ConversationStyleRegistry.ticks.size)
        })
    @Test fun round2EveryBubbleEnumAppearsExactlyOnce() = assertEquals(
        BubbleStyleId.entries.toSet(), ConversationStyleRegistry.bubbles.map { it.id }.toSet().also {
            assertEquals(it.size, ConversationStyleRegistry.bubbles.size)
        })
    @Test fun round3TickPreviewMetadataCoversAllDeliveryStates() {
        ConversationStyleRegistry.ticks.forEach {
            assertEquals(listOf(DeliveryState.sent, DeliveryState.delivered, DeliveryState.read), it.previewStates)
        }
    }
    @Test fun round4StockIsFirstAndTheOnlyRecommendation() {
        assertEquals(BubbleStyleId.stock, ConversationStyleRegistry.bubbles.first().id)
        assertEquals(1, ConversationStyleRegistry.bubbles.count { it.recommended })
    }
    @Test fun round5SourceFlagsAndNeutralLabelsStayAttachedToIds() {
        ConversationStyleRegistry.ticks.forEach {
            assertEquals(it.id in gbTickVectorInterpretations, it.vectorInterpretation)
            assertTrue(it.label.startsWith(it.family))
        }
        ConversationStyleRegistry.bubbles.forEach {
            assertEquals(it.id in gbBubbleVectorInterpretations, it.vectorInterpretation)
            assertTrue(it.label.startsWith(it.family))
        }
    }
}
