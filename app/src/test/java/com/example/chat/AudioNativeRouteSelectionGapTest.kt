package com.example.chat

import android.media.AudioDeviceInfo
import com.example.chat.core.audio.AudioOutputRoute
import com.example.chat.core.audio.audioRouteMatchesDeviceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NOT RUN. Constants/pure matching only, no AudioManager/HAL/permission coverage. */
class AudioNativeRouteSelectionGapTest {
    @Test fun receiverIsExplicitAndDefaultDoesNotPickAnArbitraryDevice() {
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.receiver, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertFalse(audioRouteMatchesDeviceType(AudioOutputRoute.receiver, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(audioRouteMatchesDeviceType(AudioOutputRoute.systemDefault, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
    }
    @Test fun compatibleWiredAndUsbVariantsMatchTheirLogicalRoute() {
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.wiredHeadset, AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.wiredHeadset, AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.usb, AudioDeviceInfo.TYPE_USB_DEVICE))
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.usb, AudioDeviceInfo.TYPE_USB_HEADSET))
        assertFalse(audioRouteMatchesDeviceType(AudioOutputRoute.bluetooth, AudioDeviceInfo.TYPE_USB_HEADSET))
    }
    @Test fun leAudioRoutesMatchBluetoothButA2dpIsNotClaimedAsCommunication() {
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.bluetooth, AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.bluetooth, AudioDeviceInfo.TYPE_BLE_SPEAKER))
        assertFalse(audioRouteMatchesDeviceType(AudioOutputRoute.bluetooth, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }

    @Test fun hearingAidIsDistinctFromBluetoothAndMapsToExternalCallState() {
        assertTrue(audioRouteMatchesDeviceType(AudioOutputRoute.hearingAid, AudioDeviceInfo.TYPE_HEARING_AID))
        assertFalse(audioRouteMatchesDeviceType(AudioOutputRoute.bluetooth, AudioDeviceInfo.TYPE_HEARING_AID))
        org.junit.Assert.assertEquals(com.example.chat.core.audio.AudioSessionState.externalCall,
            com.example.chat.core.audio.AudioPolicyCoordinator.stateForRoute(AudioOutputRoute.hearingAid))
    }

}
