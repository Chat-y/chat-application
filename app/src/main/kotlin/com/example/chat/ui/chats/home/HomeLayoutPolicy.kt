package com.example.chat.ui.chats.home

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** Temporary layout only. These values do not select, open or mark a conversation read. */
enum class HomePaneMax { None, List, Detail }

/**
 * Keep this state in the Home session, keyed by account. Do not persist divider changes unless
 * the user explicitly chooses to save a width preference. Folder/search/scroll and the selected
 * conversation remain owned by the existing Home/navigation state, not a second history here.
 */
data class HomePaneState(
    val dividerFraction: Float = 0.4f,
    val maximized: HomePaneMax = HomePaneMax.None,
) {
    fun withDivider(f: Float, hingeBounds: List<Rect> = emptyList()): HomePaneState =
        copy(dividerFraction = clampDivider(f, hingeBounds))

    /** Pressing the active maximize control again restores the split at its previous width. */
    fun toggleMaximized(pane: HomePaneMax): HomePaneState =
        copy(maximized = if (maximized == pane) HomePaneMax.None else pane)

    fun restoreSplit(): HomePaneState = copy(maximized = HomePaneMax.None)

    /**
     * Widths are layout-local dp, excluding an optional folder rail. Supply larger minimums for
     * large text if needed. A narrow window keeps an already selected detail visible; it does not
     * navigate or clear it. The caller must keep the list context alive when it is not displayed.
     *
     * [hingeBounds] uses the normalized coordinate contract of [clampDivider]. This is a seam for
     * future fold integration, not a WindowManager observer or an assertion of fold support.
     */
    fun layout(
        availableWidthDp: Float,
        detailOpen: Boolean,
        expandedListDetail: Boolean = true,
        dividerWidthDp: Float = 1f,
        minListWidthDp: Float = 360f,
        minDetailWidthDp: Float = 360f,
        hingeBounds: List<Rect> = emptyList(),
    ): HomePaneLayout {
        val fraction = clampDivider(dividerFraction, hingeBounds)
        val listMinimum = minListWidthDp.takeIf { it.isFinite() && it > 0f } ?: 360f
        val detailMinimum = minDetailWidthDp.takeIf { it.isFinite() && it > 0f } ?: 360f
        val dividerWidth = dividerWidthDp.takeIf { it.isFinite() && it >= 0f } ?: 1f
        val width = availableWidthDp - dividerWidth
        val canSplit = expandedListDetail && width.isFinite() && width > 0f &&
            width >= listMinimum + detailMinimum
        if (!canSplit) {
            return HomePaneLayout(showList = !detailOpen, showDetail = detailOpen, dividerFraction = fraction)
        }
        if (maximized == HomePaneMax.List) {
            return HomePaneLayout(showList = true, showDetail = false, dividerFraction = fraction)
        }
        if (maximized == HomePaneMax.Detail && detailOpen) {
            return HomePaneLayout(showList = false, showDetail = true, dividerFraction = fraction)
        }
        val lower = maxOf(0.2f, listMinimum / width)
        val upper = minOf(0.8f, 1f - detailMinimum / width)
        val fitted = if (lower <= upper) dividerCandidate(fraction, hingeBounds, lower, upper) else null
        return if (fitted != null) {
            HomePaneLayout(showList = true, showDetail = true, dividerFraction = fitted)
        } else {
            HomePaneLayout(showList = !detailOpen, showDetail = detailOpen, dividerFraction = fraction)
        }
    }
}

/** Both visible includes the empty-detail landing state when no conversation is selected. */
data class HomePaneLayout(
    val showList: Boolean,
    val showDetail: Boolean,
    val dividerFraction: Float,
) {
    val isSplit: Boolean get() = showList && showDetail
}

/**
 * Clamp a divider to 20..80 percent of the usable horizontal pane area. [hingeBounds] must be
 * layout-local Rects normalized to that same area's width/height (0..1), not screen pixels or dp.
 * The caller must convert actual fold bounds and account for divider thickness before passing
 * them. Ignore malformed/non-vertical bounds; do not reinterpret absolute coordinates as ratios.
 *
 * Snap away from the interior of supplied vertical obstructions, including overlapping ones.
 * If no split position remains, this scalar API returns the bounded requested fraction; callers
 * needing a safe single-pane fallback must use [HomePaneState.layout] instead. No fold source is
 * wired here. This function alone cannot establish readable pane sizes or fold support.
 */
fun clampDivider(f: Float, hingeBounds: List<Rect> = emptyList()): Float {
    val requested = if (f.isFinite()) f.coerceIn(0.2f, 0.8f) else 0.4f
    return dividerCandidate(requested, hingeBounds, 0.2f, 0.8f) ?: requested
}

private fun dividerCandidate(f: Float, hingeBounds: List<Rect>, lower: Float, upper: Float): Float? {
    val requested = f.coerceIn(lower, upper)
    val hinges = hingeBounds.filter {
        it.left.isFinite() && it.right.isFinite() && it.top.isFinite() && it.bottom.isFinite() &&
            it.left >= 0f && it.right <= 1f && it.top >= 0f && it.bottom <= 1f &&
            it.left < it.right && it.top < it.bottom && it.height > it.width
    }
    fun allowed(value: Float): Boolean = hinges.none { value > it.left && value < it.right }
    if (allowed(requested)) return requested
    // Test every boundary against every hinge so overlapping hinge interiors cannot win.
    val candidates = mutableListOf(lower, upper)
    hinges.forEach {
        if (it.left in lower..upper) candidates.add(it.left)
        if (it.right in lower..upper) candidates.add(it.right)
    }
    return candidates.filter { allowed(it) }
        .minWithOrNull(compareBy<Float> { abs(it - requested) }.thenBy { it })
}
