package com.example.chat.ui.settings.conversation

import kotlin.math.roundToInt

/** Matches this screen's discrete 12..26 point slider without ticking within one detent. */
internal fun conversationTextDetent(value: Float): Int? =
    if (value.isFinite()) value.coerceIn(12f, 26f).roundToInt() else null
