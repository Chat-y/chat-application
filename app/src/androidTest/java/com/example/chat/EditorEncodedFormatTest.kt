package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import com.example.chat.ui.chats.detail.*
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native codec/content-URI fixtures. Not a screenshot or Compose interaction test. */
@RunWith(AndroidJUnit4::class)
class EditorEncodedFormatTest {
    @Test fun pngAndWebpEncodeMatchingSignaturesAndPreserveAlpha() {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        try {
            for (format in listOf(EditorOutputFormat.PNG, EditorOutputFormat.WEBP)) {
                val bytes = ByteArrayOutputStream().use { out -> assertTrue(compressEditorImage(source, format, out)); out.toByteArray() }
                assertEquals(format.mimeType, editorHeaderMime(bytes))
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                try { assertEquals(0, Color.alpha(decoded.getPixel(8, 8))) }
                finally { decoded.recycle() }
            }
            assertEquals(0, Color.alpha(source.getPixel(8, 8)))
        } finally { source.recycle() }
    }
    @Test fun opaquePngWithoutSuffixIsDetectedThroughContentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("editor-format-", ".bin", context.cacheDir)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED); setHasAlpha(false) }
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            val before = file.readBytes()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            assertEquals("content", uri.scheme)
            assertEquals("image/png", editorSourceMime(context,uri.toString()))
            assertEquals(EditorOutputFormat.PNG,editorOutputFormat(editorSourceMime(context,uri.toString()),null,false))
            assertArrayEquals(before,file.readBytes())
        } finally { bitmap.recycle(); file.delete() }
    }
}
