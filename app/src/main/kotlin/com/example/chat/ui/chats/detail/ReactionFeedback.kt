package com.example.chat.ui.chats.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.chat.ui.kit.ChatyMotion

/** Render-only update feedback; no infinite transition, remeasure or payload mutation.
 * Caller keys each badge by message + emoji. Initial composition and scroll re-entry stay settled.
 * This is a lightweight spring-like approximation, not a claim of exact iOS timing. */
@Composable
internal fun Modifier.reactionFeedback(count: Int, selected: Boolean, reduceMotion: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(count to selected) }
    LaunchedEffect(count, selected, reduceMotion) {
        val changed = previous != (count to selected)
        previous = count to selected
        scale.snapTo(1f)
        if (changed && !reduceMotion) {
            scale.animateTo(ChatyMotion.reactionFeedbackScale, tween(ChatyMotion.instantMs, easing = ChatyMotion.enter))
            scale.animateTo(1f, tween(ChatyMotion.fastMs, easing = ChatyMotion.springGentle))
        }
    }
    return graphicsLayer {
        val value = if (reduceMotion) 1f else scale.value
        scaleX = value; scaleY = value
    }
}
