package com.example.chat

import com.example.chat.ui.media.ConversionType
import com.example.chat.ui.media.FileSanitizationService
import com.example.chat.ui.media.PdfProcessingService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** DOC-R5-211 / DOC-R5-212 / DOC-R5-213: pinning the escape-aware Tj string scanner. */
class PdfTextScannerTest {

    private fun svc() = PdfProcessingService(FileSanitizationService { File("/tmp") })

    @Test fun `plain literal string`() {
        assertEquals(listOf("Hello World"), svc().scanTjStrings("BT (Hello World) Tj ET"))
    }

    @Test fun `escaped parentheses survive`() {
        assertEquals(listOf("A (B) C"), svc().scanTjStrings("BT (A \\(B\\) C) Tj ET"))
    }

    @Test fun `nested balanced parentheses survive`() {
        assertEquals(listOf("Nested (inner) text"), svc().scanTjStrings("BT (Nested (inner) text) Tj ET"))
    }

    @Test fun `escaped backslash stays literal`() {
        assertEquals(listOf("line\\nope"), svc().scanTjStrings("BT (line\\\\nope) Tj ET"))
    }

    @Test fun `tab escape decodes`() {
        assertEquals(listOf("tab\there"), svc().scanTjStrings("BT (tab\\there) Tj ET"))
    }

    @Test fun `octal escape decodes`() {
        assertEquals(listOf("octal space"), svc().scanTjStrings("BT (octal\\040space) Tj ET"))
    }

    @Test fun `line continuation folds`() {
        assertEquals(listOf("contline"), svc().scanTjStrings("BT (cont\\\nline) Tj ET"))
    }

    @Test fun `strings not shown by Tj are skipped`() {
        assertEquals(emptyList<String>(), svc().scanTjStrings("BT (not shown) Tf ET"))
    }

    @Test fun `hex string decodes`() {
        assertEquals(listOf("Hello"), svc().scanTjStrings("BT <48656C6C6F> Tj ET"))
    }

    @Test fun `non printable hex string is rejected`() {
        assertEquals(emptyList<String>(), svc().scanTjStrings("BT <00FF00FF> Tj ET"))
    }

    @Test fun `unclosed string yields nothing`() {
        assertEquals(emptyList<String>(), svc().scanTjStrings("BT (unclosed Tj ET"))
    }

    @Test fun `multiple Tj strings in one block`() {
        assertEquals(listOf("a", "b"), svc().scanTjStrings("BT (a) Tj (b) Tj ET"))
    }
}
