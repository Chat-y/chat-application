package com.example.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chat.data.services.ChatyEventNotification
import com.example.chat.domain.notifications.ContactToastStyle
import com.example.chat.ui.theme.LocalAppColors

/** One geometry definition for the compact presence presentation, independent of event policy. */
internal object PresenceToastMetrics {
    const val MAX_WIDTH_DP = 360
    const val HORIZONTAL_PADDING_DP = 10
    const val VERTICAL_PADDING_DP = 6
    const val AVATAR_TEXT_GAP_DP = 8
    const val MIN_HEIGHT_DP = 44
}

/** Uses the existing event title/status. Never generates a presence event or changes its permission. */
@Composable
internal fun PresenceToastPill(
    event: ChatyEventNotification,
    style: ContactToastStyle,
    avatarSize: Dp,
    background: Color,
    foreground: Color,
    corner: Dp,
    elevation: Dp,
    showAvatar: Boolean,
    showName: Boolean,
    showPreview: Boolean,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(corner)
    Row(
        Modifier.widthIn(max = PresenceToastMetrics.MAX_WIDTH_DP.dp)
            .heightIn(min = PresenceToastMetrics.MIN_HEIGHT_DP.dp)
            .shadow(elevation, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(background, shape)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(horizontal = PresenceToastMetrics.HORIZONTAL_PADDING_DP.dp,
                vertical = PresenceToastMetrics.VERTICAL_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAvatar && style.showAvatar) {
            // Directory lookup only; no new profile retrieval or changed presence audience.
            val profile = event.userId?.let { id -> com.example.chat.ServiceLocator.apiBackend.users.firstOrNull { it.id == id } }
            val initials = event.avatarInitials?.takeIf { it.isNotBlank() } ?: profile?.avatarInitials ?: "?"
            // Theme tokens only. Directory photo pixels remain the user's actual image.
            val tint = colors.accent
            val fallback: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().background(tint), contentAlignment = Alignment.Center) {
                    Text(initials, color = colors.onAccent, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val pixels = with(LocalDensity.current) { avatarSize.roundToPx().coerceAtLeast(1) }
            Box(Modifier.size(avatarSize).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                com.example.chat.ui.theme.ChatyAsyncImage(
                    profile?.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    placeholder = fallback, error = fallback,
                    requestSize = coil.size.Size(pixels, pixels),
                )
            }
            Spacer(Modifier.width(PresenceToastMetrics.AVATAR_TEXT_GAP_DP.dp))
        }
        // Respect the existing sender-name / preview switches without leaking a hidden title.
        val label = when {
            showName -> event.title
            showPreview -> event.body
            else -> "Chaty"
        }
        Text(label, modifier = Modifier.weight(1f, fill = false), color = foreground,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
