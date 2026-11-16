package com.example.chat.data.services

import org.junit.Assert.assertEquals
import org.junit.Test

class CallIncomingIdentityTest {
    @Test fun missingDirectoryEntryIsUnknown() {
        assertEquals("Unknown caller", CallIncomingIdentity.displayName(null))
        assertEquals("Unknown caller", CallIncomingIdentity.displayName("  "))
    }
    @Test fun directoryNameIsUsedWithoutClaimingVerification() {
        assertEquals("Maya", CallIncomingIdentity.displayName(" Maya "))
    }
}
