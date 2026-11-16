package com.example.chat

import androidx.test.core.app.ApplicationProvider
import com.example.chat.core.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Android org.json/asset parser fixtures, not hardware evidence. */
class AudioDeviceAssetParserGapTest {
    @Test fun packagedRecordsPreserveLegacyEvidenceWithoutReverification() = runBlocking {
        val source = AssetAudioDeviceRecordSource(ApplicationProvider.getApplicationContext())
        assertEquals(AudioSettingsController.deviceVerifications, source.load())
    }
    @Test fun malformedFieldsDoNotBecomeVerifiedLabelsOrInventedDates() {
        val rows = parseAudioDeviceRecords("""[null,7,{"deviceModel":false,"testStatus":"future","testDate":"not-a-date"}]""")
        assertEquals(1, rows.size)
        assertEquals("Unknown device", rows.single().deviceModel)
        assertEquals(DeviceVerificationStatus.unknown, rows.single().status)
        assertNull(rows.single().date)
        assertEquals("not-a-date", rows.single().recordedDate)
    }
    @Test fun malformedOrOversizedDocumentIsRejectedRatherThanSilentlyVerified() {
        assertNotNull(runCatching { parseAudioDeviceRecords("not-json") }.exceptionOrNull())
        val tooMany = List(501) { "{}" }.joinToString(prefix = "[", postfix = "]")
        assertTrue(runCatching { parseAudioDeviceRecords(tooMany) }.exceptionOrNull() is IllegalArgumentException)
    }
}
