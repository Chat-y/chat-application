package com.example.chat.ui.chats.detail

/** UI chrome only. Never changes composer text, callbacks, templates or saved style IDs. */
data class ConversationComposerAppearance(val compact: Boolean = false, val floating: Boolean = false) {
    val verticalPaddingDp: Int get() = if (compact) 4 else 8
    val floatingMarginDp: Int get() = if (floating) 8 else 0
    val floatingElevationDp: Int get() = if (floating) 6 else 0
    companion object {
        const val COMPACT = "conv_composer_compact_padding"
        const val FLOATING = "conv_composer_floating"
        fun fromMap(values: Map<String, Any?>): ConversationComposerAppearance = ConversationComposerAppearance(
            compact = values[COMPACT] as? Boolean ?: false,
            floating = values[FLOATING] as? Boolean ?: false,
        )
    }
}
