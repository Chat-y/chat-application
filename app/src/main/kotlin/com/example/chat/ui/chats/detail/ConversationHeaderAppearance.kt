package com.example.chat.ui.chats.detail

/** Additive UI-only choices in the existing extensible visual-settings map. No schema migration. */
data class ConversationHeaderAppearance(
    val optionsEnabled: Boolean = false,
    val nameWeight: String = "default",
    val centeredTitle: Boolean = false,
    val roundedSquareAvatar: Boolean = false,
    val avatarSizeDp: Int = 38,
    val autoContrast: Boolean = false,
    val iconsColor: Long? = null,
    val divider: Boolean = false,
    val statusMarqueeOnTap: Boolean = false,
    val callsInOverflow: Boolean = false,
    val compactLandscape: Boolean = false,
    val subtitleMode: String = "presence",
    val audioCallVisible: Boolean = true,
    val videoCallVisible: Boolean = true,
    val unreadChatBadge: Boolean = false,
    val alphaPercent: Int = 100,
) {
    /** Per-chat theme keeps ownership of colors, matching the existing global color gate. */
    fun forScopedTheme(hasChatThemeOverride: Boolean): ConversationHeaderAppearance =
        if (hasChatThemeOverride) copy(iconsColor = null, autoContrast = false) else this
    fun effective(): ConversationHeaderAppearance = if (optionsEnabled) normalized() else ConversationHeaderAppearance()
    fun nameWeightValue(): Int = when (nameWeight) {
        "regular" -> 400
        "medium" -> 500
        "bold" -> 700
        else -> 800
    }
    fun normalized(): ConversationHeaderAppearance = copy(
        nameWeight = nameWeight.takeIf { it in setOf("default", "regular", "medium", "bold") } ?: "default",
        alphaPercent = alphaPercent.coerceIn(40,100),
        subtitleMode = subtitleMode.takeIf { it in setOf("presence","status","none") } ?: "presence",
        avatarSizeDp = avatarSizeDp.takeIf { it in 32..44 } ?: 38,
        iconsColor = com.example.chat.ui.kit.opaqueConversationArgb(iconsColor),
    )
    companion object {
        const val ALPHA_PERCENT = "conv_header_alpha_percent"
        const val UNREAD_CHAT_BADGE = "conv_header_unread_chat_badge"
        const val AUDIO_VISIBLE = "conv_header_audio_visible"
        const val VIDEO_VISIBLE = "conv_header_video_visible"
        const val SUBTITLE_MODE = "conv_header_subtitle_mode"
        const val OPTIONS_ENABLED = "conv_header_options_enabled"
        const val NAME_WEIGHT = "conv_header_name_weight"
        const val CENTERED_TITLE = "conv_header_centered_title"
        const val AVATAR_SHAPE = "conv_header_rounded_avatar"
        const val AVATAR_SIZE = "conv_header_avatar_size"
        const val AUTO_CONTRAST = "conv_header_auto_contrast"
        const val ICONS_COLOR = "conv_header_icons_color"
        const val DIVIDER = "conv_header_divider"
        const val STATUS_MARQUEE = "conv_header_status_marquee"
        const val CALLS_IN_OVERFLOW = "conv_header_calls_overflow"
        const val COMPACT_LANDSCAPE = "conv_header_compact_landscape"
        fun fromMap(values: Map<String, Any?>): ConversationHeaderAppearance = ConversationHeaderAppearance(
            alphaPercent = (values[ALPHA_PERCENT] as? Number)?.toDouble()?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it in 40.0..100.0 }?.toInt() ?: 100,
            unreadChatBadge = values[UNREAD_CHAT_BADGE] as? Boolean ?: false,
            audioCallVisible = values[AUDIO_VISIBLE] as? Boolean ?: true,
            videoCallVisible = values[VIDEO_VISIBLE] as? Boolean ?: true,
            subtitleMode = values[SUBTITLE_MODE] as? String ?: "presence",
            optionsEnabled = values[OPTIONS_ENABLED] as? Boolean ?: false,
            nameWeight = values[NAME_WEIGHT] as? String ?: "default",
            centeredTitle = values[CENTERED_TITLE] as? Boolean ?: false,
            roundedSquareAvatar = values[AVATAR_SHAPE] as? Boolean ?: false,
            avatarSizeDp = (values[AVATAR_SIZE] as? Number)?.toDouble()?.takeIf {
                it.isFinite() && it >= 32.0 && it <= 44.0 && it % 1.0 == 0.0
            }?.toInt() ?: 38,
            autoContrast = values[AUTO_CONTRAST] as? Boolean ?: false,
            iconsColor = (values[ICONS_COLOR] as? Number)?.toDouble()?.takeIf {
                it.isFinite() && it >= Int.MIN_VALUE.toDouble() && it <= UInt.MAX_VALUE.toDouble() && it % 1.0 == 0.0
            }?.toLong(),
            divider = values[DIVIDER] as? Boolean ?: false,
            statusMarqueeOnTap = values[STATUS_MARQUEE] as? Boolean ?: false,
            callsInOverflow = values[CALLS_IN_OVERFLOW] as? Boolean ?: false,
            compactLandscape = values[COMPACT_LANDSCAPE] as? Boolean ?: false,
        ).normalized()
        fun defaultValues(): Map<String, Any?> = mapOf(ALPHA_PERCENT to 100, UNREAD_CHAT_BADGE to false, AUDIO_VISIBLE to true, VIDEO_VISIBLE to true, SUBTITLE_MODE to "presence", OPTIONS_ENABLED to false, NAME_WEIGHT to "default", CENTERED_TITLE to false,
            AVATAR_SHAPE to false, AVATAR_SIZE to 38, AUTO_CONTRAST to false, ICONS_COLOR to null, DIVIDER to false, STATUS_MARQUEE to false, CALLS_IN_OVERFLOW to false, COMPACT_LANDSCAPE to false)
    }
}

/** Existing monochrome theme tokens supply the black/white candidates; no fixed UI color. */
internal fun conversationHeaderContrastForeground(background: Long): Long = listOf(
    com.example.chat.core.theme.ThemePresets.monochromeLight.accentColor,
    com.example.chat.core.theme.ThemePresets.monochromeDark.accentColor,
).maxBy { com.example.chat.ui.kit.ColorPickerLogic.contrastRatio(it, background) }

internal fun conversationHeaderMarqueeActive(enabled: Boolean, requested: Boolean, systemAnimationsAllowed: Boolean): Boolean =
    enabled && requested && systemAnimationsAllowed

internal fun conversationHeaderCallsInOverflow(showHeaderCalls: Boolean, optedIn: Boolean): Boolean = showHeaderCalls && optedIn
internal fun conversationHeaderVerticalPadding(compactEnabled: Boolean, landscape: Boolean): Int = if (compactEnabled && landscape) 0 else 8

/** Status is only an existing authorized, unblocked profile value. Never synthesize presence. */
internal fun conversationHeaderSubtitle(mode: String, presence: String?, status: String?, statusAllowed: Boolean): String? = when (mode) {
    "none" -> null
    "status" -> status?.takeIf { statusAllowed && it.isNotBlank() }
    else -> presence
}
