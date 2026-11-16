package com.example.chat

import com.example.chat.domain.models.NotificationToastContract as Contract
import com.example.chat.domain.models.NotificationToastPreferences
import org.junit.Assert.*
import org.junit.Test

/** Source-only regression cases, not compiled or run. */
class NotificationToastPreservationTest {
    @Test fun unknownLabelsAndCustomValuesSurviveRoundTrip() {
        val p = NotificationToastPreferences(onlineToastRingtone = "future tone", onlineToastPosition = "future position",
            onlineToastRadius = 91.0, onlineToastBgColor = -16711936L, onlineToastTextColor = 0x12345678L)
        val restored = NotificationToastPreferences.fromMap(p.toMap())
        assertEquals(p, restored)
        assertNull(p.toMap()["onlineToastRingtoneId"])
    }
    @Test fun knownIdsAreAdditiveAndUnknownIdsFallBack() {
        assertEquals("Default Chaty Tone", Contract.storedTone("default", "Default Chaty Tone"))
        assertEquals("future tone", Contract.storedTone("future-id", "future tone"))
        assertEquals("Silent", Contract.storedTone("silent", "renamed label"))
        Contract.tones.forEach { tone ->
            val p = NotificationToastPreferences(onlineToastRingtone = tone.label)
            assertEquals(tone.id, p.toMap()["onlineToastRingtoneId"])
            assertEquals(p, NotificationToastPreferences.fromMap(p.toMap()))
        }
    }
}
