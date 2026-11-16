package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.ThemeConfig

/** UI-only opt-in. Off retains the existing uniform explicit override and theme-seeded fallback. */
internal const val CONVERSATION_MULTI_COLOUR_NAMES = "conv_multi_colour_participant_names"
/** Default on preserves pre-existing theme-seeded group colors. Uniform explicit overrides remain. */
internal fun conversationMultiColourNamesEnabled(values: Map<String,Any?>): Boolean =
    if (conversationExtraOptionsEnabled(values)) values[CONVERSATION_MULTI_COLOUR_NAMES] as? Boolean ?: true else true
internal const val CONVERSATION_VARY_CUSTOM_NAMES = "conv_vary_custom_participant_names"
internal fun resolveConversationParticipantNameColor(
    theme: ThemeConfig, bubbleBackground: Long, senderId: String, customSeed: Long?,
    varyCustomSeed: Boolean, deriveForSender: Boolean, multiColourNames: Boolean = true,
): Long {
    if (!multiColourNames) return customSeed ?: theme.accentColor
    if (varyCustomSeed && deriveForSender) {
        val seed = com.example.chat.ui.kit.opaqueConversationArgb(customSeed) ?: theme.accentColor
        return ParticipantColorPolicy.colorFor(senderId, seed, bubbleBackground)
    }
    // Preserve previous production behavior exactly when this option is off.
    return customSeed ?: if (deriveForSender) ParticipantColorPolicy.colorFor(senderId, theme.accentColor, theme.surfaceColor)
        else theme.accentColor
}
