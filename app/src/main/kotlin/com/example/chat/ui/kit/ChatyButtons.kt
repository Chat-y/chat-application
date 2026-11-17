package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Semantic presentation only; operation state/validation remain owned by the caller. */
enum class ChatyButtonTone { Default, Quiet, Destructive }

/** Shared button metrics preserve the existing default geometry. */
private object ChatyButtonMetrics {
    val touchHeight = 48.dp
    val touchWidth = 64.dp
    val horizontalInset = 16.dp
    val iconSize = 18.dp
    val iconGap = 8.dp
    val labelSize = 14.sp
}

/** A static busy glyph avoids an unbounded animation and keeps the measured label/content size.
 * [statusLabel] on the outer button is the caller's localized loading/error/success description.
 * Custom content remains caller-owned, including its own progress semantics.
 */
@Composable
private fun RowScope.ChatyButtonLabel(loading: Boolean, color: Color, content: @Composable RowScope.() -> Unit) {
    if (!loading) {
        // Preserve the original children directly under their outer Row, with no extra layout.
        content()
    } else Box(contentAlignment = Alignment.Center, modifier = Modifier.widthIn(min = ChatyButtonMetrics.iconSize)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(0f), content = content)
        com.example.chat.ui.icons.ChatyIcon(
            com.example.chat.ui.icons.ChatyIconId.hourglass,
            size = ChatyButtonMetrics.iconSize, color = color,
        )
    }
}

private fun Modifier.chatyButtonStatus(statusLabel: String?): Modifier =
    if (statusLabel == null) this else semantics { stateDescription = statusLabel }

/**
 * Chaty text action: capsule hit area, accent label, optional leading icon,
 * 48dp minimum touch height. Replaces stock Material TextButton in Chaty chrome.
 */
@Composable
fun ChatyTextButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color? = null,
    enabled: Boolean = true,
    fontWeight: FontWeight = FontWeight.W600,
    tone: ChatyButtonTone = ChatyButtonTone.Default,
    loading: Boolean = false,
    statusLabel: String? = null,
) {
    val colors = LocalAppColors.current
    val contentColor = when {
        !enabled -> colors.disabledForeground
        color != null -> color
        tone == ChatyButtonTone.Destructive -> colors.error
        tone == ChatyButtonTone.Quiet -> colors.foreground
        else -> colors.accent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            // Previous minimum constrained only height; short labels still need a button-width floor.
            .widthIn(min = ChatyButtonMetrics.touchWidth)
            .heightIn(min = ChatyButtonMetrics.touchHeight)
            .clip(ChatyActionShape)
            // Previous: clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // Loading is opt-in; existing caller guards are unchanged when false.
            .chatyButtonStatus(statusLabel)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = ChatyButtonMetrics.horizontalInset, vertical = 8.dp),
    ) {
        ChatyButtonLabel(loading, contentColor) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(ChatyButtonMetrics.iconSize))
                Spacer(Modifier.width(ChatyButtonMetrics.iconGap))
            }
            Text(label, color = contentColor, fontWeight = fontWeight, fontSize = ChatyButtonMetrics.labelSize)
        }
    }
}

/**
 * Chaty filled action: capsule container in the theme accent, optional leading
 * icon, 48dp minimum touch height. Caller content is allowed for busy spinners
 * and multi-part labels. Replaces stock Material Button in Chaty chrome.
 */
/** Original signature kept for positional content and trailing-lambda source compatibility. */
@Composable
fun ChatyActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    content: (@Composable RowScope.() -> Unit)? = null,
) = ChatyActionButton(
    onClick = onClick, modifier = modifier, label = label, icon = icon,
    containerColor = containerColor, contentColor = contentColor, enabled = enabled,
    tone = ChatyButtonTone.Default, content = content,
)

/** Extended presentation. Supply a tone explicitly to opt in to loading/status as well. */
@Composable
fun ChatyActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    tone: ChatyButtonTone,
    loading: Boolean = false,
    statusLabel: String? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val container = when {
        !enabled -> colors.disabled
        containerColor != null -> containerColor
        tone == ChatyButtonTone.Destructive -> colors.error
        tone == ChatyButtonTone.Quiet -> colors.surface
        else -> colors.accent
    }
    val foreground = when {
        !enabled -> colors.disabledForeground
        contentColor != null -> contentColor
        tone == ChatyButtonTone.Destructive -> colors.onError
        tone == ChatyButtonTone.Quiet -> colors.foreground
        else -> colors.onAccent
    }
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                // Previous minimum constrained only height; short labels still need a button-width floor.
            .widthIn(min = ChatyButtonMetrics.touchWidth)
            .heightIn(min = ChatyButtonMetrics.touchHeight)
                .clip(ChatyActionShape)
                .background(container)
                // Previous: clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                // Loading is opt-in; existing caller guards are unchanged when false.
                .chatyButtonStatus(statusLabel)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
                .padding(horizontal = ChatyButtonMetrics.horizontalInset, vertical = 10.dp),
        ) {
            if (content != null) {
                content()
            } else ChatyButtonLabel(loading, foreground) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(ChatyButtonMetrics.iconSize))
                    Spacer(Modifier.width(ChatyButtonMetrics.iconGap))
                }
                if (label != null) {
                    Text(label, fontWeight = FontWeight.W700, fontSize = ChatyButtonMetrics.labelSize)
                }
            }
        }
    }
}

// Previous button implementation retained inactive for review.
// package com.example.chat.ui.kit
//
// import androidx.compose.foundation.background
// import androidx.compose.foundation.clickable
// import androidx.compose.foundation.layout.Arrangement
// import androidx.compose.foundation.layout.Row
// import androidx.compose.foundation.layout.RowScope
// import androidx.compose.foundation.layout.Spacer
// import androidx.compose.foundation.layout.heightIn
// import androidx.compose.foundation.layout.padding
// import androidx.compose.foundation.layout.size
// import androidx.compose.foundation.layout.width
// import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
// import androidx.compose.material3.LocalContentColor
// import androidx.compose.material3.Text
// import androidx.compose.runtime.Composable
// import androidx.compose.runtime.CompositionLocalProvider
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.draw.clip
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.graphics.vector.ImageVector
// import androidx.compose.ui.semantics.Role
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp
// import com.example.chat.ui.theme.LocalAppColors
//
// /**
//  * Chaty text action: capsule hit area, accent label, optional leading icon,
//  * 48dp minimum touch height. Replaces stock Material TextButton in Chaty chrome.
//  */
// @Composable
// fun ChatyTextButton(
//     onClick: () -> Unit,
//     label: String,
//     modifier: Modifier = Modifier,
//     icon: ImageVector? = null,
//     color: Color? = null,
//     enabled: Boolean = true,
//     fontWeight: FontWeight = FontWeight.W600,
// ) {
//     val colors = LocalAppColors.current
//     val contentColor = when {
//         !enabled -> colors.disabledForeground
//         color != null -> color
//         else -> colors.accent
//     }
//     Row(
//         verticalAlignment = Alignment.CenterVertically,
//         horizontalArrangement = Arrangement.Center,
//         modifier = modifier
//             .heightIn(min = 48.dp)
//             .clip(ChatyActionShape)
//             .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
//             .padding(horizontal = 16.dp, vertical = 8.dp),
//     ) {
//         if (icon != null) {
//             Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
//             Spacer(Modifier.width(8.dp))
//         }
//         Text(label, color = contentColor, fontWeight = fontWeight, fontSize = 14.sp)
//     }
// }
//
// /**
//  * Chaty filled action: capsule container in the theme accent, optional leading
//  * icon, 48dp minimum touch height. Caller content is allowed for busy spinners
//  * and multi-part labels. Replaces stock Material Button in Chaty chrome.
//  */
// @Composable
// fun ChatyActionButton(
//     onClick: () -> Unit,
//     modifier: Modifier = Modifier,
//     label: String? = null,
//     icon: ImageVector? = null,
//     containerColor: Color? = null,
//     contentColor: Color? = null,
//     enabled: Boolean = true,
//     content: (@Composable RowScope.() -> Unit)? = null,
// ) {
//     val colors = LocalAppColors.current
//     val container = when {
//         !enabled -> colors.disabled
//         containerColor != null -> containerColor
//         else -> colors.accent
//     }
//     val foreground = when {
//         !enabled -> colors.disabledForeground
//         contentColor != null -> contentColor
//         else -> colors.onAccent
//     }
//     CompositionLocalProvider(LocalContentColor provides foreground) {
//         Row(
//             verticalAlignment = Alignment.CenterVertically,
//             horizontalArrangement = Arrangement.Center,
//             modifier = modifier
//                 .heightIn(min = 48.dp)
//                 .clip(ChatyActionShape)
//                 .background(container)
//                 .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
//                 .padding(horizontal = 16.dp, vertical = 10.dp),
//         ) {
//             if (content != null) {
//                 content()
//             } else {
//                 if (icon != null) {
//                     Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
//                     Spacer(Modifier.width(8.dp))
//                 }
//                 if (label != null) {
//                     Text(label, fontWeight = FontWeight.W700, fontSize = 14.sp)
//                 }
//             }
//         }
//     }
// }

// Complete original-line retention for review (inactive).
                //                 .heightIn(min = ChatyButtonMetrics.touchHeight)
