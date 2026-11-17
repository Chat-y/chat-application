package com.example.chat

import com.example.chat.data.media.SecureAttachmentFileCodec as Codec
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Authored UNRUN, no build/test execution authorized. */
class SecureAttachmentFileCodecTest {
    private fun runCase(block: suspend (File, File) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("secure-media-test").toFile()
        try {
            val source = File(dir, "input.bin"); source.writeBytes(ByteArray(150_001) { (it % 251).toByte() })
            block(dir, source)
        } finally { dir.deleteRecursively() }
    }
    @Test fun chunkedRoundTripKeepsDescriptorBoundToConversation() = runCase { dir, source ->
        val sealedFile = Codec.seal(source, dir, "room", "document.bin", "application/octet-stream") { }
        val descriptor = sealedFile.descriptor.copy(attachmentId = "ticket")
        try {
            val parsed = Codec.parse(descriptor.toMap(), "room", "ticket")
            try { val plain = Codec.open(sealedFile.file, dir, parsed) { }; assertArrayEquals(source.readBytes(), plain.readBytes()); plain.delete() }
            finally { parsed.destroy() }
            var rejected = false
            try { Codec.parse(descriptor.toMap(), "other-room", "ticket") } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        } finally { descriptor.destroy(); sealedFile.file.delete() }
    }
    @Test fun modifiedTagRejectsAndRemovesPlaintextPartial() = runCase { dir, source ->
        val encrypted = Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") { }
        try {
            RandomAccessFile(encrypted.file, "rw").use { f -> f.seek(f.length() - 1); val byte = f.readByte().toInt(); f.seek(f.length() - 1); f.writeByte(byte xor 1) }
            var rejected = false
            try { Codec.open(encrypted.file, dir, encrypted.descriptor) { } } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            assertEquals(0, dir.listFiles()!!.count { it.name.startsWith("chaty-verified-") })
        } finally { encrypted.descriptor.destroy(); encrypted.file.delete() }
    }
    @Test fun truncationAndTrailingBytesRejectBeforeRendering() = runCase { dir, source ->
        val encrypted = Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") { }
        try {
            encrypted.file.appendBytes(byteArrayOf(0))
            var rejected = false
            try { Codec.open(encrypted.file, dir, encrypted.descriptor) { } } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
        } finally { encrypted.descriptor.destroy(); encrypted.file.delete() }
    }
    @Test fun reorderedOrDuplicatedIndexCannotRender() = runCase { dir, source ->
        for (badIndex in listOf(1L, -1L)) {
            val encrypted = Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") { }
            try {
                RandomAccessFile(encrypted.file, "rw").use { f -> f.seek(8); f.writeLong(badIndex) }
                var rejected = false
                try { Codec.open(encrypted.file, dir, encrypted.descriptor) { } } catch (_: Exception) { rejected = true }
                assertTrue(rejected)
                assertEquals(0, dir.listFiles()!!.count { it.name.startsWith("chaty-verified-") })
            } finally { encrypted.descriptor.destroy(); encrypted.file.delete() }
        }
    }
    @Test fun duplicateSecondFrameIndexRejects() = runCase { dir, source ->
        val encrypted = Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") { }
        try {
            RandomAccessFile(encrypted.file, "rw").use { f -> f.seek(4L + 4 + 36 + 65536 + 4); f.writeLong(0) }
            var rejected = false
            try { Codec.open(encrypted.file, dir, encrypted.descriptor) { } } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
        } finally { encrypted.descriptor.destroy(); encrypted.file.delete() }
    }
    @Test fun actualTruncationRejects() = runCase { dir, source ->
        val encrypted = Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") { }
        try {
            RandomAccessFile(encrypted.file, "rw").use { it.setLength(it.length() - 1) }
            var rejected = false
            try { Codec.open(encrypted.file, dir, encrypted.descriptor) { } } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
        } finally { encrypted.descriptor.destroy(); encrypted.file.delete() }
    }
    @Test fun emptyAndOversizeRejectBeforeHash() = runCase { dir, source ->
        for (length in listOf(0L, Codec.MAX_CIPHERTEXT + 1)) {
            RandomAccessFile(source, "rw").use { it.setLength(length) }
            var rejected = false
            try { Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") { } } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
    @Test fun ownerCancellationInHashOrDecryptRemovesOwnedPartials() = runCase { dir, source ->
        var calls = 0; var cancelled = false
        try { Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") {
            if (++calls >= 2) throw kotlinx.coroutines.CancellationException("owner revoked")
        } } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue(cancelled)
        val encrypted = Codec.seal(source, dir, "room", "file.bin", "application/octet-stream") { }
        try {
            calls = 0; cancelled = false
            try { Codec.open(encrypted.file, dir, encrypted.descriptor) {
                if (++calls >= 3) throw kotlinx.coroutines.CancellationException("owner revoked")
            } } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(0, dir.listFiles()!!.count { it.name.startsWith("chaty-verified-") })
        } finally { encrypted.descriptor.destroy(); encrypted.file.delete() }
    }

}
