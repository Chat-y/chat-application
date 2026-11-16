package com.example.chat.ui.chats.starred

import org.junit.Assert.assertEquals
import org.junit.Test

class StarredAccessRulesTest {
    @Test fun everyPrivacyAndAccountGateFailsClosed() {
        for (bits in 0 until 128) {
            val v = (0..6).map { bits and (1 shl it) != 0 }
            assertEquals("gate combination $bits", v[0] && v[1] && !v[2] && !v[3] && !v[4] && !v[5] && !v[6],
                StarredAccessRules.mayShow(v[0], v[1], v[2], v[3], v[4], v[5], v[6]))
        }
    }
}
