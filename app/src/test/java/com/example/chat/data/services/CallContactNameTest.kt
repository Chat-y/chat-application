package com.example.chat.data.services

import org.junit.Assert.*
import org.junit.Test

class CallContactNameTest {
    @Test fun placeholdersDoNotHideContactOrUsername() {
        assertEquals("maya", CallContactName.first("Chaty User", "maya", "Chat"))
        assertEquals("Maya", CallContactName.first("  ", "User", " Maya "))
        assertNull(CallContactName.first("CHATY USER", "Unknown caller", "Chat"))
    }
    @Test fun realNamesArePreserved() {
        assertEquals("Bandi Maya", CallContactName.first("Bandi Maya", "other"))
        assertEquals("Maya", CallContactName.first(" Maya "))
    }
}
