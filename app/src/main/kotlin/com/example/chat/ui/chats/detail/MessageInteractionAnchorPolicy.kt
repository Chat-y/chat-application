package com.example.chat.ui.chats.detail

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * WhatsApp/Telegram-style message interaction anchor computation.
 *
 * Given the bubble's coordinates in the message region viewport:
 * 1. Reaction rail is anchored ABOVE the bubble if there is sufficient headroom (>= railHeight + margin),
 *    otherwise it is placed immediately BELOW the bubble.
 * 2. Contextual action menu is anchored BELOW the reaction rail (or below the bubble).
 * 3. Menu and rail coordinates are strictly clamped within [0, availableWidth] and [0, availableHeight]
 *    without double-subtracting IME insets (since the parent message region already applies imePadding).
 */
data class MessageInteractionPlacement(
    val reactionRailTopDp: Dp,
    val reactionRailLeftDp: Dp,
    val menuTopDp: Dp,
    val menuLeftDp: Dp,
    val menuMaxHeightDp: Dp,
    val menuMaxWidthDp: Dp = 260.dp,
    val reactionRailMaxWidthDp: Dp = 260.dp,
    val reactionRailMaxHeightDp: Dp = 48.dp,
)

object MessageInteractionAnchorPolicy {

    fun computePlacement(
        bubbleBounds: Rect?,
        viewportWidthDp: Dp,
        viewportHeightDp: Dp,
        density: Float,
        isOutgoing: Boolean,
        railHeightDp: Dp = 48.dp,
        menuWidthDp: Dp = 260.dp,
        marginDp: Dp = 8.dp,
    ): MessageInteractionPlacement {
        fun extent(value: Float): Float = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
        val width = extent(viewportWidthDp.value)
        val height = extent(viewportHeightDp.value)
        val margin = extent(marginDp.value).coerceAtMost(minOf(width, height) / 2f)
        val railHeight = extent(railHeightDp.value).coerceAtMost((height - 2f * margin).coerceAtLeast(0f))
        val menuWidth = extent(menuWidthDp.value).coerceAtMost((width - 2f * margin).coerceAtLeast(0f))
        val usableBounds = bubbleBounds?.takeIf {
            density.isFinite() && density > 0f &&
                it.left.isFinite() && it.right.isFinite() && it.top.isFinite() && it.bottom.isFinite() &&
                it.right >= it.left && it.bottom >= it.top
        }
        val left = (usableBounds?.let { it.left / density } ?: margin).coerceIn(margin, width - margin)
        val right = (usableBounds?.let { it.right / density } ?: width - margin).coerceIn(margin, width - margin)
        val top = (usableBounds?.let { it.top / density } ?: height * 0.35f).coerceIn(margin, height - margin)
        val bottom = (usableBounds?.let { it.bottom / density } ?: top + 60f).coerceIn(margin, height - margin)
        val idealLeft = if (isOutgoing) right - menuWidth else left
        val menuLeft = idealLeft.coerceIn(margin, (width - menuWidth - margin).coerceAtLeast(margin))
        val railAbove = top - margin >= railHeight + margin
        val railTop = (if (railAbove) top - railHeight - margin else bottom + margin)
            .coerceIn(margin, (height - railHeight - margin).coerceAtLeast(margin))
        // 120dp is a preferred room allocation, never a minimum larger than the viewport.
        val preferredMenuHeight = 120f.coerceAtMost((height - 2f * margin).coerceAtLeast(0f))
        val idealMenuTop = if (railAbove) bottom + margin else railTop + railHeight + margin
        val menuTop = idealMenuTop.coerceIn(margin, (height - preferredMenuHeight - margin).coerceAtLeast(margin))
        val availableMenuHeight = (height - menuTop - margin).coerceAtLeast(0f)

        return MessageInteractionPlacement(
            reactionRailTopDp = railTop.dp,
            reactionRailLeftDp = menuLeft.dp,
            menuTopDp = menuTop.dp,
            menuLeftDp = menuLeft.dp,
            menuMaxHeightDp = availableMenuHeight.dp,
            menuMaxWidthDp = menuWidth.dp,
            reactionRailMaxWidthDp = (width - menuLeft - margin).coerceAtLeast(0f).dp,
            reactionRailMaxHeightDp = railHeight.dp,
        )
    }
}

/* Previous fixed-minimum geometry retained as disabled reference per the source-edit rules.
        if (density <= 0f || viewportWidthDp <= 0.dp || viewportHeightDp <= 0.dp) {
            return MessageInteractionPlacement(
                reactionRailTopDp = marginDp,
                reactionRailLeftDp = marginDp,
                menuTopDp = marginDp + railHeightDp + marginDp,
                menuLeftDp = marginDp,
                menuMaxHeightDp = (viewportHeightDp - (marginDp * 3) - railHeightDp).coerceAtLeast(100.dp),
            )
        }

        val bubbleLeftDp = if (bubbleBounds != null) (bubbleBounds.left / density).dp else marginDp
        val bubbleRightDp = if (bubbleBounds != null) (bubbleBounds.right / density).dp else viewportWidthDp - marginDp
        val bubbleTopDp = if (bubbleBounds != null) (bubbleBounds.top / density).dp else (viewportHeightDp * 0.35f)
        val bubbleBottomDp = if (bubbleBounds != null) (bubbleBounds.bottom / density).dp else bubbleTopDp + 60.dp

        // Compute horizontal alignment for menu and rail
        val idealLeftDp = if (isOutgoing) {
            (bubbleRightDp - menuWidthDp).coerceAtLeast(marginDp)
        } else {
            bubbleLeftDp.coerceAtLeast(marginDp)
        }
        val clampedMenuLeftDp = idealLeftDp.coerceIn(marginDp, (viewportWidthDp - menuWidthDp - marginDp).coerceAtLeast(marginDp))

        // Check if there is enough vertical space above the bubble for reaction rail
        val spaceAbove = bubbleTopDp - marginDp
        val railAbove = spaceAbove >= (railHeightDp + marginDp)

        val railTopDp: Dp
        val menuTopDp: Dp

        if (railAbove) {
            // Reaction rail sits above the bubble
            railTopDp = (bubbleTopDp - railHeightDp - marginDp).coerceAtLeast(marginDp)
            // Menu sits below the bubble
            menuTopDp = (bubbleBottomDp + marginDp).coerceAtMost((viewportHeightDp - 120.dp).coerceAtLeast(marginDp))
        } else {
            // Both rail and menu must go below the bubble
            railTopDp = (bubbleBottomDp + marginDp).coerceAtMost((viewportHeightDp - railHeightDp - marginDp).coerceAtLeast(marginDp))
            menuTopDp = (railTopDp + railHeightDp + marginDp).coerceAtMost((viewportHeightDp - 60.dp).coerceAtLeast(marginDp))
        }

        val availableMenuHeight = (viewportHeightDp - menuTopDp - marginDp).coerceAtLeast(120.dp)

        return MessageInteractionPlacement(
            reactionRailTopDp = railTopDp,
            reactionRailLeftDp = clampedMenuLeftDp,
            menuTopDp = menuTopDp,
            menuLeftDp = clampedMenuLeftDp,
            menuMaxHeightDp = availableMenuHeight,
        )
*/
