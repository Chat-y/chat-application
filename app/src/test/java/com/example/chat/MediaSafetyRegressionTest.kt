package com.example.chat

import com.example.chat.ui.media.*
import java.io.*
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MediaSafetyRegressionTest {
    private fun temp() = java.nio.file.Files.createTempDirectory("chaty-media-test-").toFile().apply { deleteOnExit() }
    private fun service() = PdfProcessingService(FileSanitizationService { temp() })
    private fun png(width: Int = 2, height: Int = 1, color: Int = 6, pixels: ByteArray = byteArrayOf(0, -1, 0, 0, -1, 0, 0, -1, 100)): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun chunk(type: String, bytes: ByteArray) {
            val data = DataOutputStream(out); val tag = type.toByteArray()
            data.writeInt(bytes.size); data.write(tag); data.write(bytes)
            data.writeInt(CRC32().apply { update(tag); update(bytes) }.value.toInt())
        }
        val header = ByteArrayOutputStream().also { val data = DataOutputStream(it); data.writeInt(width); data.writeInt(height); data.write(byteArrayOf(8, color.toByte(), 0, 0, 0)) }.toByteArray()
        chunk("IHDR", header)
        val compressed = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(pixels) } }.toByteArray()
        chunk("IDAT", compressed); chunk("IEND", byteArrayOf())
        return out.toByteArray()
    }
    private fun file(bytes: ByteArray, suffix: String) = File(temp(), "input.$suffix").apply { writeBytes(bytes) }
    private fun assertRejects(action: () -> Unit) { try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }

    @Test fun `safe paths reject prefix siblings and traversal`() {
        val dir = temp(); val sanitizer = FileSanitizationService { dir }
        assertTrue(sanitizer.validateSafePath(dir.path, File(dir, "file.pdf").path))
        assertFalse(sanitizer.validateSafePath(dir.path, dir.path + "-sibling/file.pdf"))
        assertFalse(sanitizer.validateSafePath(dir.path, File(dir, "../outside.pdf").path))
    }
    @Test fun `filenames remove direction controls and dot aliases`() {
        val sanitizer = FileSanitizationService { temp() }
        assertEquals("unnamed_file", sanitizer.sanitizeFileName(".."))
        assertEquals("unnamed_file", sanitizer.sanitizeFileName("  .  "))
        assertFalse(sanitizer.sanitizeFileName("a\u202Egpj.exe").contains('\u202E'))
    }
    @Test fun `bounded copy stops before quota and keeps exact success bytes`() = runBlocking {
        val bytes = ByteArray(100) { it.toByte() }; val out = ByteArrayOutputStream()
        assertEquals(100L, copyMediaBounded(ByteArrayInputStream(bytes), out, 100))
        assertArrayEquals(bytes, out.toByteArray())
        try { copyMediaBounded(ByteArrayInputStream(bytes), ByteArrayOutputStream(), 99); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun `bounded copy respects cancellation`() = runBlocking {
        val job = launch(start = CoroutineStart.LAZY) { copyMediaBounded(ByteArrayInputStream(ByteArray(100)), ByteArrayOutputStream(), 100) }
        job.cancel(); job.join(); assertTrue(job.isCancelled)
    }
    @Test fun `cleanup reports old files without deleting fresh files`() = runBlocking {
        val dir = temp(); val sanitizer = FileSanitizationService { dir }; val scoped = sanitizer.getScopedTempDirectory()
        val old = File(scoped, "old").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000) }
        val recent = File(scoped, "recent").apply { writeText("x") }
        val result = sanitizer.cleanExpiredTempFiles()
        assertEquals(1, result.deleted); assertEquals(1, result.skipped); assertEquals(0, result.failed)
        assertFalse(old.exists()); assertTrue(recent.exists())
    }
    @Test fun `PNG gets actual dimensions alpha mask and valid xref offsets`() {
        val pdf = service().buildPdfFromImages(listOf(file(png(), "png")))
        val body = String(pdf, Charsets.ISO_8859_1)
        assertTrue(body.contains("/Width 2 /Height 1")); assertTrue(body.contains("/SMask"))
        assertTrue(body.contains("523.0000 0 0 261.5000"))
        assertFalse(body.contains("/DCTDecode"))
        val start = body.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        assertTrue(body.substring(start).startsWith("xref\n"))
        val entries = body.substring(start).lineSequence().drop(3).takeWhile { !it.startsWith("trailer") }.toList()
        entries.forEachIndexed { index, entry -> assertTrue(body.substring(entry.take(10).toInt()).startsWith("${index + 1} 0 obj")) }
    }
    @Test fun `corrupt PNG is rejected not embedded as pixels`() {
        val corrupt = png().also { it[30] = (it[30].toInt() xor 1).toByte() }
        assertRejects { service().buildPdfFromImages(listOf(file(corrupt, "png"))) }
    }
    @Test fun `oversized decoded dimensions are rejected before inflation`() {
        assertRejects { service().buildPdfFromImages(listOf(file(png(width = 100_000, height = 100_000), "png"))) }
    }
    @Test fun `unsupported data is rejected instead of assumed JPEG`() {
        assertRejects { service().buildPdfFromImages(listOf(file("not an image".toByteArray(), "jpg"))) }
    }
    @Test fun `JPEG uses native dimensions and DCT pixels`() {
        val pdf = service().buildPdfFromImages(listOf(file(java.util.Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAoAFADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDnKKKK+1PkQooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKAP/9k="), "jpg")))
        val body = String(pdf, Charsets.ISO_8859_1)
        assertTrue(body.contains("/Width 80 /Height 40")); assertTrue(body.contains("/DCTDecode"))
    }
    @Test fun `text is wrapped to fit Courier page and Unicode fails explicitly`() {
        val body = String(service().buildPdfFromText("W".repeat(160)), Charsets.ISO_8859_1)
        assertTrue(body.contains("/BaseFont /Courier")); assertFalse(body.contains("W".repeat(81)))
        assertRejects { service().buildPdfFromText("\u0C24\u0C46\u0C32\u0C41\u0C17\u0C41") }
    }
    @Test fun `conversion will not overwrite its input`() {
        val dir = temp(); val original = File(dir, "same.pdf").apply { writeText("preserve me") }
        val result = service().convertTextToPdf(ConversionRequest("same", ConversionType.textToPdf, listOf(original.path), dir.path, "same.pdf"))
        assertFalse(result.success); assertEquals("preserve me", original.readText())
    }
    @Test fun `long filenames keep their real extension`() {
        val name = FileSanitizationService { temp() }.sanitizeFileName("x".repeat(160) + ".pdf")
        assertEquals(120, name.length); assertTrue(name.endsWith(".pdf"))
    }
    @Test fun `existing output never gets overwritten`() {
        val dir = temp(); val source = File(dir, "source.txt").apply { writeText("new content") }
        val existing = File(dir, "existing.pdf").apply { writeText("existing content") }
        val result = service().convertTextToPdf(ConversionRequest("existing", ConversionType.textToPdf, listOf(source.path), dir.path, "existing.pdf"))
        assertFalse(result.success); assertEquals("existing content", existing.readText())
        assertTrue(dir.listFiles()!!.none { it.extension == "part" })
    }

    @Test fun `stream progress is monotonic and reports exact final bytes`() = runBlocking {
        val source = ByteArray(25000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        assertEquals(source.size.toLong(), copyMediaBounded(ByteArrayInputStream(source), output, source.size.toLong()) { progress += it })
        assertArrayEquals(source, output.toByteArray())
        assertEquals(source.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b > a })
    }
    @Test fun `progress cancellation stops further writes`() = runBlocking {
        val output = ByteArrayOutputStream()
        var cancelled = false
        try {
            copyMediaBounded(ByteArrayInputStream(ByteArray(25000)), output, 25000) { throw CancellationException("stop") }
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertTrue(output.size() in 1 until 25000)
    }

}
