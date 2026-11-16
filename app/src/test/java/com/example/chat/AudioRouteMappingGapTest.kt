package com.example.chat

import com.example.chat.core.audio.AudioOutputRoute
import com.example.chat.ui.settings.audioRouteDisplayName
import com.example.chat.ui.settings.audioRouteIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Presentation mapping coverage only; does not verify hardware routing. NOT RUN. */
class AudioRouteMappingGapTest {
    @Test fun everyExistingRouteHasFriendlyNameAndGlyph() {
        val expected = mapOf(AudioOutputRoute.receiver to "Phone earpiece", AudioOutputRoute.speaker to "Phone speaker",
            AudioOutputRoute.bluetooth to "Bluetooth", AudioOutputRoute.wiredHeadset to "Wired headset",
            AudioOutputRoute.usb to "USB audio", AudioOutputRoute.hearingAid to "Hearing aid", AudioOutputRoute.systemDefault to "System default")
        assertEquals(AudioOutputRoute.entries.toSet(), expected.keys)
        AudioOutputRoute.entries.forEach { route ->
            assertEquals(expected.getValue(route), audioRouteDisplayName(route))
            assertNotNull(audioRouteIcon(route))
        }
    }
}
