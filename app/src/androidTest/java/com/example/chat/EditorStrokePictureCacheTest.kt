package com.example.chat

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.chat.ui.chats.detail.EditorStroke
import com.example.chat.ui.chats.detail.EditorStrokePictureCache
import com.example.chat.ui.chats.detail.drawEditorPreviewStroke
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** MR-R2-026/028 software preview parity sources, NOT RUN.
 * Not hardware Picture replay, export parity, device pixels or performance proof.
 */
class EditorStrokePictureCacheTest {
    private fun stroke(color: Long = 0xffcc4488, opacity: Float = 1f, dot: Boolean = false) =
        EditorStroke(color, if (dot) listOf(Offset(.3f, .4f)) else listOf(Offset(.1f, .1f), Offset(.4f, .7f), Offset(.8f, .3f)), .03f, opacity)
    private fun pixels(strokes: List<EditorStroke>, cache: EditorStrokePictureCache?, width: Int = 120, height: Int = 90,
        density: Float = 1f, direction: LayoutDirection = LayoutDirection.Ltr): IntArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(density), direction, Canvas(android.graphics.Canvas(bitmap)), Size(width.toFloat(), height.toFloat())) {
            if (cache == null) strokes.forEach { drawEditorPreviewStroke(it) } else cache.draw(this, strokes)
        }
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
    }
    private fun parity(strokes: List<EditorStroke>, cache: EditorStrokePictureCache, width: Int = 120, height: Int = 90,
        density: Float = 1f, direction: LayoutDirection = LayoutDirection.Ltr) {
        assertArrayEquals(pixels(strokes, null, width, height, density, direction), pixels(strokes, cache, width, height, density, direction))
    }
    @Test fun emptyAndSingleStrokeStayLive() {
        val cache = EditorStrokePictureCache(); parity(emptyList(), cache); parity(listOf(stroke()), cache)
    }
    @Test fun dotsLinesOpacityAndOrderMatchLiveReference() {
        parity(listOf(stroke(dot = true), stroke(0x80ffffff, .4f), stroke(0xff223344)), EditorStrokePictureCache())
    }
    @Test fun newestStrokeReplacementDoesNotReuseStalePoints() {
        val cache = EditorStrokePictureCache(); val older = stroke()
        parity(listOf(older, stroke(dot = true)), cache)
        parity(listOf(older, stroke().copy(points = listOf(Offset(.5f, .1f), Offset(.9f, .9f)))), cache)
    }
    @Test fun undoRedoAndResetInvalidateCachedOlderStrokes() {
        val cache = EditorStrokePictureCache(); val a = stroke(); val b = stroke(0xff112233); val c = stroke(dot = true)
        parity(listOf(a, b, c), cache); parity(listOf(a, b), cache)
        parity(listOf(a, b, c), cache); parity(emptyList(), cache); parity(listOf(c), cache)
    }
    @Test fun changedOlderIdentityAndReorderInvalidatePicture() {
        val cache = EditorStrokePictureCache(); val a = stroke(); val b = stroke(0x70aabbcc); val c = stroke(dot = true)
        parity(listOf(a, b, c), cache); parity(listOf(b, a, c), cache)
        parity(listOf(a.copy(widthFraction = .1f), b, c), cache)
    }
    @Test fun viewportDensityAndDirectionChangesMatchLiveReference() {
        val cache = EditorStrokePictureCache(); val strokes = listOf(stroke(), stroke(dot = true))
        parity(strokes, cache); parity(strokes, cache, 90, 120)
        parity(strokes, cache, density = 2f); parity(strokes, cache, direction = LayoutDirection.Rtl)
    }
}
