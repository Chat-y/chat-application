package com.example.chat

import com.example.chat.ui.media.*
import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures, NOT RUN. Signature classification is not malware validation. */
class PickerProviderNamePolicyTest {
    @Test fun pathSeparatorsAndNulAreRejected() {
        for (name in listOf("../../photo.jpg", "folder\\photo.jpg", "a\u0000b")) {
            try { requirePickerProviderName(name); fail("Expected invalid provider label") }
            catch (_: IllegalArgumentException) { }
        }
        requirePickerProviderName("صورة.jpg")
    }
    @Test fun bytesNotSpoofedSuffixChooseSafeImportSuffix() {
        val pdf = FileTypeDetector.classifyBytes("%PDF-1.7".toByteArray())
        assertEquals("photo.pdf", pickerDetectedImportName("photo.jpg", pdf))
        val unknown = FileTypeDetector.classifyBytes(byteArrayOf(0x4D, 0x5A, 0, 0))
        assertEquals("friendly.bin", pickerDetectedImportName("friendly.jpg", unknown))
    }
    @Test fun zipHeaderDoesNotPretendToVerifyAnOfficeOrApkContainer() {
        val zip = FileTypeDetector.classifyBytes(byteArrayOf(0x50, 0x4B, 3, 4))
        assertEquals("report.zip", pickerDetectedImportName("report.docx", zip))
        assertTrue(pickerDetectedImportName("x".repeat(500), zip).length <= 100)
    }
    @Test fun multibyteNameStaysWithinCacheComponentBudget() {
        val pdf = FileTypeDetector.classifyBytes("%PDF".toByteArray())
        val name = pickerDetectedImportName("界".repeat(120) + ".jpg", pdf)
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 144)
        assertTrue(name.endsWith(".pdf"))
        assertFalse(name.dropLast(4).last().isHighSurrogate())
    }
}
