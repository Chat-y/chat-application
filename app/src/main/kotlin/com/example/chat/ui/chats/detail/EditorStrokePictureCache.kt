package com.example.chat.ui.chats.detail

import android.graphics.Picture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil

/** MR-R2-026 partial preview cache only. Keep newest stroke live, cache preceding immutable strokes.
 * No bitmap ownership/export changes. Picture replay cost and actual pixels remain unmeasured.
 */
internal class EditorStrokePictureCache {
    private var cachedStrokes: List<EditorStroke> = emptyList()
    private var cachedSize = Size.Zero
    private var cachedDensity = 0f
    private var cachedFontScale = 0f
    private var cachedDirection = LayoutDirection.Ltr
    private var picture: Picture? = null

    fun draw(scope: DrawScope, strokes: List<EditorStroke>) = with(scope) {
        val count = (strokes.size - 1).coerceAtLeast(0)
        if (count == 0 || size.width <= 0f || size.height <= 0f) {
            picture = null
            cachedStrokes = emptyList()
        } else {
            val changed = picture == null || cachedSize != size || cachedDensity != density ||
                cachedFontScale != fontScale || cachedDirection != layoutDirection ||
                cachedStrokes.size != count || (0 until count).any { cachedStrokes[it] !== strokes[it] }
            if (changed) {
                val next = Picture()
                val canvas = next.beginRecording(ceil(size.width).toInt(), ceil(size.height).toInt())
                try {
                    CanvasDrawScope().draw(Density(density, fontScale), layoutDirection, Canvas(canvas), size) {
                        for (i in 0 until count) drawEditorPreviewStroke(strokes[i])
                    }
                } finally { next.endRecording() }
                picture = next
                cachedStrokes = List(count) { strokes[it] }
                cachedSize = size
                cachedDensity = density
                cachedFontScale = fontScale
                cachedDirection = layoutDirection
            }
            picture?.let { drawContext.canvas.nativeCanvas.drawPicture(it) }
        }
        // Newest stroke stays live even after lift, until another stroke is added.
        // No drag/tap/cancel recognition or stroke-lifecycle assumptions are introduced.
        strokes.lastOrNull()?.let { drawEditorPreviewStroke(it) }
    }
}

internal fun DrawScope.drawEditorPreviewStroke(stroke: EditorStroke) {
    val baseColor = Color(stroke.color.toInt())
    val color = baseColor.copy(alpha = baseColor.alpha * editorBrushOpacity(stroke.opacity))
    val widthPx = size.width * editorBrushWidth(stroke.widthFraction)
    if (stroke.points.size == 1) {
        val p = stroke.points.first()
        drawCircle(color, widthPx / 2f, Offset(p.x * size.width, p.y * size.height))
    }
    // MR-R2-026 prior: stroke.points.zipWithNext().forEach { (a, b) ->
    // Avoid a new pair/list allocation for every live preview draw.
    for (index in 1 until stroke.points.size) {
        val a = stroke.points[index - 1]
        val b = stroke.points[index]
        drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), widthPx, StrokeCap.Round)
    }
}
