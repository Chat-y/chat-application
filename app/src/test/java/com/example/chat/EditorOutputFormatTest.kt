package com.example.chat

import com.example.chat.ui.chats.detail.*
import org.junit.Assert.*
import org.junit.Test

class EditorOutputFormatTest {
    @Test fun opaqueProviderPngAndWebpDoNotBecomeJpeg() {
        assertEquals(EditorOutputFormat.PNG, editorOutputFormat("image/png", null, false))
        assertEquals(EditorOutputFormat.WEBP, editorOutputFormat("image/webp", null, false))
        assertEquals(EditorOutputFormat.WEBP, editorOutputFormat("IMAGE/WEBP; x=y", "jpg", true))
    }
    @Test fun headerSignaturesIdentifyOpaqueContentWithoutAnExtension() {
        assertEquals("image/png", editorHeaderMime(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)))
        assertEquals("image/webp", editorHeaderMime("RIFF1234WEBP".toByteArray()))
        assertEquals("image/jpeg", editorHeaderMime(byteArrayOf(255.toByte(),216.toByte(),255.toByte())))
        assertNull(editorHeaderMime("RIFF1234WAVE".toByteArray()))
        assertNull(editorHeaderMime(byteArrayOf(137.toByte(),80)))
    }
    @Test fun detectedMimeOutranksMisleadingSourceSuffix() {
        assertEquals(EditorOutputFormat.JPEG, editorOutputFormat("image/jpeg", "webp", false))
        assertEquals(EditorOutputFormat.PNG, editorOutputFormat("image/png", "webp", false))
    }
    @Test fun fallbackRetainsPngWebpAndUnknownAlpha() {
        assertEquals(EditorOutputFormat.PNG, editorOutputFormat(null, "PNG", false))
        assertEquals(EditorOutputFormat.WEBP, editorOutputFormat(null, "WebP", false))
        assertEquals(EditorOutputFormat.PNG, editorOutputFormat(null, null, true))
        assertEquals(EditorOutputFormat.JPEG, editorOutputFormat(null, null, false))
    }
    @Test fun fixedDestinationsAlwaysMatchTheirCodec() {
        assertEquals(EditorOutputFormat.PNG, editorOutputFormat("image/jpeg", "jpg", false, "png"))
        assertEquals(EditorOutputFormat.WEBP, editorOutputFormat("image/png", "png", true, "WEBP"))
        assertEquals(EditorOutputFormat.JPEG, editorOutputFormat("image/png", "png", true, "jpeg"))
        assertTrue(EditorOutputFormat.JPEG.flattenAlpha)
        assertFalse(EditorOutputFormat.WEBP.flattenAlpha)
        assertFalse(EditorOutputFormat.PNG.flattenAlpha)
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedDestinationIsNotWrittenAsJpeg() {
        editorOutputFormat(null, null, true, "gif")
    }
}
