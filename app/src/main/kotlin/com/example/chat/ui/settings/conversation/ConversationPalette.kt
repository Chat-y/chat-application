package com.example.chat.ui.settings.conversation

/** Names the existing picker swatches, without introducing or persisting colours. */
internal fun conversationNamedPalette(resources: android.content.res.Resources): List<Pair<String, Long>> {
    val labels = resources.getStringArray(com.example.chat.R.array.conv_proposal_colour_names)
    return com.example.chat.ui.kit.ColorPickerLogic.presetSwatches.mapIndexed { index, colour ->
        (labels.getOrNull(index) ?: "#${com.example.chat.ui.kit.ColorPickerLogic.toHex(colour)}") to colour
    }
}

/** Most-recent first, six unique values, in-memory only. */
internal fun withRecentConversationColour(previous: List<Long>, colour: Long): List<Long> =
    (listOf(colour) + previous).distinct().take(6)
