package com.example.chat

import com.example.chat.ui.media.*
import java.io.*
import java.time.Instant
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PickerInputPolicyTest {
    private fun descriptor(size: Long) = MediaFileDescriptor("id", "name", "/path", size, "text/plain", MediaCategory.document, Instant.EPOCH)
    @Test fun `only content providers pass the document boundary`() {
        requirePickerContentScheme("content"); requirePickerContentScheme("CONTENT")
        for (scheme in listOf(null, "file", "https", "data", "javascript", "intent")) {
            try { requirePickerContentScheme(scheme); fail("Expected foreign scheme rejection") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun `blank names have visible stable labels`() {
        assertEquals("Unnamed file", pickerFileLabel(""))
        assertEquals("Unnamed file", pickerFileLabel("  \t\n"))
        val safe = FileSanitizationService { File(System.getProperty("java.io.tmpdir")) }.sanitizeFileName("   ")
        assertEquals("unnamed_file", safe)
        assertEquals("application/pdf", FileTypeDetector.classifyBytes("%PDF-1.4".toByteArray(), safe).mimeType)
    }
    @Test fun `bounded query and labels never split surrogate pairs`() {
        val name = "a".repeat(511) + "\uD83D\uDE00" + "tail"
        assertEquals(511, boundedPickerText(name).length)
        assertEquals(511, pickerFileLabel(name).length)
        assertFalse(pickerFileLabel(name).last().isHighSurrogate())
        assertEquals(512, boundedPickerText("x".repeat(1000000)).length)
        assertEquals("  a  ", boundedPickerText("  a  "))
    }
    @Test fun `short reads assemble a complete header for classification`() {
        val data = "%PDF-1.7\nmore-content".toByteArray()
        val short = object : ByteArrayInputStream(data) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int = super.read(target, offset, minOf(length, 1))
        }
        val header = readPickerHeader(short)
        assertArrayEquals(data, header)
        assertEquals(MediaCategory.pdf, FileTypeDetector.classifyBytes(header).category)
    }
    @Test fun `zero bulk read makes bounded forward progress`() {
        val data = "%PDF".toByteArray()
        val input = object : ByteArrayInputStream(data) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int = if (available() > 0) 0 else -1
        }
        assertArrayEquals(data, readPickerHeader(input))
    }
    @Test fun `summary totals ignore negative descriptors and saturate`() {
        assertEquals(0, pickerTotalBytes(emptyList()))
        assertEquals(10, pickerTotalBytes(listOf(descriptor(-20), descriptor(10))))
        assertEquals(Long.MAX_VALUE, pickerTotalBytes(listOf(descriptor(Long.MAX_VALUE), descriptor(1))))
    }
    @Test fun `nonseekable unknown-length stream copies without random access`() = runBlocking {
        val data = ByteArray(10000) { (it % 256).toByte() }
        val input = object : InputStream() {
            var index = 0
            override fun available(): Int = 0
            override fun markSupported(): Boolean = false
            override fun skip(n: Long): Long = throw AssertionError("Must not seek")
            override fun reset(): Unit = throw AssertionError("Must not reset")
            override fun read(): Int = if (index < data.size) data[index++].toInt() and 255 else -1
        }
        val out = ByteArrayOutputStream()
        assertEquals(data.size.toLong(), copyMediaBounded(input, out, data.size.toLong()))
        assertArrayEquals(data, out.toByteArray())
    }
    @Test fun `never ending stream hits byte quota with no output overrun`() = runBlocking {
        val endless = object : InputStream() {
            override fun read(): Int = 1
            override fun read(target: ByteArray, offset: Int, length: Int): Int { java.util.Arrays.fill(target, offset, offset + length, 1.toByte()); return length }
        }
        val out = ByteArrayOutputStream()
        try { copyMediaBounded(endless, out, 8192); fail("Expected quota failure") } catch (_: IllegalArgumentException) { }
        assertEquals(8192, out.size())
    }
    @Test fun `never ending stream obeys coroutine cancellation`() = runBlocking {
        val endless = object : InputStream() { override fun read(): Int = 1 }
        val out = ByteArrayOutputStream()
        var canceled = false
        try { copyMediaBounded(endless, out, 100000) { throw CancellationException("cancel fixture") } }
        catch (_: CancellationException) { canceled = true }
        assertTrue(canceled); assertTrue(out.size() in 1 until 100000)
    }
    /** Source-only additions, NOT RUN under the overnight instruction. */
    @Test fun `display labels reveal bidi overrides without renaming sources`() {
        val original = "photo\u202Egpj.exe\u202C"
        assertEquals("photo\uFFFDgpj.exe\uFFFD", pickerFileLabel(original))
        assertTrue(original.contains('\u202E'))
        assertEquals("a b c", pickerFileLabel("a\nb\tc"))
        assertEquals("report.pdf", pickerFileLabel("report.pdf"))
    }
    @Test fun `mixed script and emoji labels retain natural content`() {
        assertEquals("تقرير.pdf", pickerFileLabel("تقرير.pdf"))
        assertEquals("family\u200Dphoto.png", pickerFileLabel("family\u200Dphoto.png"))
        for (codepoint in listOf(0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069)) {
            assertEquals("x\uFFFD.pdf", pickerFileLabel("x" + codepoint.toChar() + ".pdf"))
        }
    }
    /** Source-only zero-read copy fixture, NOT RUN. */
    @Test fun `bulk zero copy advances with single byte reads and honors quota`() = runBlocking {
        val stream = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int = if (available() > 0) 0 else -1
        }
        val output = ByteArrayOutputStream()
        assertEquals(3L, copyMediaBounded(stream, output, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }
}
