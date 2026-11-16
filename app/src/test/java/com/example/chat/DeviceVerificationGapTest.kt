package com.example.chat

import com.example.chat.core.audio.AudioSettingsController
import com.example.chat.core.audio.DeviceVerification
import com.example.chat.core.audio.DeviceVerificationStatus
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Parser/data-projection fixtures only, not hardware evidence. */
class DeviceVerificationGapTest {
    @Test fun everyKnownLabelParsesAndUnknownDoesNotClaimVerification() {
        DeviceVerificationStatus.entries.forEach { assertEquals(it, DeviceVerificationStatus.parse(it.label)) }
        assertEquals(DeviceVerificationStatus.unknown, DeviceVerificationStatus.parse("future-status"))
        assertEquals(DeviceVerificationStatus.unknown, DeviceVerificationStatus.parse(null))
    }
    @Test fun malformedRecordCannotProduceVerifiedStatusOrInventADate() {
        val model = DeviceVerification.fromRecord(mapOf("testStatus" to 42, "testDate" to "not-a-date"))
        assertEquals(DeviceVerificationStatus.unknown, model.status)
        assertNull(model.date)
        assertEquals("not-a-date", model.recordedDate)
        assertEquals("Unknown device", model.deviceModel)
    }
    @Test fun knownTableProjectsWithoutDiscardingRecords() {
        assertEquals(AudioSettingsController.verifiedDeviceRecords.size, AudioSettingsController.deviceVerifications.size)
        val model = DeviceVerification.fromRecord(mapOf("testDate" to "2026-10-10", "verificationMethod" to "Fixture"))
        assertEquals(LocalDate.of(2026, 10, 10), model.date)
        assertEquals("Fixture", model.method)
    }
    @Test fun otherModelsStayBehindShowAllAndNearNamesDoNotMatch() {
        val first = DeviceVerification.fromRecord(mapOf("deviceModel" to "Fixture model"))
        val second = DeviceVerification.fromRecord(mapOf("deviceModel" to "Fixture model Pro"))
        val records = listOf(second, first)
        assertEquals(listOf(first), com.example.chat.ui.settings.audioDeviceRecordsForDisplay(records, "fixture model", false))
        assertEquals(listOf(first, second), com.example.chat.ui.settings.audioDeviceRecordsForDisplay(records, "fixture model", true))
        assertEquals(emptyList<DeviceVerification>(), com.example.chat.ui.settings.audioDeviceRecordsForDisplay(records, "Other", false))
    }

}
