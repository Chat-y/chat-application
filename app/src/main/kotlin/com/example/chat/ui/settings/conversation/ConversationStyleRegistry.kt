package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.gbBubbleVectorInterpretations
import com.example.chat.core.theme.gbTickVectorInterpretations
import com.example.chat.domain.models.DeliveryState

internal data class ConversationTickOption(
    val id: DeliveryIconStyle, val label: String, val family: String,
    val previewStates: List<DeliveryState>, val vectorInterpretation: Boolean,
)
internal data class ConversationBubbleOption(
    val id: BubbleStyleId, val label: String, val family: String,
    val vectorInterpretation: Boolean, val recommended: Boolean,
)

/** Metadata derives from the renderer enums; saved ids and existing alias readers are unchanged. */
internal object ConversationStyleRegistry {
    val ticks = com.example.chat.core.theme.uniqueDeliveryStyles.mapIndexed { index, id ->
        val family = conversationStyleFamily(id.name)
        ConversationTickOption(id, "$family ${index + 1}: ${com.example.chat.core.theme.deliveryStyleLabel(id)}", family,
            listOf(DeliveryState.sent, DeliveryState.delivered, DeliveryState.read), id in gbTickVectorInterpretations)
    }
    val bubbles = BubbleStyleId.entries.map { id ->
        val family = conversationStyleFamily(id.name)
        ConversationBubbleOption(id, "$family ${id.ordinal + 1}", family,
            id in gbBubbleVectorInterpretations, id == BubbleStyleId.stock)
    }.sortedBy { if (it.recommended) 0 else 1 }
    fun tick(id: DeliveryIconStyle) = ticks.first { it.id == com.example.chat.core.theme.canonicalDeliveryStyle(id) }
    fun bubble(id: BubbleStyleId) = bubbles.first { it.id == id }
}

internal fun conversationStyleFamily(name: String): String {
    val key = name.lowercase(java.util.Locale.ROOT)
    return when {
        "stock" in key || "classic" in key -> "Classic"
        "circle" in key || "dot" in key || "round" in key -> "Rounded"
        "line" in key || "flat" in key || "material" in key || "transparent" in key -> "Minimal"
        else -> "Decorative"
    }
}

/* Previous pre-merge registry retained.
package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.gbBubbleVectorInterpretations
import com.example.chat.core.theme.gbTickVectorInterpretations
import com.example.chat.domain.models.DeliveryState

internal data class ConversationTickOption(
    val id: DeliveryIconStyle, val label: String, val family: String,
    val previewStates: List<DeliveryState>, val vectorInterpretation: Boolean,
)
internal data class ConversationBubbleOption(
    val id: BubbleStyleId, val label: String, val family: String,
    val vectorInterpretation: Boolean, val recommended: Boolean,
)

/** Metadata derives from the renderer enums; saved ids and existing alias readers are unchanged. */
internal object ConversationStyleRegistry {
    val ticks = DeliveryIconStyle.entries.map { id ->
        val family = conversationStyleFamily(id.name)
        ConversationTickOption(id, "$family ${id.ordinal + 1}", family,
            listOf(DeliveryState.sent, DeliveryState.delivered, DeliveryState.read), id in gbTickVectorInterpretations)
    }
    val bubbles = BubbleStyleId.entries.map { id ->
        val family = conversationStyleFamily(id.name)
        ConversationBubbleOption(id, "$family ${id.ordinal + 1}", family,
            id in gbBubbleVectorInterpretations, id == BubbleStyleId.stock)
    }.sortedBy { if (it.recommended) 0 else 1 }
    fun tick(id: DeliveryIconStyle) = ticks.first { it.id == id }
    fun bubble(id: BubbleStyleId) = bubbles.first { it.id == id }
}

internal fun conversationStyleFamily(name: String): String {
    val key = name.lowercase(java.util.Locale.ROOT)
    return when {
        "stock" in key || "classic" in key -> "Classic"
        "circle" in key || "dot" in key || "round" in key -> "Rounded"
        "line" in key || "flat" in key || "material" in key || "transparent" in key -> "Minimal"
        else -> "Decorative"
    }
}

*/
