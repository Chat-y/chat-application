package com.example.chat.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.chat.ui.kit.ChatyMotion
import com.example.chat.ui.kit.rememberChatyReduceMotion
import kotlinx.coroutines.delay

/**
 * Row 9 (all-screens list): iOS-style content animations, reactions and smooth
 * motion across the app beyond Home.
 *
 * Semantics, stated precisely:
 *  - One-shot entrance only. Plays when an item first enters composition; like
 *    [listEntrance], a LazyColumn re-entry after being disposed replays it,
 *    matching the existing list-animation policy. Recomposition and reorder do
 *    NOT replay: remember(key) state travels with the keyed item.
 *  - Lightweight and battery-light: a single Animatable per item, applied only
 *    through graphicsLayer (alpha/translation/scale), so no re-measure or
 *    re-layout is triggered and no infinite transition runs. Settled items keep
 *    a constant 1f progress read.
 *  - Durations and curves come from [ChatyMotion] tokens only; no hard-coded
 *    colors and no new timing values beyond the stagger step below.
 *  - Honors the platform reduce-motion policy via [rememberChatyReduceMotion]:
 *    reduced motion renders every item fully settled with no animation.
 */
object IosContentMotion {
    /** Only the first screenful (indices 0 until this) animates; later items render settled. */
    const val maxStaggeredItems = 12

    /** Per-item stagger step. iOS lists cascade quickly; 24 ms reads as one wave. */
    const val staggerStepMs = 24

    /** Subtle rise distance, iOS-style (fade + small upward settle). */
    val entranceRise = 14.dp

    /** Slight grow-in; stays under 1f so it never overshoots layout bounds. */
    const val entranceScaleFrom = 0.97f

    /** Reaction-rail emoji start scale for the spring pop. */
    const val popScaleFrom = 0.2f

    /** Per-item delay; items outside the first screenful get NO delay (they render settled). */
    fun staggerDelayMs(index: Int): Int =
        if (index in 0 until maxStaggeredItems) index * staggerStepMs else 0
}

/**
 * iOS-style list/content entrance: fade + small rise + subtle scale, staggered
 * by [index]. No-op when reduce motion is on. [key] should be the item's stable
 * identity so list reorder does not replay the animation.
 */
@Composable
fun Modifier.iosItemEntrance(index: Int, key: Any?): Modifier {
    // Only the first screenful animates: later items render settled, with no
    // per-item Animatable/coroutine for large grids (battery-light).
    if (index < 0 || index >= IosContentMotion.maxStaggeredItems) return this
    if (rememberChatyReduceMotion()) return this
    val progress = remember(key) { Animatable(0f) }
    val settled = remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(IosContentMotion.staggerDelayMs(index).toLong())
        progress.animateTo(1f, tween(ChatyMotion.baseMs, easing = ChatyMotion.enter))
        settled.value = true
    }
    // State is read only inside the graphicsLayer lambda, so animation frames
    // update the draw layer without recomposing the item.
    return graphicsLayer {
        val p = if (settled.value) 1f else progress.value
        translationY = (1f - p) * IosContentMotion.entranceRise.toPx()
        val s = IosContentMotion.entranceScaleFrom + (1f - IosContentMotion.entranceScaleFrom) * p
        scaleX = s
        scaleY = s
        alpha = p
    }
}

/**
 * Identities whose entrance already played, hoisted above an unkeyed layout
 * (plain Column/Row content, or grids where an item can move across parents).
 * Membership is deliberately NOT Compose state: a played item must stay settled
 * without recomposition, and a not-yet-played item animates on composition.
 */
class IosEntrancePlayed internal constructor() {
    internal val playedIds = mutableSetOf<Any?>()
}

/** Remember one [IosEntrancePlayed] above the rows of an unkeyed layout. */
@Composable
fun rememberIosEntrancePlayed(): IosEntrancePlayed = remember { IosEntrancePlayed() }

/**
 * [iosItemEntrance] variant for unkeyed parents: once [key]'s entrance has
 * played, later compositions (reorder across rows/parents, recomposition)
 * render settled instead of replaying.
 */
@Composable
fun Modifier.iosItemEntrance(index: Int, key: Any?, played: IosEntrancePlayed): Modifier {
    if (index < 0 || index >= IosContentMotion.maxStaggeredItems) return this
    if (played.playedIds.contains(key)) return this
    if (rememberChatyReduceMotion()) return this
    val progress = remember(key) { Animatable(0f) }
    val settled = remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(IosContentMotion.staggerDelayMs(index).toLong())
        progress.animateTo(1f, tween(ChatyMotion.baseMs, easing = ChatyMotion.enter))
        settled.value = true
        played.playedIds.add(key)
    }
    // State is read only inside the graphicsLayer lambda, so animation frames
    // update the draw layer without recomposing the item.
    return graphicsLayer {
        val p = if (settled.value) 1f else progress.value
        translationY = (1f - p) * IosContentMotion.entranceRise.toPx()
        val s = IosContentMotion.entranceScaleFrom + (1f - IosContentMotion.entranceScaleFrom) * p
        scaleX = s
        scaleY = s
        alpha = p
    }
}

/**
 * iOS-style reaction pop-in: one-shot scale-up with the soft-spring token curve
 * (slight overshoot), optionally delayed by [delayMs] for staggered rails.
 * No-op when reduce motion is on.
 */
@Composable
fun Modifier.iosReactionPopIn(delayMs: Int = 0): Modifier {
    if (rememberChatyReduceMotion()) return this
    val scale = remember { Animatable(IosContentMotion.popScaleFrom) }
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        scale.animateTo(1f, tween(ChatyMotion.emphasisMs, easing = ChatyMotion.springSoft))
        settled.value = true
    }
    // State is read only inside the graphicsLayer lambda (no per-frame recomposition).
    return graphicsLayer {
        val s = if (settled.value) 1f else scale.value
        scaleX = s
        scaleY = s
        // springSoft overshoots above 1f; alpha must stay in range.
        alpha = s.coerceIn(0f, 1f)
    }
}
