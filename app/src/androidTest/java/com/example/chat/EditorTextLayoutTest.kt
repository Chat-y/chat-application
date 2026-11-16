package com.example.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.ui.chats.detail.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source-only local text fixtures. NOT RUN under the overnight no-build instruction. */
@RunWith(AndroidJUnit4::class)
class EditorTextLayoutTest {
    @Test fun explicitLinesNormalizedBoundedAndSurrogateSafe() {
        assertEquals("a\nb\nc\nd", editorOverlayText("a\r\nb\rc\nd\ne"))
        assertEquals(120, editorOverlayText("x".repeat(200)).length)
        val bounded = editorOverlayText("x".repeat(119) + "\uD83D\uDE00")
        assertEquals(119, bounded.length)
        assertFalse(bounded.last().isHighSurrogate())
    }
    @Test fun previewHelperAndExportShareEveryAlignmentAndStripStyle() {
        EditorTextAlignment.entries.forEach { alignment ->
            listOf(false, true).forEach { strip ->
                val original = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                val preview = original.copy(Bitmap.Config.ARGB_8888, true)
                val exported = renderEditedImage(original, 0f, 1f, emptyList(), "Two lines\nSame layout",
                    textY = .95f, textColor = Color.BLACK, textAlignment = alignment, textStrip = strip)
                try {
                    drawEditorText(Canvas(preview), 480f, 320f, "Two lines\nSame layout",
                        .1f, .95f, .07f, Color.BLACK, alignment, strip)
                    assertTrue(preview.sameAs(exported))
                    assertFalse(original.sameAs(exported))
                    assertEquals(Color.WHITE, original.getPixel(240, 160))
                } finally { exported.recycle(); preview.recycle(); original.recycle() }
            }
        }
    }
    @Test fun veryShortPanoramaDoesNotThrowForMultilineText() {
        val bitmap = Bitmap.createBitmap(1000, 2, Bitmap.Config.ARGB_8888)
        try { drawEditorText(Canvas(bitmap), 1000f, 2f, "one\ntwo\nthree\nfour", .9f, .95f, .15f, Color.WHITE) }
        finally { bitmap.recycle() }
    }
}
