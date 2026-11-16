package com.example.chat

import com.example.chat.core.audio.*
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Payload only, not Android sharesheet/recipient delivery. */
class AudioDiagnosticExportGapTest {
    private fun event(time: Long) = AudioPolicyDiagnosticEvent("private contact/address/native detail",
        AudioUseCase.communication, AudioSessionState.receiverCall, AudioOutputRoute.receiver,
        "private exception", time)
    @Test fun onlyFixedFieldsLeaveTheBufferAndNoRawDetailsAreShared() {
        val payload = formatAudioDiagnosticExport(listOf(event(123)))
        assertTrue(payload.contains("123 | communication | receiverCall | receiver | Audio operation failed"))
        assertFalse(payload.contains("private"))
        assertFalse(payload.contains("native detail"))
    }
    @Test fun capAndEmptySnapshotAreBounded() {
        val payload = formatAudioDiagnosticExport((0L..249L).map(::event))
        assertEquals(202, payload.lines().size)
        assertEquals("Events: 200", payload.lines()[1])
        assertTrue(payload.lines()[2].startsWith("50 |"))
        assertEquals("Audio diagnostics (debug)\nEvents: 0", formatAudioDiagnosticExport(emptyList()))
    }
    @Test fun patchMetadataNeverUsesCurrentPhoneOrInventsAnInvalidDate() {
        val dated = DeviceVerification.fromRecord(mapOf("securityPatchLevel" to "2026-09-05"))
        assertEquals(java.time.LocalDate.of(2026, 9, 5), dated.securityPatchLevel)
        assertNull(DeviceVerification.fromRecord(emptyMap()).securityPatchLevel)
        for (bad in listOf("bad", "2026-13-05", "2026-02-30", "2026-9-5"))
            assertNull(DeviceVerification.fromRecord(mapOf("securityPatchLevel" to bad)).securityPatchLevel)
    }
}
