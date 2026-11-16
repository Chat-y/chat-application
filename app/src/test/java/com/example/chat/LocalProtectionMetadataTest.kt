package com.example.chat

import com.example.chat.core.preferences.LocalProtectionMetadata
import com.example.chat.core.preferences.PreferenceKeys
import org.junit.Assert.*
import org.junit.Test

class LocalProtectionMetadataTest {
    private val device = mapOf<String,Any?>(
        PreferenceKeys.security to mapOf("lockedConversationIds" to listOf("private-chat"), "hiddenConversationIds" to listOf("hidden-chat"), "lockMethod" to "PIN", "futureField" to "retained"),
        PreferenceKeys.privacy to mapOf("readReceipts" to false))

    @Test fun outgoingRemovesOnlyDeviceMembershipAndNeverMutatesLocalSnapshot() {
        val remote = LocalProtectionMetadata.forRemote(device)
        val security = remote[PreferenceKeys.security] as Map<*, *>
        assertFalse(security.containsKey("lockedConversationIds")); assertFalse(security.containsKey("hiddenConversationIds"))
        assertEquals("PIN", security["lockMethod"]); assertEquals("retained", security["futureField"])
        assertEquals(device[PreferenceKeys.privacy], remote[PreferenceKeys.privacy])
        assertTrue(LocalProtectionMetadata.isPresent(device)); assertFalse(LocalProtectionMetadata.isPresent(remote))
        assertEquals(listOf("private-chat"), (device[PreferenceKeys.security] as Map<*, *>)["lockedConversationIds"])
    }
    @Test fun incomingRemoteCannotReplaceDeviceListsButOtherSettingsMerge() {
        val remote = mapOf<String,Any?>(PreferenceKeys.security to mapOf("lockedConversationIds" to listOf("other-device-chat"), "hiddenConversationIds" to listOf("other-hidden"), "lockMethod" to "Password", "futureField" to 42))
        val merged = LocalProtectionMetadata.keepLocal(remote, device)[PreferenceKeys.security] as Map<*, *>
        assertEquals(listOf("private-chat"), merged["lockedConversationIds"])
        assertEquals(listOf("hidden-chat"), merged["hiddenConversationIds"])
        assertEquals("Password", merged["lockMethod"]); assertEquals(42, merged["futureField"])
    }
    @Test fun firstDeviceLoadNeverImportsRemoteMembership() {
        val result = LocalProtectionMetadata.keepLocal(device, emptyMap())[PreferenceKeys.security] as Map<*, *>
        assertEquals(emptyList<String>(), result["lockedConversationIds"])
        assertEquals(emptyList<String>(), result["hiddenConversationIds"])
    }
    @Test fun absentAndMalformedRemoteSecurityDoNotCrashOrClearLocalMembership() {
        assertFalse(LocalProtectionMetadata.isPresent(emptyMap()))
        assertFalse(LocalProtectionMetadata.isPresent(mapOf(PreferenceKeys.security to "invalid")))
        assertEquals(emptyMap<String,Any?>(), LocalProtectionMetadata.forRemote(emptyMap()))
        val restored = LocalProtectionMetadata.keepLocal(mapOf(PreferenceKeys.security to "invalid"), device)[PreferenceKeys.security] as Map<*, *>
        assertEquals(listOf("private-chat"), restored["lockedConversationIds"])
    }
}
