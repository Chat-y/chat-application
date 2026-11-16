package com.example.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.ui.chats.detail.editorOutputAliasesSource
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source fixtures only, NOT RUN. No app builds under the overnight instruction. */
@RunWith(AndroidJUnit4::class)
class EditorSourceAliasTest {
    @Test fun fileUriAndRawLocalSourceCannotBeTheirOwnOutput() {
        val source = File("/tmp/editor-original.jpg")
        assertTrue(editorOutputAliasesSource(source.toURI().toString(), source))
        assertTrue(editorOutputAliasesSource(source.path, source))
    }
    @Test fun canonicalDotSegmentsCannotBypassTheGuard() {
        assertTrue(editorOutputAliasesSource("/tmp/sub/../editor-original.jpg", File("/tmp/editor-original.jpg")))
    }
    @Test fun distinctCopyOrRemoteSourceIsNotALocalAlias() {
        assertFalse(editorOutputAliasesSource("/tmp/editor-original.jpg", File("/tmp/editor-copy.jpg")))
        assertFalse(editorOutputAliasesSource("https://example.invalid/photo.jpg", File("/tmp/editor-copy.jpg")))
        assertFalse(editorOutputAliasesSource("content://test/photo", File("/tmp/editor-copy.jpg")))
    }
}
