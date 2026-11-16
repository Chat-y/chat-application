package com.example.chat.ui.camera

import com.example.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import kotlin.math.roundToInt

// CAM-R4-004 prior: private val paperCornerLabels = listOf("Top left", "Top right", "Bottom right", "Bottom left")

/** CAM-R4-034 UI-only step, normalized to the existing image coordinate space. */
internal fun stepPaperCorner(quad: PaperQuad, corner: Int, dx: Int, dy: Int): PaperQuad {
    require(corner in 0..3)
    require(dx in -1..1 && dy in -1..1)
    val point = quad.points[corner]
    return quad.moved(corner, PaperPoint(point.x + dx * .01f, point.y + dy * .01f))
}

/** One real focus target, same existing quad edit callback as touch; no automatic crop/IO. */
internal fun Modifier.paperCornerAccessibility(
    quad: PaperQuad,
    enabled: Boolean,
    onQuadChange: (PaperQuad) -> Unit,
): Modifier = composed {
    var selected by remember { mutableIntStateOf(0) }
    val paperCornerLabels = listOf(
        stringResource(R.string.camera_corner_top_left), stringResource(R.string.camera_corner_top_right),
        stringResource(R.string.camera_corner_bottom_right), stringResource(R.string.camera_corner_bottom_left),
    )
    val selectLabels = paperCornerLabels.map { stringResource(R.string.camera_corner_select, it) }
    val moveLeft = stringResource(R.string.camera_corner_move_left)
    val moveRight = stringResource(R.string.camera_corner_move_right)
    val moveUp = stringResource(R.string.camera_corner_move_up)
    val moveDown = stringResource(R.string.camera_corner_move_down)
    val description = stringResource(R.string.camera_corner_adjust)
    val point = quad.points[selected]
    val positionDescription = stringResource(R.string.camera_corner_position,
        paperCornerLabels[selected], (point.x * 100).roundToInt(), (point.y * 100).roundToInt())
    fun move(dx: Int, dy: Int): Boolean {
        if (!enabled) return false
        onQuadChange(stepPaperCorner(quad, selected, dx, dy))
        return true
    }
    this.cameraActionFocusRing()
        .onKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.DirectionLeft -> move(-1, 0)
                Key.DirectionRight -> move(1, 0)
                Key.DirectionUp -> move(0, -1)
                Key.DirectionDown -> move(0, 1)
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    selected = (selected + if (event.isShiftPressed) 3 else 1) % 4
                    true
                }
                else -> false // Tab/Shift-Tab, Back and Escape remain host navigation.
            }
        }
        .focusable(enabled)
        .semantics {
            // CAM-R4-004 prior description: "Adjust paper corners"
            // Prior state: "${paperCornerLabels[selected]} corner, ${(point.x * 100).roundToInt()} percent across, ${(point.y * 100).roundToInt()} percent down. Enter selects next corner; arrow keys move one percent."
            contentDescription = description
            stateDescription = positionDescription
            if (!enabled) disabled()
            // CAM-R4-004 prior: paperCornerLabels.mapIndexed { index, label -> CustomAccessibilityAction("Select $label corner") ... }
            else customActions = selectLabels.mapIndexed { index, label ->
                CustomAccessibilityAction(label) { selected = index; true }
            } + listOf(
                CustomAccessibilityAction(moveLeft) /* prior label: "Move selected corner left" */ { move(-1, 0) },
                CustomAccessibilityAction(moveRight) /* prior label: "Move selected corner right" */ { move(1, 0) },
                CustomAccessibilityAction(moveUp) /* prior label: "Move selected corner up" */ { move(0, -1) },
                CustomAccessibilityAction(moveDown) /* prior label: "Move selected corner down" */ { move(0, 1) },
            )
        }
}
