package com.example.chat.ui.chats.home

import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Attach before the existing row's clickable focus target. Do not add a second
 * focusable wrapper: keyboard activation and TalkBack stay with that target.
 * onContext must only open a menu, not reuse a selection or destructive action.
 * Primary clicks, touch long-press, scroll and drag remain with the existing row.
 */
fun Modifier.homeRowInput(
    onContext: () -> Unit,
    onHover: (Boolean) -> Unit,
    focus: FocusRequester?,
): Modifier = composed {
    val contextAction by rememberUpdatedState(onContext)
    val hoverAction by rememberUpdatedState(onHover)
    var contextKey by remember { mutableStateOf<Key?>(null) }
    val requester = if (focus != null) Modifier.focusRequester(focus) else Modifier

    this.then(requester)
        .onFocusChanged { if (!it.hasFocus) contextKey = null }
        .onPreviewKeyEvent { event ->
            val isContextKey = !event.isAltPressed && !event.isCtrlPressed &&
                !event.isMetaPressed &&
                (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))
            when {
                event.type == KeyEventType.KeyUp && contextKey == event.key -> {
                    contextKey = null
                    true
                }
                isContextKey && event.type == KeyEventType.KeyDown -> {
                    if (contextKey == null) {
                        contextKey = event.key
                        contextAction()
                    }
                    true
                }
                else -> false
            }
        }
        .pointerInput(Unit) {
            var hovering = false
            var secondaryDown = false
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Enter -> if (!hovering) {
                                hovering = true
                                hoverAction(true)
                            }
                            PointerEventType.Exit -> if (hovering) {
                                hovering = false
                                hoverAction(false)
                            }
                        }
                        // Consume the whole secondary-button sequence before
                        // child click handlers. No focus request, read or mutation.
                        val secondaryPressed = event.buttons.isSecondaryPressed
                        val openContext = secondaryPressed && !secondaryDown &&
                            event.type == PointerEventType.Press
                        if (secondaryPressed || secondaryDown) {
                            event.changes.forEach { it.consume() }
                        }
                        secondaryDown = secondaryPressed
                        if (openContext) contextAction()
                    }
                }
            } finally {
                if (hovering) hoverAction(false)
            }
        }
}

/**
 * Optional in-memory name prefix matching. Establishes a non-focusable list
 * focus group. Never attach above search, composer or other editable fields. labels
 * must contain only eligible displayed names, in the same order as onMatch uses.
 * Key the list subtree by account identity to discard state on account changes.
 * The caller scrolls to the match and requests focus; this never opens a chat.
 */
fun Modifier.homeTypeahead(
    labels: List<String>,
    onMatch: (Int) -> Unit,
    enabled: Boolean = true,
    timeoutMillis: Long = 900L,
): Modifier = composed {
    val matchAction by rememberUpdatedState(onMatch)
    val capturedKeys = remember { mutableSetOf<Key>() }
    var query by remember(labels, enabled) { mutableStateOf("") }
    var lastTypedAt by remember(labels, enabled) { mutableStateOf(0L) }
    var hasFocus by remember { mutableStateOf(false) }
    val idleMillis = timeoutMillis.coerceIn(100L, 5000L)

    LaunchedEffect(query, lastTypedAt, idleMillis, enabled, hasFocus) {
        if (enabled && hasFocus && query.isNotEmpty()) delay(idleMillis)
        query = ""
    }

    this.onFocusChanged {
        hasFocus = it.hasFocus
        if (!it.hasFocus) query = ""
    }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp && capturedKeys.remove(event.key)) {
            // Space in a multi-word prefix must not activate the matched row
            // on key-up after its key-down was consumed here.
            true
        } else if (!enabled || !hasFocus || event.type != KeyEventType.KeyDown ||
            event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
        ) {
            false
        } else {
            val codePoint = event.utf16CodePoint
            val printable = Character.isValidCodePoint(codePoint) &&
                !Character.isISOControl(codePoint) &&
                codePoint !in 0xD800..0xDFFF &&
                Character.getType(codePoint) != Character.FORMAT.toInt()
            val now = SystemClock.uptimeMillis()
            val prefix = if (now - lastTypedAt >= idleMillis) "" else query
            // A standalone Space belongs to row activation, not typeahead.
            if (!printable || (prefix.isEmpty() && Character.isWhitespace(codePoint))) {
                false
            } else {
                val typed = String(Character.toChars(codePoint))
                val next = prefix + typed
                query = if (next.codePointCount(0, next.length) > 128) {
                    next.substring(0, next.offsetByCodePoints(0, 128))
                } else {
                    next
                }
                lastTypedAt = now
                capturedKeys.add(event.key)
                val index = labels.indexOfFirst { it.startsWith(query, ignoreCase = true) }
                if (index >= 0) matchAction(index)
                true
            }
        }
    }.focusGroup()
}

/**
 * Outline uses a caller-supplied focus token, independent of selection/unread
 * fills. Observe the existing descendant focus target, without adding one.
 * A pointer press hides the ring without clearing focus or cancelling a click.
 */
fun Modifier.homeFocusRing(
    color: Color,
    width: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(8.dp),
): Modifier = composed {
    val inputMode = LocalInputModeManager.current
    var hasFocus by remember { mutableStateOf(false) }
    var pointerActivated by remember { mutableStateOf(false) }
    this.onFocusChanged {
        hasFocus = it.hasFocus
        if (!it.hasFocus) pointerActivated = false
    }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) pointerActivated = false
        false
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) pointerActivated = true
            }
        }
    }.then(
        if (hasFocus && inputMode.inputMode == InputMode.Keyboard && !pointerActivated) {
            Modifier.border(width, color, shape)
        } else {
            Modifier
        },
    )
}
