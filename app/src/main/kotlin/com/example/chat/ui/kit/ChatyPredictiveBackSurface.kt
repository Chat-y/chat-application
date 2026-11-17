package com.example.chat.ui.kit

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/** Presentation only. No route, draft, credential, saved content or navigation snapshot is held. */
internal class BackVisualState {
    var progress by mutableFloatStateOf(0f)
    var direction by mutableFloatStateOf(1f)
    var gesture: Any? = null
}

private val LocalBackVisualState = staticCompositionLocalOf<BackVisualState?> { null }

internal fun backVisualProgress(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

/**
 * Root routes get a themed backdrop, not a copy of the previous route. Pushed pages leave the
 * existing underlay visible. This is a finger-tracked current-surface preview, not a back-stack
 * destination preview. resetKey clears presentation on route/account changes without recreating
 * the content or retaining an exiting credential screen.
 */
@Composable
internal fun ChatyPredictiveBackSurface(
    resetKey: Any? = Unit,
    rootBackdrop: Boolean = false,
    fillAvailable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberChatyBackPresentation(resetKey)
    val reduceMotion = !rememberSystemAnimationsAllowed() || LocalThemeConfig.current.animationLevel <= 0.0 ||
        LocalReferenceLayoutPresentation.current.animationsDisabled
    val backdrop = if (rootBackdrop) Modifier.background(LocalAppColors.current.background) else Modifier
    CompositionLocalProvider(LocalBackVisualState provides state) {
        // Previous inline transform retained inactive; the same math is shared below.
        // Box(Modifier.fillMaxSize().then(backdrop)) {
        //     Box(Modifier.fillMaxSize().graphicsLayer {
        //         val p = if (reduceMotion) 0f else backVisualProgress(state.progress)
        //         scaleX = 1f - 0.08f * p
        //         scaleY = 1f - 0.08f * p
        //         translationX = size.width * 0.08f * p * state.direction
        //     }) { content() }
        // }
        val size = if (fillAvailable) Modifier.fillMaxSize() else Modifier
        Box(size.then(backdrop)) {
            Box(size.chatyPredictiveBack(state, enabled = !reduceMotion)) { content() }
        }
    }
}

/**
 * Replace an existing BackHandler IN PLACE, keeping its enabled expression and callback.
 * No additional global owner is registered. Later child handlers still take precedence.
 * A cancelled gesture never invokes onBack. A committed gesture invokes the CURRENT guard
 * exactly once, after visuals are reset, so a refused dirty/saving guard stays on a resting UI.
 */
@Composable
internal fun ChatyPredictiveBackHandler(
    enabled: Boolean = true,
    presentation: BackVisualState? = LocalBackVisualState.current,
    onBack: () -> Unit,
) {
    val state = presentation
    val latestState by rememberUpdatedState(state)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        val target = latestState
        val token = Any()
        target?.gesture = token
        try {
            events.collect { event ->
                if (!latestEnabled || target !== latestState) throw CancellationException("Back surface changed")
                target?.direction = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                target?.progress = backVisualProgress(event.progress)
            }
        } finally {
            // Do not let cancellation from an earlier gesture reset a newer gesture.
            if (target != null && target.gesture === token) {
                target.progress = 0f
                target.gesture = null
            }
        }
        // On cancellation collect throws, so execution cannot reach this callback.
        if (latestEnabled && target === latestState) latestBack()
    }
}

/** Local owner identity. Nothing about the prior route or its data is retained. */
@Composable
internal fun rememberChatyBackPresentation(resetKey: Any? = Unit): BackVisualState {
    val account = com.example.chat.ServiceLocator.apiBackend.authSnapshot()
    return remember(account, resetKey) { BackVisualState() }
}

/** Transform an existing measured surface, without adding a layout or changing its bounds. */
internal fun Modifier.chatyPredictiveBack(state: BackVisualState, enabled: Boolean = true): Modifier = composed {
    val reduceMotion = !rememberSystemAnimationsAllowed() || LocalThemeConfig.current.animationLevel <= 0.0 ||
        LocalReferenceLayoutPresentation.current.animationsDisabled
    this.graphicsLayer {
        val p = if (reduceMotion || !enabled) 0f else backVisualProgress(state.progress)
        scaleX = 1f - 0.08f * p
        scaleY = 1f - 0.08f * p
        translationX = size.width * 0.08f * p * state.direction
    }
}
