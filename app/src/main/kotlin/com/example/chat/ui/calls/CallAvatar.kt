package com.example.chat.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Initials avatar with the caller's stored color (Dart AppAvatar usage in
 *  the call screens: initials circle colored by avatarColorHex). */
@Composable
fun CallAvatar(
    initials: String,
    colorHex: String?,
    size: Dp,
) {
    // CALLS-R1-28: parse once per color, not on every recomposition of every visible row.
    val background = remember(colorHex) { parseCallAvatarColor(colorHex) }
    Box(
        modifier = Modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.ifEmpty { "U" },
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.W700,
        )
    }
}

/** Accepts the avatar color formats the backend stores: "0xFF6366F1",
 *  "#6366F1", or "FF6366F1". Falls back to the Chaty indigo. */
fun parseCallAvatarColor(colorHex: String?): Color {
    if (colorHex.isNullOrBlank()) return Color(0xFF6366F1)
    val raw = colorHex.trim()
    val value = when {
        raw.startsWith("0x", ignoreCase = true) -> raw.substring(2).toLongOrNull(16)
        raw.startsWith("#") -> raw.substring(1).toLongOrNull(16)
        else -> raw.toLongOrNull(16)
    } ?: return Color(0xFF6366F1)
    val argb = if (value <= 0xFFFFFF) (0xFF000000L or value) else value
    return Color(argb)
}

/** CALL-R1-07: the one caller-initials rule - first letter of the trimmed name, "U" when empty. */
// Previous implementation retained: fun callInitials(name: String?): String =
// Previous implementation retained:     name?.trim()?.takeIf { it.isNotEmpty() }?.substring(0, 1)?.uppercase() ?: "U"
fun callInitials(name: String?): String {
    val text = name?.trim().orEmpty()
    if (text.isEmpty()) return "U"
    // CALLS-R2-25: ICU character boundaries preserve surrogate pairs and combining clusters.
    // android.icu is available on the app's minSdk 24.
    val iterator = android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
    iterator.setText(text)
    val end = iterator.next()
    return if (end == android.icu.text.BreakIterator.DONE) "U"
        else text.substring(0, end).uppercase(java.util.Locale.ROOT)
}
