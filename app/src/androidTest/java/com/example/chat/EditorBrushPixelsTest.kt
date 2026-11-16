package com.example.chat

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.ui.chats.detail.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source fixtures only. NOT RUN under the no-build instruction. */
@RunWith(AndroidJUnit4::class)
class EditorBrushPixelsTest {
    @Test fun brushDefaultsPreserveExistingWidthAndOpacity() {
        val stroke = EditorStroke(Color.RED.toLong(), emptyList())
        assertEquals(0.008f, stroke.widthFraction, 0f); assertEquals(1f, stroke.opacity, 0f)
        assertEquals(0.008f, editorBrushWidth(Float.NaN), 0f)
        assertEquals(1f, editorBrushOpacity(Float.NaN), 0f)
    }
    @Test fun chosenStrokeWidthsAreStoredPerStrokeAndChangeExportCoverage() {
        val original = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val fine = EditorStroke(Color.RED.toLong(), listOf(Offset(.1f, .25f), Offset(.9f, .25f)), .004f)
        val thick = EditorStroke(Color.BLUE.toLong(), listOf(Offset(.1f, .75f), Offset(.9f, .75f)), .016f)
        val edited = renderEditedImage(original, 0f, 1f, listOf(fine, thick))
        try {
            assertEquals(Color.WHITE, edited.getPixel(500, 256))
            assertTrue(Color.blue(edited.getPixel(500, 756)) > 240)
            assertTrue(Color.red(edited.getPixel(500, 756)) < 30)
            assertEquals(Color.WHITE, original.getPixel(500, 750))
        } finally { edited.recycle(); original.recycle() }
    }
    @Test fun halfOpacityBlendsRatherThanReplacesOriginalPixels() {
        val original = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val stroke = EditorStroke(Color.RED.toLong(), listOf(Offset(.1f, .5f), Offset(.9f, .5f)), .016f, .5f)
        val edited = renderEditedImage(original, 0f, 1f, listOf(stroke))
        try {
            val pixel = edited.getPixel(250, 250)
            assertTrue(Color.red(pixel) > 240); assertTrue(Color.green(pixel) in 110..145); assertTrue(Color.blue(pixel) in 110..145)
        } finally { edited.recycle(); original.recycle() }
    }
}
