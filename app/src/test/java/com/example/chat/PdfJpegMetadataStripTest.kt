package com.example.chat

import com.example.chat.ui.media.PdfImageEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** DOC-R3-129: generated PDFs must not carry camera metadata. */
class PdfJpegMetadataStripTest {

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xff); out.write(marker)
        val length = payload.size + 2
        out.write(length shr 8); out.write(length and 255)
        out.write(payload)
        return out.toByteArray()
    }

    private fun sampleJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xff.toByte(), 0xd8.toByte()))
        out.write(segment(0xe0, "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray(Charsets.US_ASCII)))
        out.write(segment(0xe1, "Exif\u0000\u0000GPS-LAT-52.1-LON-4.3-DEVICE".toByteArray(Charsets.US_ASCII)))
        out.write(segment(0xe2, "ICC_PROFILE\u0000\u0001\u0001fake".toByteArray(Charsets.US_ASCII)))
        out.write(segment(0xed, "Photoshop 3.0\u0000iptc".toByteArray(Charsets.US_ASCII)))
        out.write(segment(0xfe, "a comment".toByteArray(Charsets.US_ASCII)))
        out.write(segment(0xdb, ByteArray(65)))
        out.write(segment(0xc0, byteArrayOf(8, 0, 16, 0, 16, 3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1)))
        out.write(segment(0xc4, ByteArray(20)))
        out.write(segment(0xda, byteArrayOf(3, 1, 0, 2, 0x11, 3, 0x11, 0, 63, 0)))
        out.write(byteArrayOf(0x11, 0x22, 0xff.toByte(), 0x00, 0x33, 0xff.toByte(), 0xd9.toByte()))
        return out.toByteArray()
    }

    @Test fun `app1 app13 and com are removed`() {
        val stripped = PdfImageEncoder.stripJpegMetadata(sampleJpeg())
        val text = String(stripped, Charsets.US_ASCII)
        assertFalse(text.contains("GPS"))
        assertFalse(text.contains("Exif"))
        assertFalse(text.contains("Photoshop"))
        assertFalse(text.contains("a comment"))
    }

    @Test fun `jfif icc tables and scan data survive`() {
        val source = sampleJpeg()
        val stripped = PdfImageEncoder.stripJpegMetadata(source)
        val text = String(stripped, Charsets.US_ASCII)
        assertTrue(text.contains("JFIF"))
        assertTrue(text.contains("ICC_PROFILE"))
        // Entropy data after SOS is byte-identical.
        val scanStart = source.indexOf(0xda.toByte())
        val strippedScanStart = stripped.indexOf(0xda.toByte())
        assertArrayEquals(source.copyOfRange(scanStart - 1, source.size), stripped.copyOfRange(strippedScanStart - 1, stripped.size))
    }

    @Test fun `jpeg without metadata is unchanged`() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xff.toByte(), 0xd8.toByte()))
        out.write(segment(0xe0, "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray(Charsets.US_ASCII)))
        out.write(segment(0xc0, byteArrayOf(8, 0, 16, 0, 16, 3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1)))
        out.write(segment(0xda, byteArrayOf(3, 1, 0, 2, 0x11, 3, 0x11, 0, 63, 0)))
        out.write(byteArrayOf(0x11, 0xff.toByte(), 0xd9.toByte()))
        val clean = out.toByteArray()
        assertArrayEquals(clean, PdfImageEncoder.stripJpegMetadata(clean))
    }

    @Test fun `generated pdf embeds no camera metadata`() {
        val tmp = File.createTempFile("strip-test-", ".jpg")
        try {
            tmp.writeBytes(sampleJpeg())
            val pdf = String(PdfImageEncoder.build(listOf(tmp)), Charsets.ISO_8859_1)
            assertFalse(pdf.contains("GPS-LAT"))
            assertFalse(pdf.contains("Photoshop"))
            assertTrue(pdf.contains("/DCTDecode"))
        } finally {
            tmp.delete()
        }
    }

    private fun ByteArray.indexOf(value: Byte): Int {
        for (i in indices) if (this[i] == value) return i
        return -1
    }
}
