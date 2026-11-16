package com.example.chat

import com.example.chat.ui.media.*
import java.io.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Added for later pipeline execution. Not run under the source-only/no-build instruction. */
class PickerImportCopyTest {
    private fun directory() = java.nio.file.Files.createTempDirectory("picker-copy-test").toFile()
    @Test fun `successful copy returns only a hidden part after input close`() = runBlocking {
        val dir = directory()
        var closed = false
        try {
            val result = copyPickerImport(dir, 100, {
                object : ByteArrayInputStream("content".toByteArray()) { override fun close() { closed = true; super.close() } }
            })
            assertTrue(closed)
            assertEquals(7L, result.bytesCopied)
            assertEquals("content", result.file.readText())
            assertTrue(result.file.name.startsWith(".devpick_")); assertTrue(result.file.name.endsWith(".part"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun `null stream leaves no partial file`() = runBlocking {
        val dir = directory()
        try {
            try { copyPickerImport(dir, 100, { null }); fail("Expected unavailable") }
            catch (failure: PickerImportFailure) { assertEquals(PickerImportFailureKind.Unavailable, failure.kind) }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `revoked access is permission failure with no successful output`() = runBlocking {
        val dir = directory()
        try {
            try { copyPickerImport(dir, 100, { throw SecurityException("private provider message") }); fail("Expected permission failure") }
            catch (failure: PickerImportFailure) { assertEquals(PickerImportFailureKind.Permission, failure.kind); assertEquals("Permission", failure.message) }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `input close failure rolls back even after all bytes copied`() = runBlocking {
        val dir = directory()
        try {
            try { copyPickerImport(dir, 100, { object : ByteArrayInputStream(byteArrayOf(1, 2)) { override fun close() { throw IOException("close failure") } } }); fail("Expected close failure") }
            catch (failure: PickerImportFailure) { assertEquals(PickerImportFailureKind.Read, failure.kind) }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `zero bytes and quota failures do not leak partials`() = runBlocking {
        val dir = directory()
        try {
            for ((input, expected) in listOf(byteArrayOf() to PickerImportFailureKind.Empty, ByteArray(101) to PickerImportFailureKind.TooLarge)) {
                try { copyPickerImport(dir, 100, { ByteArrayInputStream(input) }); fail("Expected rejection") }
                catch (failure: PickerImportFailure) { assertEquals(expected, failure.kind) }
                assertTrue(dir.listFiles().orEmpty().isEmpty())
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun `cancel callback closes stream before cleaning private output`() = runBlocking {
        val dir = directory()
        var closed = false
        try {
            try { copyPickerImport(dir, 100000, { object : ByteArrayInputStream(ByteArray(20000)) { override fun close() { closed = true; super.close() } } }) { throw CancellationException("fixture") }; fail("Expected cancellation") }
            catch (_: CancellationException) { }
            assertTrue(closed); assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun `two same-name sources get independent exclusive pending paths`() = runBlocking {
        val dir = directory()
        try {
            val a = copyPickerImport(dir, 100, { ByteArrayInputStream(byteArrayOf(1)) })
            val b = copyPickerImport(dir, 100, { ByteArrayInputStream(byteArrayOf(2)) })
            assertNotEquals(a.file.path, b.file.path)
            assertArrayEquals(byteArrayOf(1), a.file.readBytes()); assertArrayEquals(byteArrayOf(2), b.file.readBytes())
        } finally { dir.deleteRecursively() }
    }
}
