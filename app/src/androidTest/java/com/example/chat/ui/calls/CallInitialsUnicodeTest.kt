package com.example.chat.ui.calls

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** CALLS-R2-25: device test, because ICU is an Android API. */
@RunWith(AndroidJUnit4::class)
class CallInitialsUnicodeTest {
    @Test fun blankAndNullKeepFallback() {
        assertEquals("U", callInitials(null))
        assertEquals("U", callInitials("  "))
    }
    @Test fun latinUsesUppercase() { assertEquals("M", callInitials(" maya")) }
    @Test fun combiningMarkIsNotSplit() { assertEquals("E\u0301", callInitials("e\u0301mile")) }
    @Test fun surrogatePairIsNotSplit() { assertEquals("\uD83D\uDE00", callInitials("\uD83D\uDE00 Maya")) }
    @Test fun indicClusterIsNotSplit() { assertEquals("మా", callInitials("మాయ")) }
}
